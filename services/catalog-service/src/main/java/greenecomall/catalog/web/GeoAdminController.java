package greenecomall.catalog.web;

import greenecomall.catalog.geo.GeoService;
import greenecomall.catalog.web.dto.CityCreateRequest;
import greenecomall.catalog.web.dto.CityUpdateRequest;
import greenecomall.catalog.web.dto.CountryRequest;
import greenecomall.catalog.web.dto.IdResponse;
import greenecomall.catalog.web.dto.ZoneCreateRequest;
import greenecomall.catalog.web.dto.ZoneUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Админский CRUD гео-справочников. Без проверки роли — см. TODO в {@code SecurityConfig}
 * (docs/TASK-02-catalog-geo.md §5): JWT/ADMIN подключим отдельно, когда появится связь с JWKS
 * auth-service.
 */
@RestController
@RequestMapping("/admin/geo")
public class GeoAdminController {

    private final GeoService geoService;

    public GeoAdminController(GeoService geoService) {
        this.geoService = geoService;
    }

    @PostMapping("/countries")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createCountry(@Valid @RequestBody CountryRequest request) {
        return new IdResponse(geoService.createCountry(request.name(), request.isoCode()));
    }

    @PutMapping("/countries/{id}")
    public void updateCountry(@PathVariable UUID id, @Valid @RequestBody CountryRequest request) {
        geoService.updateCountry(id, request.name(), request.isoCode());
    }

    @DeleteMapping("/countries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCountry(@PathVariable UUID id) {
        geoService.deleteCountry(id);
    }

    @PostMapping("/cities")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createCity(@Valid @RequestBody CityCreateRequest request) {
        UUID id = geoService.createCity(
                request.countryId(), request.name(), request.lat(), request.lon(), request.timezone());
        return new IdResponse(id);
    }

    @PutMapping("/cities/{id}")
    public void updateCity(@PathVariable UUID id, @Valid @RequestBody CityUpdateRequest request) {
        geoService.updateCity(id, request.name(), request.lat(), request.lon(), request.timezone());
    }

    @DeleteMapping("/cities/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCity(@PathVariable UUID id) {
        geoService.deleteCity(id);
    }

    @PostMapping("/zones")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse createZone(@Valid @RequestBody ZoneCreateRequest request) {
        return new IdResponse(geoService.createZone(request.cityId(), request.name(), request.radiusM()));
    }

    @PutMapping("/zones/{id}")
    public void updateZone(@PathVariable UUID id, @Valid @RequestBody ZoneUpdateRequest request) {
        geoService.updateZone(id, request.name(), request.radiusM());
    }

    @DeleteMapping("/zones/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteZone(@PathVariable UUID id) {
        geoService.deleteZone(id);
    }
}
