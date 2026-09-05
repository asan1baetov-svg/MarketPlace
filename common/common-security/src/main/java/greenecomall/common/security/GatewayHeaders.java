package greenecomall.common.security;

/**
 * Заголовки, которые api-gateway проставляет после валидации JWT и прокидывает вниз по цепочке.
 * Внутренние сервисы доверяют этим заголовкам только на защищённом сетевом периметре;
 * снаружи периметра каждый сервис валидирует JWT сам.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String CLIENT_TYPE = "X-Client-Type";
    public static final String TRACE_ID = "X-Trace-Id";

    private GatewayHeaders() {
    }
}
