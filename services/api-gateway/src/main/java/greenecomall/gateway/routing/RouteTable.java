package greenecomall.gateway.routing;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Карта внешних путей на сервисы. Внешний путь {@code /api/<x>} проксируется как {@code /<x>}
 * в сервис, чей префикс совпал длиннее всех. {@code /internal/**} и служебные пути наружу
 * не публикуются — у них нет маршрута, гейтвей отвечает 404.
 */
public final class RouteTable {

    public record Route(String prefix, String service) {

        boolean matches(String path) {
            return path.equals(prefix) || path.startsWith(prefix + "/");
        }
    }

    private static final List<Route> ROUTES = List.of(
            new Route("/auth", "auth"),

            new Route("/catalog", "catalog"),
            new Route("/shops", "catalog"),
            new Route("/products", "catalog"),
            new Route("/admin/categories", "catalog"),
            new Route("/admin/geo", "catalog"),
            new Route("/admin/markup-rules", "catalog"),
            new Route("/admin/products", "catalog"),
            new Route("/admin/shops", "catalog"),
            new Route("/reviews", "catalog"),
            new Route("/admin/reviews", "catalog"),

            new Route("/cart", "order"),
            new Route("/orders", "order"),
            new Route("/shop/suborders", "order"),
            new Route("/admin/orders", "order"),
            new Route("/admin/promocodes", "order"),

            new Route("/payments", "finance"),
            new Route("/wallet", "finance"),
            new Route("/webhooks/acquiring", "finance"),
            new Route("/admin/payouts", "finance"),
            new Route("/admin/wallets", "finance"),
            new Route("/admin/requisites", "finance"),
            new Route("/admin/refunds", "finance"),

            new Route("/couriers", "courier"),
            new Route("/assignments", "courier"),
            new Route("/admin/couriers", "courier"),
            new Route("/admin/assignments", "courier"),

            new Route("/mlm", "mlm"),
            new Route("/admin/mlm", "mlm"),
            new Route("/webhooks/mlm", "mlm"),

            new Route("/notifications", "notification"),
            new Route("/admin/notification-templates", "notification"),
            new Route("/admin/notifications", "notification")
    ).stream().sorted(Comparator.comparingInt((Route r) -> r.prefix().length()).reversed()).toList();

    private RouteTable() {
    }

    public static Optional<Route> resolve(String internalPath) {
        return ROUTES.stream().filter(r -> r.matches(internalPath)).findFirst();
    }
}
