package dev.social.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.social.shared.error.CommonErrorCode;
import dev.social.shared.error.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/**
 * Answers "you are logged in, but this is not yours" in the project's error
 * format. Same reasoning as {@link ProblemDetailAuthenticationEntryPoint}: the
 * rejection happens in the filter chain, out of reach of
 * {@code @RestControllerAdvice}.
 */
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ProblemDetailFactory problems, ObjectMapper objectMapper) {
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        ProblemDetail problem = problems.create(
                CommonErrorCode.ACCESS_DENIED,
                "You are not allowed to perform this action",
                request);

        response.setStatus(CommonErrorCode.ACCESS_DENIED.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
