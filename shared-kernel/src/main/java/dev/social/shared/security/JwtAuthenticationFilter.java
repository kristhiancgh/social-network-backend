package dev.social.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.social.shared.error.CommonErrorCode;
import dev.social.shared.error.ErrorCode;
import dev.social.shared.error.ProblemDetailFactory;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Turns a {@code Bearer} token into an authenticated {@code SecurityContext}.
 *
 * <p>Two design points worth stating:
 *
 * <p><b>A missing token is not an error here.</b> The filter simply does
 * nothing and lets the chain continue. Whether anonymous access is acceptable
 * is a routing decision that belongs to each service's {@code SecurityConfig};
 * if the endpoint did require authentication,
 * {@link ProblemDetailAuthenticationEntryPoint} produces the 401 further down.
 * Rejecting here would make every public endpoint - Swagger, actuator, login
 * itself - unreachable.
 *
 * <p><b>A present but broken token is an error.</b> That is an active attempt
 * to authenticate, and letting it fall through would surface as a confusing
 * 403-on-a-public-page later. It is answered immediately, and expired is kept
 * distinct from invalid so the frontend can refresh silently instead of
 * bouncing the user to the login screen.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;
    private final ProblemDetailFactory problems;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider,
                                   ProblemDetailFactory problems,
                                   ObjectMapper objectMapper) {
        this.tokenProvider = tokenProvider;
        this.problems = problems;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String token = extractBearerToken(request);

        if (token == null) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            AuthenticatedUser user = tokenProvider.parse(token);

            List<SimpleGrantedAuthority> authorities = user.roles()
                    .stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException exception) {
            log.debug("Expired token on {} {}", request.getMethod(), request.getRequestURI());
            writeProblem(request, response, CommonErrorCode.EXPIRED_TOKEN,
                    "The access token has expired");

        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("Rejected token on {} {}: {}",
                    request.getMethod(), request.getRequestURI(), exception.getMessage());
            writeProblem(request, response, CommonErrorCode.INVALID_TOKEN,
                    "The access token is not valid");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * The STOMP handshake carries no Authorization header from a browser, so
     * {@code /ws} authenticates on the STOMP CONNECT frame instead (see
     * like-service's {@code StompAuthenticationInterceptor}). Skipping the
     * filter here keeps the handshake from being rejected before it starts.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/ws");
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    private void writeProblem(HttpServletRequest request,
                              HttpServletResponse response,
                              ErrorCode errorCode,
                              String detail) throws IOException {

        ProblemDetail problem = problems.create(errorCode, detail, request);

        response.setStatus(errorCode.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
