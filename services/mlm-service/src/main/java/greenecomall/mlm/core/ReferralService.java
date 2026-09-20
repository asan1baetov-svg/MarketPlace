package greenecomall.mlm.core;

import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.MlmEvents;
import greenecomall.mlm.config.MlmProperties;
import greenecomall.mlm.domain.MlmAccount;
import greenecomall.mlm.domain.MlmBonusTransaction;
import greenecomall.mlm.domain.MlmOrder;
import greenecomall.mlm.domain.MlmReferralEdge;
import greenecomall.mlm.domain.MlmReferralRate;
import greenecomall.mlm.domain.MlmTariff;
import greenecomall.mlm.repo.MlmAccountRepository;
import greenecomall.mlm.repo.MlmBonusTransactionRepository;
import greenecomall.mlm.repo.MlmReferralRateRepository;
import greenecomall.mlm.repo.MlmReferralTreeRepository;
import greenecomall.mlm.repo.MlmTariffRepository;
import greenecomall.mlm.support.Tracing;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Реферальное дерево (closure table, произвольная глубина) и локальные реферальные бонусы.
 *
 * <p>Во внешнем MLM-бэке уже есть своё дерево (матрица уровней/этапов) и начисления, поэтому
 * бонусы здесь по умолчанию выключены ({@code mlm.local-bonuses-enabled=false}) — маркетплейс
 * только сообщает о покупках. Дерево строится всегда: оно нужно для кабинета и отчётов.
 */
@Service
public class ReferralService {

    private static final String PRODUCER = "mlm-service";
    private static final String SOURCE_PURCHASE = "REFERRAL_PURCHASE";

    private final MlmReferralTreeRepository tree;
    private final MlmAccountRepository accounts;
    private final MlmReferralRateRepository rates;
    private final MlmTariffRepository tariffs;
    private final MlmBonusTransactionRepository bonuses;
    private final DomainEventPublisher events;
    private final MlmProperties properties;

    public ReferralService(MlmReferralTreeRepository tree, MlmAccountRepository accounts,
                           MlmReferralRateRepository rates, MlmTariffRepository tariffs,
                           MlmBonusTransactionRepository bonuses, DomainEventPublisher events,
                           MlmProperties properties) {
        this.tree = tree;
        this.accounts = accounts;
        this.rates = rates;
        this.tariffs = tariffs;
        this.bonuses = bonuses;
        this.events = events;
        this.properties = properties;
    }

    /**
     * Вставляет узел в дерево. Реферер мог зайти на маркетплейс позже реферала — тогда уже
     * пришедшие рефералы (со всем их поддеревом) подвешиваются к нему в этот момент.
     */
    @Transactional
    public void attach(MlmAccount account) {
        if (!tree.existsByAncestorIdAndDescendantId(account.getId(), account.getId())) {
            tree.save(new MlmReferralEdge(account.getId(), account.getId(), 0));
        }
        if (account.getUplineMlmUserId() != null) {
            accounts.findByMlmUserId(account.getUplineMlmUserId())
                    .ifPresent(upline -> connectSubtree(account.getId(), upline.getId()));
        }
        for (MlmAccount child : accounts.findByUplineMlmUserId(account.getMlmUserId())) {
            connectSubtree(child.getId(), account.getId());
        }
    }

