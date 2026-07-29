package dev.social.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Gives every request a trace id, so one user action can be followed across
 * four services and four log files.
 *
 * <p>If the caller already sent {@code X-Trace-Id} it is reused - that is what
 * lets the gateway, or the browser, stitch a whole chain together. Otherwise a
 * fresh one is minted.
 *
 * <p>Runs at {@link Ordered#HIGHEST_PRECEDENCE} so the id exists before the
 * security filters get a chance to reject the request: a 401 is exactly the
 * kind of event you want to be able to trace.
 *
 * <p>The {@code finally} block is not optional. Servlet containers pool their
 * threads, so an MDC entry left behind would silently reappear on somebody
 * else's request.
 */
public class CorrelationIdFilter extends OncePerRequestFilter implements Ordered {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = resolveTraceId(request);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER_NAME, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveTraceId(HttpServletRequest request) {
        String incoming = request.getHeader(HEADER_NAME);
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        String sanitised = incoming.replaceAll("[^A-Za-z0-9._-]", "");
        return sanitised.isBlank() ? UUID.randomUUID().toString()
                                   : sanitised.substring(0, Math.min(64, sanitised.length()));
    }

    /** Current trace id, or {@code "unknown"} outside a request. */
    public static String currentTraceId() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null ? "unknown" : traceId;
    }
}
