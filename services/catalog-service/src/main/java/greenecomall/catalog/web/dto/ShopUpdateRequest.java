package greenecomall.catalog.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param address адрес, по которому курьер забирает заказ
 * @param phone   телефон магазина для курьера и поддержки
 */
public record ShopUpdateRequest(
        @NotBlank @Size(max = 200) String name, String legalInfo,
        @Size(max = 300) String address, @Size(max = 20) String phone) {
}
