package greenecomall.gateway.routing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * Простой реверс-прокси до подключения Spring Cloud Gateway (он пока не вышел под Boot 4.1.1,
 * см. TODO в pom.xml). Тело и ответ стримятся без буферизации; заголовки {@code X-User-*}
 * к этому моменту уже проставлены {@code GatewayIdentityPropagationFilter}.
 */
@Component
public class ProxyHandler {

    private static final Logger log = LoggerFactory.getLogger(ProxyHandler.class);
    private static final String EXTERNAL_PREFIX = "/api";
    private static final Set<String> HOP_BY_HOP = Set.of(
            HttpHeaders.HOST.toLowerCase(), HttpHeaders.CONNECTION.toLowerCase(), "keep-alive",
            HttpHeaders.TRANSFER_ENCODING.toLowerCase(), HttpHeaders.TE.toLowerCase(), HttpHeaders.TRAILER.toLowerCase(),
            HttpHeaders.PROXY_AUTHORIZATION.toLowerCase(), HttpHeaders.PROXY_AUTHENTICATE.toLowerCase(),
            HttpHeaders.UPGRADE.toLowerCase(), HttpHeaders.CONTENT_LENGTH.toLowerCase());
    /** Служебный токен межсервисных вызовов — от внешнего клиента не пропускаем никогда. */
    private static final String INTERNAL_TOKEN_HEADER = "x-internal-token";
    /** Сегменты, которыми можно выйти из префикса маршрута ({@code ..}, закодированные точки/слэши, {@code ;}). */
    private static final Pattern UNSAFE_PATH =
            Pattern.compile("(^|/)\\.\\.(/|$)|%2e|%2f|%5c|\\\\|;", Pattern.CASE_INSENSITIVE);

    private final WebClient client;
    private final GatewayRoutingProperties properties;

    public ProxyHandler(GatewayRoutingProperties properties) {
        // В Boot 4 автоконфиг WebClient.Builder вынесен в отдельный стартер — прокси он и не нужен
        this.client = WebClient.builder().build();
        this.properties = properties;
    }

    public Mono<ServerResponse> proxy(ServerRequest request) {
        String internalPath = request.path().substring(EXTERNAL_PREFIX.length());
        if (UNSAFE_PATH.matcher(request.uri().getRawPath()).find()) {
            // Spring Security уже отсекает такие пути; это второй рубеж, чтобы не выйти на /internal/**
            return problem(HttpStatus.BAD_REQUEST, "path is not normalized");
        }
        RouteTable.Route route = RouteTable.resolve(internalPath).orElse(null);
        if (route == null) {
            return problem(HttpStatus.NOT_FOUND, "no route for " + request.path());
        }
        String baseUrl = properties.services().get(route.service());
        if (baseUrl == null || baseUrl.isBlank()) {
            return problem(HttpStatus.SERVICE_UNAVAILABLE, "service '" + route.service() + "' is not configured");
        }
        String rawQuery = request.uri().getRawQuery();
        URI target = URI.create(baseUrl.replaceAll("/+$", "") + internalPath + (rawQuery == null ? "" : "?" + rawQuery));

        return client.method(request.method())
                .uri(target)
                .headers(h -> request.headers().asHttpHeaders().forEach((name, values) -> {
                    String lower = name.toLowerCase();
                    if (!HOP_BY_HOP.contains(lower) && !INTERNAL_TOKEN_HEADER.equals(lower)) {
                        h.addAll(name, values);
                    }
                }))
                .body(BodyInserters.fromDataBuffers(request.bodyToFlux(DataBuffer.class)))
                .retrieve()
                // статусы upstream (4xx/5xx) отдаём клиенту как есть, а не превращаем в исключение
                .onStatus(status -> true, upstream -> Mono.empty())
                // toEntityFlux стримит тело после выдачи заголовков; exchangeToMono освободил бы его раньше
                .toEntityFlux(DataBuffer.class)
                .flatMap(upstream -> ServerResponse.status(upstream.getStatusCode())
                        .headers(h -> upstream.getHeaders().forEach((name, values) -> {
                            if (!HOP_BY_HOP.contains(name.toLowerCase())) {
                                h.addAll(name, values);
                            }
                        }))
                        .body(BodyInserters.fromDataBuffers(upstream.getBody())))
                .timeout(properties.responseTimeout())
                .onErrorResume(TimeoutException.class,
                        e -> problem(HttpStatus.GATEWAY_TIMEOUT, route.service() + " did not respond in time"))
                .onErrorResume(e -> !(e instanceof TimeoutException), e -> {
                    log.warn("proxy to {} {} failed: {}", route.service(), target, e.toString());
                    return problem(HttpStatus.BAD_GATEWAY, route.service() + " is unavailable");
                });
    }

    private static Mono<ServerResponse> problem(HttpStatus status, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setProperty("code", "gateway." + status.name().toLowerCase());
        return ServerResponse.status(status).bodyValue(pd);
    }
}
