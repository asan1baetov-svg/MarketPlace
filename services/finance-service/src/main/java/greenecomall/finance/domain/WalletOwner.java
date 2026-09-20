package greenecomall.finance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Пользователь, которому принадлежат кошельки магазина/курьера {@code (ownerType, ownerRef)}.
 * Кошельков платформы здесь нет — к ним доступ только у админа.
 */
@Entity
@Table(name = "wallet_owners")
@IdClass(WalletOwner.Key.class)
public class WalletOwner {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, length = 16)
    private WalletOwnerType ownerType;

    @Id
    @Column(name = "owner_ref", nullable = false, length = 128)
    private String ownerRef;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletOwner() {
    }

    public WalletOwner(WalletOwnerType ownerType, String ownerRef, UUID userId) {
        this.ownerType = ownerType;
        this.ownerRef = ownerRef;
        this.userId = userId;
        this.createdAt = Instant.now();
    }

    public UUID getUserId() {
        return userId;
    }

    /** Составной ключ {@code (owner_type, owner_ref)}; обычный класс — JPA заполняет поля рефлексией. */
    public static class Key implements Serializable {

        private WalletOwnerType ownerType;
        private String ownerRef;

        protected Key() {
        }

        public Key(WalletOwnerType ownerType, String ownerRef) {
            this.ownerType = ownerType;
            this.ownerRef = ownerRef;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && ownerType == k.ownerType && Objects.equals(ownerRef, k.ownerRef);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownerType, ownerRef);
        }
    }
}
