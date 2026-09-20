package greenecomall.catalog.web;

import greenecomall.catalog.CatalogErrors;
import greenecomall.catalog.domain.Shop;
import greenecomall.catalog.domain.ShopStatus;
import greenecomall.catalog.shop.ShopService;
import greenecomall.catalog.web.dto.IdResponse;
import greenecomall.catalog.web.dto.PublicShopResponse;
import greenecomall.catalog.web.dto.ShopRegisterRequest;
import greenecomall.catalog.web.dto.ShopResponse;
import greenecomall.common.domain.DomainException;
import greenecomall.common.security.AuthPrincipal;
import greenecomall.common.web.PageResponse;
import greenecomall.common.web.security.Authz;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class ShopController {

    private final ShopService shopService;

    public ShopController(ShopService shopService) {
        this.shopService = shopService;
    }

    /** Заявка на регистрацию магазина — уходит сразу на модерацию (см. {@code ShopService.register}). */
    @PostMapping("/shops")
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse register(@Valid @RequestBody ShopRegisterRequest request, AuthPrincipal principal) {
        UUID id = shopService.register(
                Authz.require(principal).userId(), request.name(), request.legalInfo(), request.countryId(), request.cityId());
        return new IdResponse(id);
    }

    /** Публично — только активные магазины и без служебных полей. */
    @GetMapping("/catalog/shops/{id}")
    public PublicShopResponse get(@PathVariable UUID id) {
        Shop shop = shopService.get(id);
        if (shop.getStatus() != ShopStatus.ACTIVE) {
            throw new DomainException(CatalogErrors.SHOP_NOT_FOUND, "shop not found: " + id);
        }
        return PublicShopResponse.from(shop);
    }

    /** Магазины пользователя из токена — точка входа в кабинет магазина. */
    @GetMapping("/shops/mine")
    public PageResponse<ShopResponse> mine(AuthPrincipal principal, Pageable pageable) {
        Page<Shop> page = shopService.listOwnedBy(Authz.require(principal).userId(), pageable);
        return PageResponse.of(page.getContent().stream().map(ShopResponse::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements());
    }

    /** Полная карточка (реквизиты, статус модерации) — владельцу и админу. */
    @GetMapping("/shops/{id}")
    public ShopResponse getOwn(@PathVariable UUID id, AuthPrincipal principal) {
        Shop shop = shopService.get(id);
        Authz.requireSelfOrAdmin(principal, shop.getOwnerUserId());
        return ShopResponse.from(shop);
    }
}
