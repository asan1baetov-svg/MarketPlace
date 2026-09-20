package greenecomall.order.web;

import greenecomall.common.security.Roles;
import greenecomall.common.test.TestJwts;
import greenecomall.common.test.TestSecurityConfig;
import greenecomall.common.web.security.InternalTokenFilter;
import greenecomall.order.cart.CartService;
import greenecomall.order.catalog.CatalogClient;
import greenecomall.order.order.OrderService;
import greenecomall.order.order.ShopSuborderService;
import greenecomall.order.order.SuborderWorkflow;
import greenecomall.order.promo.PromocodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Модель доступа order-service: личность клиента — только из access-JWT, кабинет магазина — только
 * владельцу магазина, {@code /internal/**} — только с {@code X-Internal-Token}, {@code /admin/**} — только ADMIN.
 */
@WebMvcTest(properties = "gem.security.internal-token=" + TestSecurityConfig.INTERNAL_TOKEN)
@Import({TestSecurityConfig.class, ShopAccess.class})
class OrderSecurityWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CartService cartService;
    @MockitoBean
    private OrderService orderService;
    @MockitoBean
    private ShopSuborderService shopSuborders;
    @MockitoBean
    private SuborderWorkflow workflow;
    @MockitoBean
    private PromocodeService promocodes;
    @MockitoBean
    private CatalogClient catalog;

    @Test
    void cart_withoutToken_is401ProblemJson() throws Exception {
        mvc.perform(get("/cart"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("security.unauthorized"));
    }

    @Test
    void cart_isResolvedFromTokenSubject() throws Exception {
        UUID client = UUID.randomUUID();
        when(cartService.view(client)).thenReturn(new CartService.CartView(UUID.randomUUID(), null, List.of()));

        mvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(client, Roles.CLIENT_EXTERNAL)))
                .andExpect(status().isOk());
        verify(cartService).view(client);
    }

    @Test
    void cart_withTamperedToken_is401() throws Exception {
        String token = TestJwts.token(UUID.randomUUID(), Roles.CLIENT_EXTERNAL);
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        mvc.perform(get("/cart").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shopSuborderAccept_byShopOwner_isAllowed() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID shopId = UUID.randomUUID();
        UUID suborderId = UUID.randomUUID();
        when(catalog.shop(shopId)).thenReturn(new CatalogClient.ShopView(shopId, owner, "ACTIVE"));

        mvc.perform(post("/shop/suborders/{id}/accept", suborderId).param("shopId", shopId.toString())
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(owner, Roles.SHOP)))
                .andExpect(status().isNoContent());
        verify(shopSuborders).accept(suborderId, shopId);
    }

    @Test
    void shopSuborderAccept_forSomeoneElsesShop_is403() throws Exception {
        UUID shopId = UUID.randomUUID();
        when(catalog.shop(shopId)).thenReturn(new CatalogClient.ShopView(shopId, UUID.randomUUID(), "ACTIVE"));

        mvc.perform(post("/shop/suborders/{id}/accept", UUID.randomUUID()).param("shopId", shopId.toString())
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.SHOP)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("security.forbidden"));
        verify(shopSuborders, never()).accept(any(), eq(shopId));
    }

    @Test
    void internal_requiresInternalToken_notJwt() throws Exception {
        UUID orderId = UUID.randomUUID();
        mvc.perform(get("/internal/orders/{id}", orderId)
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.ADMIN)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("security.internal_token_invalid"));
        mvc.perform(get("/internal/orders/{id}", orderId).header(InternalTokenFilter.HEADER, "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_requiresAdminRole() throws Exception {
        mvc.perform(get("/admin/orders")
                        .header(HttpHeaders.AUTHORIZATION, TestJwts.bearer(UUID.randomUUID(), Roles.CLIENT_EXTERNAL)))
                .andExpect(status().isForbidden());
    }
}
