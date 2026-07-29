package dev.social.post.realtime.services;

import dev.social.post.realtime.dto.PostCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Pushes new publications to every connected browser.
 *
 * <p>Same shape, and the same reasoning, as like-service's
 * {@code LikeEventBroadcaster}: {@code PostService} publishes a Spring
 * application event and this listener only fires once the transaction has
 * committed.
 *
 * <p>Announcing inline would put a post on everyone's timeline that might still
 * roll back - and a rollback emits no retraction, so those timelines would show
 * a publication that does not exist until the page is reloaded. Waiting for the
 * commit means the network only ever hears about facts.
 */
@Component
public class PostEventBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(PostEventBroadcaster.class);

    public static final String TOPIC_NEW_POSTS = "/topic/posts";

    private final SimpMessagingTemplate messagingTemplate;

    public PostEventBroadcaster(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void broadcast(PostCreatedEvent event) {
        try {
            messagingTemplate.convertAndSend(TOPIC_NEW_POSTS, event);
            log.debug("Broadcast new post {} by {}", event.postId(), event.authorUsername());

        } catch (RuntimeException exception) {
            log.error("Failed to broadcast new post {}", event.postId(), exception);
        }
    }
}
