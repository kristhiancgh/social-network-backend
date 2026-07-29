package dev.social.shared.error;

import dev.social.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Builds the one and only error payload shape this project emits.
 *
 * <p>Everything that can fail funnels through here - the exception handler, the
 * authentication entry point, the access-denied handler - so there is exactly
 * one place that decides what an error looks like on the wire. Without this,
 * the security filters (which run outside {@code @RestControllerAdvice}) would
 * quietly answer with Spring's default JSON and clients would face two
 * different error formats.
 *
 * <p>Produced payload:
 * <pre>{@code
 * {
 *   "type":      "https://social.dev/errors/post-not-found",
 *   "title":     "Post not found",
 *   "status":    404,
 *   "detail":    "Post not found: 6f1c...",
 *   "instance":  "/api/posts/6f1c...",
 *   "errorCode": "POST_NOT_FOUND",
 *   "traceId":   "0f8a2c3e-...",
 *   "timestamp": "2026-07-27T10:15:30.123Z",
 *   "service":   "post-service",
 *   "errors":    [ { "field": "message", "message": "must not be blank" } ]
 * }
 * }</pre>
 */
public class ProblemDetailFactory {

    private final String serviceName;

    public ProblemDetailFactory(String serviceName) {
        this.serviceName = serviceName;
    }

    public ProblemDetail create(ErrorCode errorCode, String detail, String instance) {
        return create(errorCode, detail, instance, List.of());
    }

    public ProblemDetail create(ErrorCode errorCode,
                                String detail,
                                String instance,
                                List<FieldViolation> violations) {

        ProblemDetail problem = ProblemDetail.forStatus(errorCode.status());
        problem.setType(URI.create(errorCode.type()));
        problem.setTitle(errorCode.title());
        problem.setDetail(detail);
        if (instance != null) {
            problem.setInstance(URI.create(instance));
        }

        problem.setProperty(ErrorContract.ERROR_CODE, errorCode.code());
        problem.setProperty(ErrorContract.TRACE_ID, CorrelationIdFilter.currentTraceId());
        problem.setProperty(ErrorContract.TIMESTAMP, Instant.now().toString());
        problem.setProperty(ErrorContract.SERVICE, serviceName);

        // Omitted rather than sent empty: an always-present `errors: []` invites
        // clients to render an empty "problems" list on non-validation errors.
        if (violations != null && !violations.isEmpty()) {
            problem.setProperty(ErrorContract.ERRORS, violations);
        }
        return problem;
    }

    /** Overload for the servlet path, where the request is at hand. */
    public ProblemDetail create(ErrorCode errorCode, String detail, HttpServletRequest request) {
        return create(errorCode, detail, request == null ? null : request.getRequestURI(), List.of());
    }

    public ProblemDetail create(ErrorCode errorCode,
                                String detail,
                                HttpServletRequest request,
                                List<FieldViolation> violations) {
        return create(errorCode, detail, request == null ? null : request.getRequestURI(), violations);
    }

    public String getServiceName() {
        return serviceName;
    }
}
