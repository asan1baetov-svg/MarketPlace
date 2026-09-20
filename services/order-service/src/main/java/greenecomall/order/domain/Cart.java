package greenecomall.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Корзина клиента. Одна на пользователя; товары в ней — из магазинов одного города
 * ({@code cityId} фиксируется первым добавленным товаром).
 */
@Entity
@Table(name = "carts")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "client_user_id", nullable = false, updatable = false, unique = true)
    private UUID clientUserId;

    @Column(name = "city_id", nullable = false)
    private UUID cityId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Cart() {
    }

    public Cart(UUID clientUserId, UUID cityId) {
        this.clientUserId = clientUserId;
        this.cityId = cityId;
    }

    public void setCityId(UUID cityId) {
        this.cityId = cityId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getClientUserId() {
        return clientUserId;
    }

    public UUID getCityId() {
        return cityId;
    }
}
