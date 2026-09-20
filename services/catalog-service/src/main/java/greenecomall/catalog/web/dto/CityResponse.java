package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.City;

import java.math.BigDecimal;
import java.util.UUID;

public record CityResponse(UUID id, UUID countryId, String name, BigDecimal lat, BigDecimal lon, String timezone) {

    public static CityResponse from(City city) {
        return new CityResponse(
                city.getId(), city.getCountry().getId(), city.getName(), city.getLat(), city.getLon(),
                city.getTimezone());
    }
}
