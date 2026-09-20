package greenecomall.catalog.web;

import greenecomall.catalog.geo.GeoService;
import greenecomall.catalog.web.dto.CityResponse;
import greenecomall.catalog.web.dto.CountryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Публичная витрина справочников — для селектора страны/города на клиенте. */
@RestController
@RequestMapping("/catalog/geo")
public class GeoPublicController {

    private final GeoService geoService;

    public GeoPublicController(GeoService geoService) {
        this.geoService = geoService;
    }

    @GetMapping("/countries")
    public List<CountryResponse> countries() {
        return geoService.listCountries().stream().map(CountryResponse::from).toList();
    }

    @GetMapping("/cities")
    public List<CityResponse> cities(@RequestParam UUID countryId) {
        return geoService.listCitiesByCountry(countryId).stream().map(CityResponse::from).toList();
    }
}
