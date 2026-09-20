package greenecomall.mlm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * Тариф доступа MLM: цена «входного билета» и условие активации «покупка на X за Y дней»
 * (ТЗ §4.2, §7.3). Один тариф помечен дефолтным — применяется, если страна не задала свой.
 */
@Entity
@Table(name = "mlm_tariffs")
public class MlmTariff {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "country_id")
    private UUID countryId;

    @Column(name = "access_price_minor", nullable = false)
    private long accessPriceMinor;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "required_purchase_amount_minor", nullable = false)
    private long requiredPurchaseAmountMinor;

    @Column(name = "purchase_window_days", nullable = false)
    private int purchaseWindowDays;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(nullable = false)
    private boolean active = true;

    protected MlmTariff() {
    }

    public MlmTariff(String name, UUID countryId, long accessPriceMinor, String currency,
                     long requiredPurchaseAmountMinor, int purchaseWindowDays, boolean isDefault) {
        this.name = name;
        this.countryId = countryId;
        this.accessPriceMinor = accessPriceMinor;
        this.currency = currency;
        this.requiredPurchaseAmountMinor = requiredPurchaseAmountMinor;
        this.purchaseWindowDays = purchaseWindowDays;
        this.isDefault = isDefault;
        this.active = true;
    }

    public void update(String name, long accessPriceMinor, long requiredPurchaseAmountMinor,
                       int purchaseWindowDays, boolean active) {
        this.name = name;
        this.accessPriceMinor = accessPriceMinor;
        this.requiredPurchaseAmountMinor = requiredPurchaseAmountMinor;
        this.purchaseWindowDays = purchaseWindowDays;
        this.active = active;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public UUID getCountryId() {
        return countryId;
    }

    public long getAccessPriceMinor() {
        return accessPriceMinor;
    }

    public String getCurrency() {
        return currency;
    }

    public long getRequiredPurchaseAmountMinor() {
        return requiredPurchaseAmountMinor;
    }

    public int getPurchaseWindowDays() {
        return purchaseWindowDays;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isActive() {
        return active;
    }
}
