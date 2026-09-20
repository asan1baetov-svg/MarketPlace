package greenecomall.catalog.geo;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.City;
import greenecomall.catalog.domain.Country;
import greenecomall.catalog.repo.CityRepository;
import greenecomall.catalog.repo.CountryRepository;
import greenecomall.catalog.repo.DeliveryZoneRepository;
import greenecomall.common.domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GeoServiceTest {

    @Mock
    private CountryRepository countries;
    @Mock
    private CityRepository cities;
    @Mock
    private DeliveryZoneRepository zones;

    private GeoService geoService;

    @BeforeEach
    void setUp() {
        geoService = new GeoService(countries, cities, zones);
    }

    @Test
    void createCountry_withDuplicateIsoCode_isRejected() {
        when(countries.existsByIsoCode("KG")).thenReturn(true);

        assertThatThrownBy(() -> geoService.createCountry("Kyrgyzstan", "kg"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.COUNTRY_CODE_TAKEN);
    }

    @Test
    void createCity_withUnknownCountry_isRejected() {
        UUID countryId = UUID.randomUUID();
        when(countries.findById(countryId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> geoService.createCity(countryId, "Bishkek", null, null, null))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.COUNTRY_NOT_FOUND);
    }

    @Test
    void createZone_withUnknownCity_isRejected() {
        UUID cityId = UUID.randomUUID();
        when(cities.findById(cityId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> geoService.createZone(cityId, "Center", 1000))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.CITY_NOT_FOUND);
    }

    @Test
    void deleteCountry_withExistingCities_isRejected() {
        UUID countryId = UUID.randomUUID();
        Country country = new Country("Kyrgyzstan", "KG");
        when(countries.findById(countryId)).thenReturn(Optional.of(country));
        when(cities.existsByCountryId(countryId)).thenReturn(true);

        assertThatThrownBy(() -> geoService.deleteCountry(countryId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.GEO_HAS_DEPENDENTS);
    }

    @Test
    void deleteCity_withExistingZones_isRejected() {
        UUID cityId = UUID.randomUUID();
        City city = new City(new Country("Kyrgyzstan", "KG"), "Bishkek", null, null, null);
        when(cities.findById(cityId)).thenReturn(Optional.of(city));
        when(zones.existsByCityId(cityId)).thenReturn(true);

        assertThatThrownBy(() -> geoService.deleteCity(cityId))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.GEO_HAS_DEPENDENTS);
    }

    @Test
    void updateCountry_withCodeTakenBySomeoneElse_isRejected() {
        UUID id = UUID.randomUUID();
        Country country = new Country("Kyrgyzstan", "KG");
        when(countries.findById(id)).thenReturn(Optional.of(country));
        when(countries.existsByIsoCodeAndIdNot("KZ", id)).thenReturn(true);

        assertThatThrownBy(() -> geoService.updateCountry(id, "Kazakhstan", "kz"))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.COUNTRY_CODE_TAKEN);
    }

    @Test
    void deleteZone_whenMissing_isRejected() {
        UUID id = UUID.randomUUID();
        when(zones.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> geoService.deleteZone(id))
                .isInstanceOf(DomainException.class)
                .extracting(ex -> ((DomainException) ex).getCode())
                .isEqualTo(CatalogErrors.ZONE_NOT_FOUND);
    }
}
