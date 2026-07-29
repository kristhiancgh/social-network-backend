package dev.social.shared.realtime;

import dev.social.shared.security.AuthenticatedUser;
import dev.social.shared.security.JwtTokenProvider;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/**
 * Authenticates the WebSocket connection on the STOMP {@code CONNECT} frame.
 *
 * <h2>Why not the HTTP handshake</h2>
 * The browser's {@code WebSocket} constructor takes a URL and nothing else -
 * there is no way to set an {@code Authorization} header on the handshake. The
 * usual workarounds are both bad: putting the token in the query string writes
 * it to every access log, and relying on a cookie reintroduces the CSRF surface
 * this application avoids by being stateless.
 *
 * <p>STOMP solves it cleanly. The handshake itself is left open (see
 * {@code SecurityConfig}, which permits {@code /ws/**}); the very first STOMP
 * frame carries the token as a native header, it is verified here, and a
 * connection that fails verification is refused before it can subscribe to
 * anything.
 *
 * <p>Only {@code CONNECT} is checked. Once established, the authenticated
 * principal is attached to the session and Spring carries it on every
 * subsequent frame.
 *
 * <p>Lives in shared-kernel because two services now expose a WebSocket -
 * like-service for like changes and post-service for new publications - and a
 * second copy of authentication logic is a second place for it to go wrong. It
 * is deliberately NOT a {@code @Component}: each service instantiates it in its
 * own {@code WebSocketConfig}, so a service without WebSocket support never
 * gets a bean it cannot use.
 */
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(StompAuthenticationInterceptor.class);

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    public StompAuthenticationInterceptor(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        String token = extractToken(accessor);
        if (token == null) {
            log.warn("STOMP CONNECT without a token - refused");
            throw refuse("Missing Authorization header on the STOMP CONNECT frame");
        }

        try {
            AuthenticatedUser user = tokenProvider.parse(token);

            List<SimpleGrantedAuthority> authorities = user.roles()
                    .stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();

            var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
            accessor.setUser(authentication);

            log.debug("WebSocket authenticated for {}", user.username());
            return message;

        } catch (JwtException | IllegalArgumentException exception) {
            log.warn("STOMP CONNECT with an invalid token - refused: {}", exception.getMessage());
            throw refuse("The access token is not valid");
        }
    }

    /**
     * Refuses the connection loudly.
     *
     * <p>The obvious alternative - {@code return null} - drops the frame
     * silently. The socket then stays open while the client waits forever for a
     * CONNECTED that will never arrive: no error, no close, just a connection
     * that quietly does nothing. An integration test caught exactly that.
     *
     * <p>Throwing instead makes Spring send a STOMP ERROR frame and close the
     * session, so the client's {@code onStompError} fires and it can react.
     * The reason is deliberately vague - a rejected token gets no free hints.
     */
    private MessageDeliveryException refuse(String reason) {
        return new MessageDeliveryException(reason);
    }

    private String extractToken(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }
}
