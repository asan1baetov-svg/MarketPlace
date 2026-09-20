package greenecomall.finance.support;

import org.slf4j.MDC;

import java.util.UUID;

/** Временный источник traceId для конверта события, пока не подключён Micrometer Tracing. */
public final class Tracing {

    private Tracing() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
