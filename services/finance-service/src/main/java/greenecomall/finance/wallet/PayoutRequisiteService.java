package greenecomall.finance.wallet;

import greenecomall.common.domain.DomainException;
import greenecomall.finance.FinanceErrors;
import greenecomall.finance.domain.PayoutRequisite;
import greenecomall.finance.domain.Wallet;
import greenecomall.finance.domain.WalletOwnerType;
import greenecomall.finance.finik.FinikBank;
import greenecomall.finance.repo.PayoutRequisiteRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Реквизиты для автовыплат: магазин добавляет, админ одобряет или блокирует. */
@Service
public class PayoutRequisiteService {

    private final PayoutRequisiteRepository requisites;
    private final Clock clock;

    public PayoutRequisiteService(PayoutRequisiteRepository requisites, Clock clock) {
        this.requisites = requisites;
        this.clock = clock;
    }

    /** Реквизиты владельца кошелька (магазина). Автовыплаты — только владельцам-магазинам. */
    @Transactional
    public PayoutRequisite add(Wallet wallet, FinikBank bank, String phone) {
        if (wallet.getOwnerType() != WalletOwnerType.SHOP) {
            throw new DomainException(FinanceErrors.REQUISITE_INVALID, "auto payouts are available for shops only");
        }
        return requisites.save(new PayoutRequisite(wallet.getOwnerType(), wallet.getOwnerRef(), bank,
                normalizePhone(phone), Instant.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<PayoutRequisite> forOwner(WalletOwnerType ownerType, String ownerRef) {
        return requisites.findByOwnerTypeAndOwnerRefOrderByCreatedAtAsc(ownerType, ownerRef);
    }

    /** Куда переводить: самые ранние одобренные реквизиты (как в GreenEcoMall). */
    @Transactional(readOnly = true)
    public Optional<PayoutRequisite> approvedFor(WalletOwnerType ownerType, String ownerRef) {
        return requisites.findFirstByOwnerTypeAndOwnerRefAndStatusOrderByCreatedAtAsc(
                ownerType, ownerRef, PayoutRequisite.Status.APPROVED);
    }

    @Transactional(readOnly = true)
    public Page<PayoutRequisite> byStatus(PayoutRequisite.Status status, Pageable pageable) {
        return requisites.findByStatusOrderByCreatedAtAsc(status, pageable);
    }

    @Transactional
    public PayoutRequisite approve(UUID id) {
        PayoutRequisite requisite = require(id);
        requisite.approve(Instant.now(clock));
        return requisite;
    }

    @Transactional
    public PayoutRequisite block(UUID id) {
        PayoutRequisite requisite = require(id);
        requisite.block(Instant.now(clock));
        return requisite;
    }

    public PayoutRequisite require(UUID id) {
        return requisites.findById(id)
                .orElseThrow(() -> new DomainException(FinanceErrors.REQUISITE_NOT_FOUND, "requisite not found: " + id));
    }

    /** Формат Finik: 996XXXXXXXXX (12 цифр). Принимаем +996…, 0XXX…, XXXXXXXXX. */
    static String normalizePhone(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("\\D", "");
        if (digits.length() == 10 && digits.startsWith("0")) {
            digits = "996" + digits.substring(1);
        } else if (digits.length() == 9) {
            digits = "996" + digits;
        }
        if (!digits.matches("996\\d{9}")) {
            throw new DomainException(FinanceErrors.REQUISITE_INVALID, "phone must be a Kyrgyz number 996XXXXXXXXX");
        }
        return digits;
    }
}
