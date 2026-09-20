package greenecomall.gateway.routing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteTableTest {

    private static String serviceOf(String path) {
        return RouteTable.resolve(path).map(RouteTable.Route::service).orElse(null);
    }

    @Test
    void adminPathsGoToOwningService() {
        assertThat(serviceOf("/admin/geo/countries")).isEqualTo("catalog");
        assertThat(serviceOf("/admin/orders/1/cancel")).isEqualTo("order");
        assertThat(serviceOf("/admin/payouts/1/approve")).isEqualTo("finance");
        assertThat(serviceOf("/admin/couriers")).isEqualTo("courier");
        assertThat(serviceOf("/admin/mlm/tariffs")).isEqualTo("mlm");
        assertThat(serviceOf("/admin/notification-templates")).isEqualTo("notification");
    }

    @Test
    void prefixMatchesWholeSegmentsOnly() {
        assertThat(serviceOf("/shops/1/products")).isEqualTo("catalog");
        assertThat(serviceOf("/shop/suborders")).isEqualTo("order");
        assertThat(serviceOf("/shopping")).isNull();
    }

    @Test
    void internalAndServicePathsAreNotExposed() {
        assertThat(serviceOf("/internal/catalog/price")).isNull();
        assertThat(serviceOf("/mock-acquiring/pay/x")).isNull();
        assertThat(serviceOf("/admin/unknown")).isNull();
    }
}
