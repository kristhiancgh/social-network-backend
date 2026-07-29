package dev.social.post.integration;

import dev.social.post.posts.dto.CreatePostRequest;
import dev.social.post.posts.services.PostService;
import dev.social.post.realtime.dto.PostCreatedEvent;
import dev.social.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import dev.social.post.realtime.services.PostEventBroadcaster;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * Proves a new publication is announced to other users - the behaviour that was
 * missing when a post only appeared after a manual reload.
 *
 * <p>The interesting assertion is the <em>timing</em>: the broadcast must not
 * leave the server until the transaction commits. Announcing a post that later
 * rolls back would put it on everybody's timeline with nothing to retract it,
 * and it would sit there until each of them reloaded.
 */
@RecordApplicationEvents
class PostBroadcastIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private PostService postService;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoSpyBean
    private SimpMessagingTemplate messagingTemplate;

    private static AuthenticatedUser author() {
        return new AuthenticatedUser(UUID.randomUUID(), "tester", List.of("ROLE_USER"));
    }

    private static String uniqueMessage() {
        return "Broadcast " + UUID.randomUUID();
    }

    @Test
    @DisplayName("publishing emits a PostCreatedEvent carrying the whole post")
    void publishesEvent() {
        AuthenticatedUser user = author();
        String message = uniqueMessage();

        postService.create(user, "tester_alias", new CreatePostRequest(message));

        List<PostCreatedEvent> published =
                events.stream(PostCreatedEvent.class).toList();

        assertThat(published).hasSize(1);
        assertThat(published.getFirst()).satisfies(event -> {
            assertThat(event.postId()).isNotNull();
            assertThat(event.authorId()).isEqualTo(user.userId());
            assertThat(event.authorUsername()).isEqualTo("tester");
            assertThat(event.authorAlias()).isEqualTo("tester_alias");
            assertThat(event.message()).isEqualTo(message);
            assertThat(event.publishedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("the broadcast reaches the topic other users subscribe to")
    void broadcastsToTopic() {
        postService.create(author(), "tester_alias", new CreatePostRequest(uniqueMessage()));

        verify(messagingTemplate).convertAndSend(
                eq(PostEventBroadcaster.TOPIC_NEW_POSTS), any(PostCreatedEvent.class));
    }

    @Test
    @DisplayName("nothing is broadcast when the transaction rolls back")
    void silentOnRollback() {
        String message = uniqueMessage();

        Throwable thrown = catchThrowable(() -> transactionTemplate.execute(status -> {
            postService.create(author(), "tester_alias", new CreatePostRequest(message));
            throw new IllegalStateException("forced rollback");
        }));

        assertThat(thrown).hasMessage("forced rollback");

        verify(messagingTemplate, never()).convertAndSend(
                eq(PostEventBroadcaster.TOPIC_NEW_POSTS), any(PostCreatedEvent.class));
    }
}
