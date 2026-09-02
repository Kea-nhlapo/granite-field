package za.co.trademesh.shared.web;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class RequestObservabilityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);

    private final MeterRegistry metrics;

    public RequestObservabilityFilter(MeterRegistry metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        UUID requestId = requestId(request.getHeader(RequestContext.REQUEST_ID_HEADER));
        request.setAttribute(RequestContext.REQUEST_ID_ATTRIBUTE, requestId);
        response.setHeader(RequestContext.REQUEST_ID_HEADER, requestId.toString());
        long startedAt = System.nanoTime();

        try (MDC.MDCCloseable ignored = MDC.putCloseable("request_id", requestId.toString())) {
            try {
                filterChain.doFilter(request, response);
            } finally {
                long elapsed = System.nanoTime() - startedAt;
                String status = Integer.toString(response.getStatus());
                Timer.builder("trademesh.http.request.duration")
                        .description("Time spent handling TradeMesh HTTP requests")
                        .tag("method", safeMethod(request.getMethod()))
                        .tag("status", status)
                        .register(metrics)
                        .record(Duration.ofNanos(elapsed));

                // This is intentionally an allow-list. Never add paths, query
                // strings, headers, bodies, account identifiers, or coordinates.
                log.info(
                        "HTTP request completed method={} status={} durationMs={}",
                        safeMethod(request.getMethod()),
                        status,
                        Duration.ofNanos(elapsed).toMillis());
            }
        }
    }

    private static UUID requestId(String candidate) {
        try {
            return candidate == null ? UUID.randomUUID() : UUID.fromString(candidate);
        } catch (IllegalArgumentException invalid) {
            return UUID.randomUUID();
        }
    }

    private static String safeMethod(String method) {
        return method != null && method.matches("[A-Z]{3,10}") ? method : "UNKNOWN";
    }
}
