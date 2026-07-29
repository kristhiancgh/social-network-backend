package dev.social.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Comparator;
import java.util.List;

/**
 * The single place where an exception becomes an HTTP response.
 *
 * <p>Every method returns the same RFC 7807 shape built by
 * {@link ProblemDetailFactory}, so a client can parse errors from all four
 * services with one piece of code.
 *
 * <h2>Rules this class follows</h2>
 * <ul>
 *   <li><b>Never leak internals.</b> Stack traces, SQL text and constraint
 *       names stay in the logs. The client gets a code and a sentence.</li>
 *   <li><b>4xx is not an incident.</b> Client mistakes are logged at WARN with
 *       no stack trace; only 5xx gets ERROR plus the full trace. Otherwise the
 *       log fills with noise and real failures get lost.</li>
 *   <li><b>Specific before generic.</b> Spring picks the closest matching
 *       handler, so the {@link Exception} catch-all only ever fires for
 *       something genuinely unforeseen.</li>
 * </ul>
 *
 * <p>Ordered just above {@link Ordered#LOWEST_PRECEDENCE} so an individual
 * service can register its own advice at the default order and take priority
 * for a type it wants to treat differently.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE - 100)
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    // -------------------------------------------------------------------------
    //  Our own exceptions - the status already travels on the ErrorCode
    // -------------------------------------------------------------------------

    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ProblemDetail> handleApplication(ApplicationException exception,
                                                           HttpServletRequest request) {
        ErrorCode errorCode = exception.getErrorCode();
        ProblemDetail problem = problems.create(
                errorCode, exception.getMessage(), request, exception.getViolations());

        logByStatus(errorCode, exception, request);
        return ResponseEntity.status(errorCode.status()).body(problem);
    }

    // -------------------------------------------------------------------------
    //  Bean Validation
    // -------------------------------------------------------------------------

    /** Fired by {@code @Valid @RequestBody}. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleBodyValidation(MethodArgumentNotValidException exception,
                                                              HttpServletRequest request) {

        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldViolation(
                        error.getField(),
                        error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage()))
                // Stable ordering keeps responses (and the tests asserting on
                // them) deterministic; Hibernate Validator does not guarantee it.
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        String detail = violations.size() == 1
                ? "%s %s".formatted(violations.getFirst().field(), violations.getFirst().message())
                : "%d fields failed validation".formatted(violations.size());

        return respond(CommonErrorCode.VALIDATION_ERROR, detail, request, violations, exception);
    }

    /** Fired by {@code @Validated} on path variables and query parameters. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintValidation(ConstraintViolationException exception,
                                                                    HttpServletRequest request) {

        List<FieldViolation> violations = exception.getConstraintViolations()
                .stream()
                .map(violation -> new FieldViolation(
                        lastPathNode(String.valueOf(violation.getPropertyPath())),
                        violation.getMessage()))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        return respond(CommonErrorCode.VALIDATION_ERROR,
                "%d parameters failed validation".formatted(violations.size()),
                request, violations, exception);
    }

    /** Spring 6.1+ raises this for validated controller method arguments. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ProblemDetail> handleMethodValidation(HandlerMethodValidationException exception,
                                                                 HttpServletRequest request) {

        List<FieldViolation> violations = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> result.getResolvableErrors()
                        .stream()
                        .map(error -> new FieldViolation(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage())))
                .sorted(Comparator.comparing(FieldViolation::field))
                .toList();

        return respond(CommonErrorCode.VALIDATION_ERROR,
                "Request parameters failed validation",
                request, violations, exception);
    }

    // -------------------------------------------------------------------------
    //  Malformed requests
    // -------------------------------------------------------------------------

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleUnreadableBody(HttpMessageNotReadableException exception,
                                                              HttpServletRequest request) {
        // The parser's own message quotes the offending payload, which would
        // reflect user input straight back. Only the generic sentence goes out.
        return respond(CommonErrorCode.MALFORMED_REQUEST,
                "Request body is missing or is not valid JSON",
                request, List.of(), exception);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ProblemDetail> handleTypeMismatch(MethodArgumentTypeMismatchException exception,
                                                            HttpServletRequest request) {
        String expectedType = exception.getRequiredType() == null
                ? "the expected type"
                : exception.getRequiredType().getSimpleName();

        return respond(CommonErrorCode.INVALID_PARAMETER,
                "Parameter '%s' is not a valid %s".formatted(exception.getName(), expectedType),
                request,
                List.of(new FieldViolation(exception.getName(), "must be a valid " + expectedType)),
                exception);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ProblemDetail> handleMissingParameter(MissingServletRequestParameterException exception,
                                                                HttpServletRequest request) {
        return respond(CommonErrorCode.MISSING_PARAMETER,
                "Required parameter '%s' is missing".formatted(exception.getParameterName()),
                request,
                List.of(new FieldViolation(exception.getParameterName(), "is required")),
                exception);
    }

    // -------------------------------------------------------------------------
    //  Routing
    // -------------------------------------------------------------------------

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException exception,
                                                          HttpServletRequest request) {
        return respond(CommonErrorCode.RESOURCE_NOT_FOUND,
                "No endpoint %s %s".formatted(request.getMethod(), request.getRequestURI()),
                request, List.of(), exception);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception,
                                                                   HttpServletRequest request) {
        String supported = exception.getSupportedHttpMethods() == null
                ? "none"
                : exception.getSupportedHttpMethods().toString();

        return respond(CommonErrorCode.METHOD_NOT_ALLOWED,
                "Method %s is not supported for this endpoint. Supported: %s"
                        .formatted(exception.getMethod(), supported),
                request, List.of(), exception);
    }

    // -------------------------------------------------------------------------
    //  Security
    //
    //  These only fire for failures raised *inside* the dispatcher. Rejections
    //  from the filter chain never reach an @ExceptionHandler, which is why
    //  ProblemDetailAuthenticationEntryPoint and ProblemDetailAccessDeniedHandler
    //  exist and reuse the same factory.
    // -------------------------------------------------------------------------

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException exception,
                                                              HttpServletRequest request) {
        return respond(CommonErrorCode.UNAUTHENTICATED,
                "Authentication is required to access this resource",
                request, List.of(), exception);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException exception,
                                                            HttpServletRequest request) {
        return respond(CommonErrorCode.ACCESS_DENIED,
                "You are not allowed to perform this action",
                request, List.of(), exception);
    }

    // -------------------------------------------------------------------------
    //  Persistence
    // -------------------------------------------------------------------------

    /**
     * Last line of defence for constraint violations the service did not
     * anticipate. A service that knows a given constraint can fire should catch
     * it and throw a {@link ConflictException} with a meaningful code instead of
     * letting it land here.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException exception,
                                                             HttpServletRequest request) {

        String constraint = SqlErrorSupport.violatedConstraint(exception).orElse("unknown");
        log.warn("Unmapped constraint violation [{}] on {} {} - consider handling it in the service layer",
                constraint, request.getMethod(), request.getRequestURI());

        // The constraint name hints at the schema, so it is logged, not returned.
        return respond(CommonErrorCode.DATA_INTEGRITY_VIOLATION,
                "The request conflicts with data that already exists",
                request, List.of(), exception);
    }

    // -------------------------------------------------------------------------
    //  Catch-all
    // -------------------------------------------------------------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception exception,
                                                          HttpServletRequest request) {
        // Full trace to the log, nothing but a code to the client: an exception
        // message can carry a connection string or a query.
        log.error("Unhandled exception on {} {}", request.getMethod(), request.getRequestURI(), exception);

        ProblemDetail problem = problems.create(
                CommonErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred. Quote the traceId when reporting it.",
                request);

        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status()).body(problem);
    }

    // -------------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<ProblemDetail> respond(ErrorCode errorCode,
                                                  String detail,
                                                  HttpServletRequest request,
                                                  List<FieldViolation> violations,
                                                  Exception exception) {

        ProblemDetail problem = problems.create(errorCode, detail, request, violations);
        logByStatus(errorCode, exception, request);
        return ResponseEntity.status(errorCode.status()).body(problem);
    }

    private void logByStatus(ErrorCode errorCode, Exception exception, HttpServletRequest request) {
        if (errorCode.status().is5xxServerError()) {
            log.error("{} on {} {}", errorCode.code(), request.getMethod(), request.getRequestURI(), exception);
        } else {
            log.warn("{} on {} {}: {}",
                    errorCode.code(), request.getMethod(), request.getRequestURI(), exception.getMessage());
        }
    }

    /** {@code createPost.request.message} -> {@code message}. */
    private String lastPathNode(String propertyPath) {
        int lastDot = propertyPath.lastIndexOf('.');
        return lastDot < 0 ? propertyPath : propertyPath.substring(lastDot + 1);
    }
}
