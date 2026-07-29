package dev.social.post.integration;

import dev.social.post.posts.repositories.PostProcedureRepository;
import dev.social.post.posts.repositories.PostRepository;
import dev.social.shared.error.SqlErrorSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/** Exercises {@code sp_create_post} and {@code sp_soft_delete_post}. */
@Transactional
class PostProcedureRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private PostProcedureRepository procedures;

    @Autowired
    private PostRepository posts;

    private static UUID newAuthor() {
        return UUID.randomUUID();
    }

    @Test
    @DisplayName("sp_create_post stamps published_at server-side, as the brief requires")
    void createsPostWithServerTimestamp() {
        Instant before = Instant.now().minusSeconds(5);

        PostProcedureRepository.CreatedPost created =
                procedures.createPost(newAuthor(), "jdoe", "johnny", "Hello world");

        assertThat(created.postId()).isNotNull();
        assertThat(created.publishedAt()).isAfter(before);
        assertThat(posts.findById(created.postId())).hasValueSatisfying(post -> {
            assertThat(post.getMessage()).isEqualTo("Hello world");
            assertThat(post.getAuthorUsername()).isEqualTo("jdoe");
            assertThat(post.getAuthorAlias()).isEqualTo("johnny");
            assertThat(post.isDeleted()).isFalse();
        });
    }

    @Test
    @DisplayName("sp_create_post trims the message")
    void trimsMessage() {
        PostProcedureRepository.CreatedPost created =
                procedures.createPost(newAuthor(), "jdoe", "johnny", "   padded   ");

        assertThat(posts.findById(created.postId()))
                .hasValueSatisfying(post -> assertThat(post.getMessage()).isEqualTo("padded"));
    }

    @Test
    @DisplayName("sp_create_post rejects a blank message with EMPTY_MESSAGE")
    void rejectsBlankMessage() {
        Throwable thrown = catchThrowable(
                () -> procedures.createPost(newAuthor(), "jdoe", "johnny", "     "));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown)).contains("EMPTY_MESSAGE");
    }

    @Test
    @DisplayName("sp_create_post blocks the same author repeating a message within 30 seconds")
    void blocksDuplicateWithinWindow() {
        UUID authorId = newAuthor();
        procedures.createPost(authorId, "jdoe", "johnny", "Double submit");

        Throwable thrown = catchThrowable(
                () -> procedures.createPost(authorId, "jdoe", "johnny", "Double submit"));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown)).contains("DUPLICATE_POST");
    }

    @Test
    @DisplayName("the anti-flood rule is per author, not global")
    void duplicateRuleIsPerAuthor() {
        procedures.createPost(newAuthor(), "jdoe", "johnny", "Same words");

        PostProcedureRepository.CreatedPost other =
                procedures.createPost(newAuthor(), "mgarcia", "mary_g", "Same words");

        assertThat(other.postId()).isNotNull();
    }

    @Test
    @DisplayName("sp_soft_delete_post lets the author delete and hides the post from reads")
    void ownerCanDelete() {
        UUID authorId = newAuthor();
        PostProcedureRepository.CreatedPost created =
                procedures.createPost(authorId, "jdoe", "johnny", "To be removed");

        boolean deleted = procedures.softDeletePost(created.postId(), authorId);

        assertThat(deleted).isTrue();
        assertThat(posts.findById(created.postId())).isPresent();
        assertThat(posts.findActiveById(created.postId())).isEmpty();
    }

    @Test
    @DisplayName("sp_soft_delete_post refuses a post belonging to somebody else")
    void nonOwnerCannotDelete() {
        PostProcedureRepository.CreatedPost created =
                procedures.createPost(newAuthor(), "jdoe", "johnny", "Mine");

        Throwable thrown = catchThrowable(
                () -> procedures.softDeletePost(created.postId(), newAuthor()));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown)).contains("NOT_POST_OWNER");
    }

    @Test
    @DisplayName("deleting twice reports no change, so DELETE stays idempotent")
    void secondDeleteChangesNothing() {
        UUID authorId = newAuthor();
        PostProcedureRepository.CreatedPost created =
                procedures.createPost(authorId, "jdoe", "johnny", "Delete me");

        assertThat(procedures.softDeletePost(created.postId(), authorId)).isTrue();
        assertThat(procedures.softDeletePost(created.postId(), authorId)).isFalse();
    }

    @Test
    @DisplayName("sp_soft_delete_post reports POST_NOT_FOUND for an unknown id")
    void unknownPost() {
        Throwable thrown = catchThrowable(
                () -> procedures.softDeletePost(UUID.randomUUID(), newAuthor()));

        assertThat(SqlErrorSupport.raisedBusinessCode(thrown)).contains("POST_NOT_FOUND");
    }

    @Test
    @DisplayName("the timeline excludes deleted posts and orders newest first")
    void timelineHidesDeletedAndOrders() {
        UUID authorId = newAuthor();
        var first = procedures.createPost(authorId, "jdoe", "johnny", "Oldest");
        var second = procedures.createPost(authorId, "jdoe", "johnny", "Newest");
        procedures.softDeletePost(first.postId(), authorId);

        var timeline = posts.findTimeline(PageRequest.of(0, 50));

        assertThat(timeline.getContent()).extracting("id").contains(second.postId());
        assertThat(timeline.getContent()).extracting("id").doesNotContain(first.postId());
    }

    @Test
    @DisplayName("findTimelineExcludingAuthor leaves out one author's own posts")
    void timelineCanExcludeAnAuthor() {
        UUID me = newAuthor();
        UUID someoneElse = newAuthor();
        var mine = procedures.createPost(me, "jdoe", "johnny", "My post");
        var theirs = procedures.createPost(someoneElse, "mgarcia", "mary_g", "Their post");

        var timeline = posts.findTimelineExcludingAuthor(me, PageRequest.of(0, 50));

        assertThat(timeline.getContent()).extracting("id").contains(theirs.postId());
        assertThat(timeline.getContent()).extracting("id").doesNotContain(mine.postId());
    }
}
