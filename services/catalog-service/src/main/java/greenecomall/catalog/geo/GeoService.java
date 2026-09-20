package greenecomall.catalog.geo;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.domain.DeliveryZone;
import greenecomall.catalog.repo.CityRepository;
import greenecomall.catalog.repo.CountryRepository;
import greenecomall.catalog.repo.DeliveryZoneRepository;
import greenecomall.common.domain.DomainException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * CRUD гео-справочников: страны → города → зоны доставки. Один сервис на все три сущности —
 * они образуют одну строгую иерархию и не разрастаются, отдельные сервисы на каждую не нужны.
 */
@Service
public class GeoService {

    private final CountryRepository countries;
    private final CityRepository cities;
    private final DeliveryZoneRepository zones;

    public GeoService(CountryRepository countries, CityRepository cities, DeliveryZoneRepository zones) {
        this.countries = countries;
        this.cities = cities;
        this.zones = zones;
    }

    @Transactional(readOnly = true)
    public List<Country> listCountries() {
        return countries.findAll();
    }

    @Transactional(readOnly = true)
    public List<City> listCitiesByCountry(UUID countryId) {
        return cities.findByCountryId(countryId);
    }

    @Transactional
    public UUID createCountry(String name, String isoCode) {
        String code = normalizeIsoCode(isoCode);
        if (countries.existsByIsoCode(code)) {
            throw new DomainException(CatalogErrors.COUNTRY_CODE_TAKEN, "country code already exists: " + code);
        }
        return countries.save(new Country(name, code)).getId();
    }

    @Transactional
    public void updateCountry(UUID id, String name, String isoCode) {
        Country country = requireCountry(id);
        String code = normalizeIsoCode(isoCode);
        if (countries.existsByIsoCodeAndIdNot(code, id)) {
            throw new DomainException(CatalogErrors.COUNTRY_CODE_TAKEN, "country code already exists: " + code);
        }
        country.rename(name, code);
    }

    @Transactional
    public void deleteCountry(UUID id) {
        requireCountry(id);
        if (cities.existsByCountryId(id)) {
            throw new DomainException(CatalogErrors.GEO_HAS_DEPENDENTS, "country has cities, delete them first");
        }
        countries.deleteById(id);
    }

    @Transactional
    public UUID createCity(UUID countryId, String name, BigDecimal lat, BigDecimal lon, String timezone) {
        Country country = requireCountry(countryId);
        return cities.save(new City(country, name, lat, lon, timezone)).getId();
    }

    @Transactional
    public void updateCity(UUID id, String name, BigDecimal lat, BigDecimal lon, String timezone) {
        City city = requireCity(id);
        city.update(name, lat, lon, timezone);
    }

    @Transactional
    public void deleteCity(UUID id) {
        requireCity(id);
        if (zones.existsByCityId(id)) {
            throw new DomainException(CatalogErrors.GEO_HAS_DEPENDENTS, "city has delivery zones, delete them first");
        }
        cities.deleteById(id);
    }

    @Transactional
    public UUID createZone(UUID cityId, String name, Integer radiusM) {
        City city = requireCity(cityId);
        return zones.save(new DeliveryZone(city, name, radiusM)).getId();
    }

    @Transactional
    public void updateZone(UUID id, String name, Integer radiusM) {
        DeliveryZone zone = requireZone(id);
        zone.update(name, radiusM);
    }

    @Transactional
    public void deleteZone(UUID id) {
        requireZone(id);
        zones.deleteById(id);
    }

    private Country requireCountry(UUID id) {
        return countries.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.COUNTRY_NOT_FOUND, "country not found: " + id));
    }

    private City requireCity(UUID id) {
        return cities.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.CITY_NOT_FOUND, "city not found: " + id));
    }

    private DeliveryZone requireZone(UUID id) {
        return zones.findById(id)
                .orElseThrow(() -> new DomainException(CatalogErrors.ZONE_NOT_FOUND, "zone not found: " + id));
    }

    private static String normalizeIsoCode(String isoCode) {
        return isoCode == null ? null : isoCode.trim().toUpperCase();
    }
}
