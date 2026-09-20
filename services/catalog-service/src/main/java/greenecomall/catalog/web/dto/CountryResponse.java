package greenecomall.catalog.web.dto;

import greenecomall.catalog.domain.Country;

import java.util.UUID;

public record CountryResponse(UUID id, String name, String isoCode) {

    public static CountryResponse from(Country country) {
        return new CountryResponse(country.getId(), country.getName(), country.getIsoCode());
    }
}
