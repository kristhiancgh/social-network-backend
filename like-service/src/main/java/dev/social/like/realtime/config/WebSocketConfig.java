package dev.social.like.realtime.config;

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
 * STOMP over WebSocket, for the real-time like counter.
 *
 * <pre>
 *   Browser --- ws://host/ws ---> like-service
 *      subscribe /topic/likes
 *      ... POST /api/likes ...
 *      &lt;--- LikeChangedEvent to every subscriber
 * </pre>
 *
 * <p>The simple in-memory broker is enough for a single instance and needs no
 * extra container. Scaling out means swapping
 * {@code enableSimpleBroker} for {@code enableStompBrokerRelay} pointed at
 * RabbitMQ - the destinations, the frontend and every other class stay as they
 * are.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthenticationInterceptor authenticationInterceptor;
    private final List<String> allowedOrigins;

    public WebSocketConfig(JwtTokenProvider tokenProvider,
                           @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        // Built here rather than injected: the interceptor lives in
        // shared-kernel and is not a bean, so services that never open a
        // WebSocket do not get one.
        this.authenticationInterceptor = new StompAuthenticationInterceptor(tokenProvider);
        this.allowedOrigins = allowedOrigins;
    }

    /** Exposed so tests can reach the same instance the channel uses. */
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
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.toArray(String[]::new));
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authenticationInterceptor);
    }
}
