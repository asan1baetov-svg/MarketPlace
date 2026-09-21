package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * {@code legalInfo} — произвольный JSON-текст (реквизиты и т.п.), сохраняется как есть.
 * Владелец магазина — пользователь из access-JWT.
 */
public record ShopRegisterRequest(
        @NotBlank String name, String legalInfo, String address, String phone,
        @NotNull UUID countryId, @NotNull UUID cityId) {
}
