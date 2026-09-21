package greenecomall.catalog.shop;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.domain.ShopStatus;
import greenecomall.catalog.repo.CityRepository;
import greenecomall.catalog.repo.CountryRepository;
import greenecomall.catalog.repo.ShopRepository;
import greenecomall.catalog.support.Tracing;
import greenecomall.common.domain.DomainException;
import greenecomall.common.events.DomainEventPublisher;
import greenecomall.common.events.EventEnvelope;
import greenecomall.common.events.EventTypes;
import greenecomall.common.events.Topics;
import greenecomall.common.events.payload.CatalogEvents;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ShopService {

    private static final String PRODUCER = "catalog-service";

    private final ShopRepository shops;
    private final CountryRepository countries;
    private final CityRepository cities;
    private final DomainEventPublisher events;

    public ShopService(ShopRepository shops, CountryRepository countries, CityRepository cities,
                       DomainEventPublisher events) {
        this.shops = shops;
        this.countries = countries;
        this.cities = cities;
        this.events = events;
    }

    @Transactional
    public UUID register(UUID ownerUserId, String name, String legalInfo, String address, String phone,
                         UUID countryId, UUID cityId) {
        Country country = countries.findById(countryId)
                .orElseThrow(() -> new DomainException(CatalogErrors.COUNTRY_NOT_FOUND, "country not found: " + countryId));
        City city = cities.findById(cityId)
                .orElseThrow(() -> new DomainException(CatalogErrors.CITY_NOT_FOUND, "city not found: " + cityId));
        Shop shop = new Shop(ownerUserId, name, legalInfo, country, city);
        shop.updateProfile(name, legalInfo, address, phone);
        return shops.save(shop).getId();
    }

    @Transactional(readOnly = true)
    public Shop get(UUID id) {
        return requireShop(id);
    }

    /** Профиль магазина меняет владелец (или админ): название, реквизиты, адрес забора, телефон. */
    @Transactional
    public Shop updateProfile(UUID id, String name, String legalInfo, String address, String phone) {
        Shop shop = requireShop(id);
        shop.updateProfile(name, legalInfo, address, phone);
        return shop;
    }

    @Transactional(readOnly = true)
    public Page<Shop> listOwnedBy(UUID ownerUserId, Pageable pageable) {
        return shops.findByOwnerUserId(ownerUserId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Shop> listByStatus(ShopStatus status, Pageable pageable) {
        return shops.findByStatus(status, pageable);
    }

    @Transactional
    public void approve(UUID id) {
        Shop shop = requireShop(id);
        if (shop.getStatus() != ShopStatus.MODERATION) {
            throw new DomainException(CatalogErrors.SHOP_STATUS_INVALID, "shop is not pending moderation");
        }
        shop.approve();
        events.publish(Topics.CATALOG, shop.getId().toString(),
                EventEnvelope.of(EventTypes.SHOP_APPROVED, PRODUCER, Tracing.currentTraceId(),
                        new CatalogEvents.ShopApproved(shop.getId(), shop.getOwnerUserId(), shop.getCity().getId())));
    }

    @Transactional
    public void reject(UUID id, String reason) {
        Shop shop = requireShop(id);
        if (shop.getStatus() != ShopStatus.MODERATION) {
            throw new DomainException(CatalogErrors.SHOP_STATUS_INVALID, "shop is not pending moderation");
        }
        shop.reject(reason);
    }

    @Transactional
    public void suspend(UUID id, String reason) {
        Shop shop = requireShop(id);
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new DomainException(CatalogErrors.SHOP_STATUS_INVALID, "only an active shop can be suspended");
        }
        shop.suspend(reason);
        events.publish(Topics.CATALOG, shop.getId().toString(),
                EventEnvelope.of(EventTypes.SHOP_SUSPENDED, PRODUCER, Tracing.currentTraceId(),
                        new CatalogEvents.ShopSuspended(shop.getId(), reason)));
    }

    public Shop requireShop(UUID id) {
        return shops.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.SHOP_NOT_FOUND, "shop not found: " + id));
    }
}
