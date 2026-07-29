package dev.social.like.integration;

import dev.social.like.likes.repositories.LikeProcedureRepository;
import dev.social.like.likes.repositories.PostLikeCounterRepository;
import dev.social.like.likes.repositories.PostLikeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@code sp_toggle_post_like}, the procedure the whole real-time
 * feature rests on.
 *
 * <p>The concurrency test at the end is the one that matters. Everything else
 * here would pass just as well against an implementation written as four
 * separate Java statements; only that test distinguishes a correct
 * implementation from one that quietly loses likes under load.
 */
class LikeProcedureRepositoryIT extends AbstractPostgresIntegrationTest {

    @Autowired
    private LikeProcedureRepository procedures;

    @Autowired
    private PostLikeRepository likes;

    @Autowired
    private PostLikeCounterRepository counters;

    private static UUID newPost() {
        return UUID.randomUUID();
    }

    private static UUID newUser() {
        return UUID.randomUUID();
    }

    @Test
    @Transactional
    @DisplayName("first toggle likes the post and creates its counter row")
    void firstToggleLikes() {
        UUID postId = newPost();

        LikeProcedureRepository.ToggleResult result =
                procedures.toggleLike(postId, newUser(), "alice");

        assertThat(result.liked()).isTrue();
        assertThat(result.likeCount()).isEqualTo(1);
        assertThat(counters.findById(postId)).isPresent();
    }

    @Test
    @Transactional
    @DisplayName("toggling twice returns the post to its original state")
    void secondToggleUnlikes() {
        UUID postId = newPost();
        UUID userId = newUser();

        procedures.toggleLike(postId, userId, "alice");
        LikeProcedureRepository.ToggleResult second = procedures.toggleLike(postId, userId, "alice");

        assertThat(second.liked()).isFalse();
        assertThat(second.likeCount()).isZero();
        assertThat(likes.existsByPostIdAndUserId(postId, userId)).isFalse();
    }

    @Test
    @Transactional
    @DisplayName("a third toggle likes it again - the row is recreated, not resurrected")
    void thirdToggleLikesAgain() {
        UUID postId = newPost();
        UUID userId = newUser();

        procedures.toggleLike(postId, userId, "alice");
        procedures.toggleLike(postId, userId, "alice");
        LikeProcedureRepository.ToggleResult third = procedures.toggleLike(postId, userId, "alice");

        assertThat(third.liked()).isTrue();
        assertThat(third.likeCount()).isEqualTo(1);
    }

    @Test
    @Transactional
    @DisplayName("different users on the same post each add one")
    void differentUsersAccumulate() {
        UUID postId = newPost();

        assertThat(procedures.toggleLike(postId, newUser(), "alice").likeCount()).isEqualTo(1);
        assertThat(procedures.toggleLike(postId, newUser(), "bob").likeCount()).isEqualTo(2);
        assertThat(procedures.toggleLike(postId, newUser(), "carol").likeCount()).isEqualTo(3);
    }

    @Test
    @Transactional
    @DisplayName("one user's likes on different posts do not interfere")
    void postsAreIndependent() {
        UUID userId = newUser();
        UUID firstPost = newPost();
        UUID secondPost = newPost();

        procedures.toggleLike(firstPost, userId, "alice");
        procedures.toggleLike(secondPost, userId, "alice");
        procedures.toggleLike(firstPost, userId, "alice");

        assertThat(counters.findById(firstPost)).hasValueSatisfying(
                counter -> assertThat(counter.getLikeCount()).isZero());
        assertThat(counters.findById(secondPost)).hasValueSatisfying(
                counter -> assertThat(counter.getLikeCount()).isEqualTo(1));
    }

    @Test
    @Transactional
    @DisplayName("sp_rebuild_like_counters recomputes totals from the like rows")
    void rebuildsCounters() {
        UUID postId = newPost();
        procedures.toggleLike(postId, newUser(), "alice");
        procedures.toggleLike(postId, newUser(), "bob");

        long counterRows = procedures.rebuildCounters();

        assertThat(counterRows).isPositive();
        assertThat(counters.findById(postId)).hasValueSatisfying(
                counter -> assertThat(counter.getLikeCount())
                        .isEqualTo(likes.countByPostId(postId)));
    }

    /**
     * The test the procedure exists for.
     *
     * <p>Twenty threads like the same post at the same instant. Implemented in
     * Java as read-modify-write, most of those threads would read the same total
     * and write it back, and the counter would land somewhere below twenty. The
     * {@code FOR UPDATE} on the counter row serialises them.
     *
     * <p>Note this test is deliberately NOT {@code @Transactional}: each thread
     * needs its own connection and its own transaction, which is the entire
     * point. A test-managed transaction would put them all on one connection and
     * prove nothing.
     */
    @Test
    @DisplayName("twenty concurrent likes on one post lose none of them")
    void concurrentLikesAreNotLost() throws Exception {
        UUID postId = newPost();
        int threads = 20;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startTogether = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(threads);
        AtomicInteger failures = new AtomicInteger();

        List<UUID> userIds = java.util.stream.Stream
                .generate(UUID::randomUUID)
                .limit(threads)
                .toList();

        for (int i = 0; i < threads; i++) {
            UUID userId = userIds.get(i);
            String username = "user" + i;
            pool.submit(() -> {
                try {
                    startTogether.await();
                    procedures.toggleLike(postId, userId, username);
                } catch (Exception exception) {
                    failures.incrementAndGet();
                } finally {
                    finished.countDown();
                }
            });
        }

        startTogether.countDown();
        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        assertThat(failures.get()).isZero();
        assertThat(likes.countByPostId(postId)).isEqualTo(threads);
        assertThat(counters.findById(postId)).hasValueSatisfying(
                counter -> assertThat(counter.getLikeCount()).isEqualTo(threads));
    }

    /**
     * The same post, the same user, twenty times at once.
     *
     * <p>Each call flips the state, so the final total is not predictable - but
     * the invariant is: the counter must always agree with the number of rows,
     * and it must never go negative. A broken implementation produces a counter
     * of 3 with 1 row behind it.
     */
    @Test
    @DisplayName("concurrent toggles by one user leave the counter consistent with the rows")
    void concurrentTogglesStayConsistent() throws Exception {
        UUID postId = newPost();
        UUID userId = newUser();
        int attempts = 20;

        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch finished = new CountDownLatch(attempts);

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    procedures.toggleLike(postId, userId, "flipper");
                } catch (Exception ignored) {
                } finally {
                    finished.countDown();
                }
            });
        }

        assertThat(finished.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdown();

        long rows = likes.countByPostId(postId);
        assertThat(rows).isBetween(0L, 1L);
        assertThat(counters.findById(postId)).hasValueSatisfying(
                counter -> assertThat(counter.getLikeCount()).isEqualTo(rows));
    }
}
