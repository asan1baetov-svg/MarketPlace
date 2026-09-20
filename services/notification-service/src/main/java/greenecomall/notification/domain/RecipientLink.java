package greenecomall.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Локальный read-model «кому слать»: события часто несут id агрегата (заказ, магазин, курьер,
 * кошелёк), а не id пользователя. Связки собираются из событий, где пользователь известен
 * ({@code OrderCreated.clientUserId}, {@code ShopApproved.ownerUserId}, {@code CourierRegistered.userId}).
 */
@Entity
@Table(name = "recipient_links")
@IdClass(RecipientLink.Key.class)
public class RecipientLink {

    public enum RefType {ORDER, SHOP, COURIER, WALLET, PAYOUT, MLM_USER}

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "ref_type", nullable = false, length = 16, updatable = false)
    private RefType refType;

    @Id
    @Column(name = "ref_id", nullable = false, length = 128, updatable = false)
    private String refId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected RecipientLink() {
    }

    public RecipientLink(RefType refType, String refId, UUID userId) {
        this.refType = refType;
        this.refId = refId;
        this.userId = userId;
    }

    public RefType getRefType() {
        return refType;
    }

    public String getRefId() {
        return refId;
    }

    public UUID getUserId() {
        return userId;
    }

    public static class Key implements Serializable {
        private RefType refType;
        private String refId;

        public Key() {
        }

        public Key(RefType refType, String refId) {
            this.refType = refType;
            this.refId = refId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && refType == k.refType && Objects.equals(refId, k.refId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(refType, refId);
        }
    }
}
