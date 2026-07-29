package dev.social.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.social.shared.error.CommonErrorCode;
import dev.social.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Answers "you need to log in" in the project's error format.
 *
 * <p>Spring Security rejects unauthenticated requests inside the filter chain,
 * which sits <em>before</em> the DispatcherServlet - so no
 * {@code @ExceptionHandler} ever sees them. Without this class those 401s would
 * come back as Spring's own JSON while every other error used RFC 7807, and a
 * client would need two parsers.
 */
public class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    public ProblemDetailAuthenticationEntryPoint(ProblemDetailFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        ProblemDetail problem = problems.create(
                CommonErrorCode.UNAUTHENTICATED,
                "Authentication is required. Send a valid Bearer token.",
                request);

        response.setStatus(CommonErrorCode.UNAUTHENTICATED.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