    /** Для каждого предка родителя A и потомка ребёнка D: ребро (A, D, depth(A→parent) + depth(child→D) + 1). */
    private void connectSubtree(UUID childId, UUID parentId) {
        if (tree.existsByAncestorIdAndDescendantId(parentId, childId) || childId.equals(parentId)) {
            return;
        }
        List<MlmReferralEdge> parentAncestors = tree.findByDescendantIdOrderByDepthAsc(parentId);
        List<MlmReferralEdge> childDescendants = tree.findByAncestorIdAndDepthBetweenOrderByDepthAsc(childId, 0, Integer.MAX_VALUE);
        for (MlmReferralEdge up : parentAncestors) {
            if (up.getAncestorId().equals(childId)) {
                return; // защита от цикла в кривых данных внешней системы
            }
            for (MlmReferralEdge down : childDescendants) {
                if (!tree.existsByAncestorIdAndDescendantId(up.getAncestorId(), down.getDescendantId())) {
                    tree.save(new MlmReferralEdge(up.getAncestorId(), down.getDescendantId(),
                            up.getDepth() + down.getDepth() + 1));
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public List<Referral> referrals(UUID accountId, int maxDepth) {
        List<MlmReferralEdge> edges = tree.findByAncestorIdAndDepthBetweenOrderByDepthAsc(accountId, 1, maxDepth);
        Map<UUID, MlmAccount> byId = accounts.findAllById(edges.stream().map(MlmReferralEdge::getDescendantId).toList())
                .stream().collect(Collectors.toMap(MlmAccount::getId, Function.identity()));
        return edges.stream()
                .filter(e -> byId.containsKey(e.getDescendantId()))
                .map(e -> new Referral(byId.get(e.getDescendantId()), e.getDepth()))
                .toList();
    }

    /** Начисляет бонусы предкам покупателя по ставкам тарифа. No-op, если локальные бонусы выключены. */
    @Transactional
    public void accrueForPurchase(MlmAccount buyer, MlmOrder order) {
        if (!properties.localBonusesEnabled()) {
            return;
        }
        UUID tariffId = buyer.getTariffId() != null ? buyer.getTariffId()
                : tariffs.findFirstByIsDefaultTrueAndActiveTrue().map(MlmTariff::getId).orElse(null);
        if (tariffId == null) {
            return;
        }
        Map<Integer, BigDecimal> byLevel = rates.findByTariffIdOrderByLevelAsc(tariffId).stream()
                .collect(Collectors.toMap(MlmReferralRate::getLevel, MlmReferralRate::getPercent));
        String sourceRef = order.getOrderId().toString();
        for (MlmReferralEdge edge : tree.findByDescendantIdOrderByDepthAsc(buyer.getId())) {
            BigDecimal percent = byLevel.get(edge.getDepth());
            if (edge.getDepth() == 0 || percent == null
                    || bonuses.existsBySourceRefAndMlmAccountId(sourceRef, edge.getAncestorId())) {
                continue;
            }
            long amount = BigDecimal.valueOf(order.getAmountMinor()).multiply(percent)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_DOWN).longValueExact();
            if (amount <= 0) {
                continue;
            }
            MlmAccount beneficiary = accounts.findById(edge.getAncestorId()).orElse(null);
            if (beneficiary == null || beneficiary.isBlocked()) {
                continue;
            }
            bonuses.save(new MlmBonusTransaction(beneficiary.getId(), SOURCE_PURCHASE, sourceRef, buyer.getId(),
                    edge.getDepth(), percent, amount, order.getCurrency()));
            beneficiary.creditBonus(amount);
            events.publish(Topics.MLM, beneficiary.getMlmUserId(),
                    EventEnvelope.of(EventTypes.MLM_REFERRAL_BONUS_ACCRUED, PRODUCER, Tracing.currentTraceId(),
                            new MlmEvents.MlmReferralBonusAccrued(beneficiary.getId(), buyer.getId(),
                                    edge.getDepth(), amount, order.getCurrency())));
        }
    }

    @Transactional
    public void reverseForOrder(UUID orderId) {
        for (MlmBonusTransaction tx : bonuses.findBySourceRef(orderId.toString())) {
            if (tx.getStatus() == MlmBonusTransaction.Status.ACCRUED) {
                tx.reverse();
                accounts.findById(tx.getMlmAccountId()).ifPresent(a -> a.debitBonus(tx.getAmountMinor()));
            }
        }
    }

    public record Referral(MlmAccount account, int level) {
    }
}
