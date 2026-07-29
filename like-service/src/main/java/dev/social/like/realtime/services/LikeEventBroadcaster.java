package dev.social.like.realtime.services;

import dev.social.like.realtime.dto.LikeChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes like changes to every connected browser.
 *
 * <h2>Why AFTER_COMMIT and not a direct call</h2>
 * {@code LikeService} publishes a Spring application event instead of sending
 * the STOMP message itself, and this listener only fires once the transaction
 * has actually committed.
 *
 * <p>Broadcasting inline would announce a like that might still roll back -
 * every other browser would increment a counter for something that never
 * happened, and nothing would ever correct them, because a rollback produces no
 * message. Waiting for the commit means the network only ever hears about facts.
 *
 * <h2>Two destinations</h2>
 * <ul>
 *   <li>{@code /topic/likes} - the firehose. The timeline subscribes here once
 *       and filters, rather than subscribing and unsubscribing per post as the
 *       user scrolls.</li>
 *   <li>{@code /topic/posts/{postId}/likes} - a single post. Used by a detail
 *       view, where receiving every like in the network would be wasteful.</li>
 * </ul>
 *
 * <p>The in-memory broker behind these topics is per-instance. With several
 * like-service replicas a client would only hear about likes that happened to
 * land on its own instance; the fix is a broker relay (RabbitMQ or Redis), and
 * it is a configuration change in {@code WebSocketConfig} - no code here or in
 * the frontend would move.
 */
@Component
public class LikeEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(LikeEventBroadcaster.class);

    /** Every like in the network. */
    public static final String TOPIC_ALL_LIKES = "/topic/likes";

    /** Likes on one post: {@code /topic/posts/{postId}/likes}. */
    public static final String TOPIC_POST_LIKES_PATTERN = "/topic/posts/%s/likes";

    private final SimpMessagingTemplate messagingTemplate;

    public LikeEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(LikeChangedEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_ALL_LIKES, event);
            messagingTemplate.convertAndSend(
                    TOPIC_POST_LIKES_PATTERN.formatted(event.postId()), event);

            log.debug("Broadcast like change for post {} (count={}, by={})",
                    event.postId(), event.likeCount(), event.actorUsername());

        } catch (RuntimeException exception) {
            // The like is already committed and the HTTP response has been sent.
            // A broker failure must not turn a successful action into an error;
            // the client's own optimistic update already shows the right value,
            // and the next page load reconciles everyone else.
            log.error("Failed to broadcast like change for post {}", event.postId(), exception);
        }
    }
}
