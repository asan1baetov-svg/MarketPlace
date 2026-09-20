package greenecomall.catalog.web;

import greenecomall.catalog.web.dto.CityCreateRequest;
import greenecomall.catalog.web.dto.CityResponse;
import greenecomall.catalog.web.dto.CountryRequest;
import greenecomall.catalog.web.dto.CountryResponse;
import greenecomall.catalog.web.dto.IdResponse;
import greenecomall.catalog.web.dto.ZoneCreateRequest;
import greenecomall.common.security.Roles;
import greenecomall.common.test.TestJwts;
import greenecomall.common.test.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Полный путь через реальные HTTP-контроллеры против настоящего Postgres (Testcontainers). */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestSecurityConfig.class)
class GeoFlowIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private TestRestTemplate rest;

    @Test
    void countryCityZone_createListDeleteInDependencyOrder() {
        assertThat(rest.postForEntity("/admin/geo/countries", new CountryRequest("X", "XX"), String.class)
                .getStatusCode()).as("админка без токена").isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(rest.getForEntity("/catalog/geo/countries", String.class).getStatusCode())
                .as("гео-справочник публичный").isEqualTo(HttpStatus.OK);

        String adminBearer = TestJwts.bearer(UUID.randomUUID(), Roles.ADMIN);
        rest.getRestTemplate().getInterceptors().add((request, body, execution) -> {
            request.getHeaders().set(HttpHeaders.AUTHORIZATION, adminBearer);
            return execution.execute(request, body);
        });

        ResponseEntity<IdResponse> country = rest.postForEntity(
                "/admin/geo/countries", new CountryRequest("Kyrgyzstan", "KG"), IdResponse.class);
        assertThat(country.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var countryId = country.getBody().id();

        ResponseEntity<String> duplicateCode = rest.postForEntity(
                "/admin/geo/countries", new CountryRequest("Other", "kg"), String.class);
        assertThat(duplicateCode.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        ResponseEntity<IdResponse> city = rest.postForEntity(
                "/admin/geo/cities", new CityCreateRequest(countryId, "Bishkek", null, null, null), IdResponse.class);
        assertThat(city.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var cityId = city.getBody().id();

        ResponseEntity<IdResponse> zone = rest.postForEntity(
                "/admin/geo/zones", new ZoneCreateRequest(cityId, "Center", 5000), IdResponse.class);
        assertThat(zone.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var zoneId = zone.getBody().id();

        ResponseEntity<CountryResponse[]> countries =
                rest.getForEntity("/catalog/geo/countries", CountryResponse[].class);
        assertThat(countries.getBody()).extracting(CountryResponse::isoCode).contains("KG");

        ResponseEntity<CityResponse[]> cities =
                rest.getForEntity("/catalog/geo/cities?countryId=" + countryId, CityResponse[].class);
        assertThat(cities.getBody()).extracting(CityResponse::name).contains("Bishkek");

        // страну с городом удалить нельзя
        ResponseEntity<String> deleteCountryWithCity =
                rest.exchange("/admin/geo/countries/" + countryId, org.springframework.http.HttpMethod.DELETE,
                        null, String.class);
        assertThat(deleteCountryWithCity.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // город с зоной удалить нельзя
        ResponseEntity<String> deleteCityWithZone =
                rest.exchange("/admin/geo/cities/" + cityId, org.springframework.http.HttpMethod.DELETE,
                        null, String.class);
        assertThat(deleteCityWithZone.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

        // сносим по порядку: зона -> город -> страна
        rest.delete("/admin/geo/zones/" + zoneId);
        rest.delete("/admin/geo/cities/" + cityId);
        ResponseEntity<Void> deleteCountry =
                rest.exchange("/admin/geo/countries/" + countryId, org.springframework.http.HttpMethod.DELETE,
                        null, Void.class);
        assertThat(deleteCountry.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        List<CountryResponse> remaining =
                List.of(rest.getForEntity("/catalog/geo/countries", CountryResponse[].class).getBody());
        assertThat(remaining).extracting(CountryResponse::isoCode).doesNotContain("KG");
    }
}
