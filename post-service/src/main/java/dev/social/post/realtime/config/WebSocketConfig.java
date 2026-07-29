package dev.social.post.realtime.config;

import dev.social.shared.realtime.StompAuthenticationInterceptor;
import dev.social.shared.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over WebSocket for new publications.
 *
 * <pre>
 *   Browser --- ws://host/ws-posts ---> post-service
 *      subscribe /topic/posts
 *      ... somebody POSTs /api/posts ...
 *      &lt;--- PostCreatedEvent to every subscriber
 * </pre>
 *
 * <h2>Why a second endpoint instead of reusing like-service's</h2>
 * The obvious shortcut is to have post-service call like-service and ask it to
 * broadcast. That would make publishing a post depend on a service that has
 * nothing to do with posts - if like-service is down, publishing breaks, and
 * the two are coupled exactly where the architecture says they should not be.
 *
 * <p>So each service that produces real-time events owns its own endpoint. The
 * cost is a second WebSocket connection in the browser, which the frontend's
 * {@code RealtimeService} hides behind one API. The moment a broker relay
 * (RabbitMQ, Redis) is introduced - which multi-replica deployment needs
 * anyway - both endpoints can fan out through it without either service
 * learning about the other.
 *
 * <p>The path is {@code /ws-posts} rather than {@code /ws} because the gateway
 * has to be able to route them to different services.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String ENDPOINT = "/ws-posts";

    private final StompAuthenticationInterceptor authenticationInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(JwtTokenProvider tokenProvider,
                           @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        this.authenticationInterceptor = new StompAuthenticationInterceptor(tokenProvider);
        this.allowedOrigins = allowedOrigins;
    }

    @Bean
    public StompAuthenticationInterceptor stompAuthenticationInterceptor() {
        return authenticationInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT)
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}
