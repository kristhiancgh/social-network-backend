package dev.social.like.likes.services;

import dev.social.like.likes.domain.PostLike;
import dev.social.like.likes.domain.PostLikeCounter;
import dev.social.like.likes.dto.LikeToggleResponse;
import dev.social.like.likes.dto.PostLikeSummary;
import dev.social.like.likes.repositories.LikeProcedureRepository;
import dev.social.like.likes.repositories.PostLikeCounterRepository;
import dev.social.like.likes.repositories.PostLikeRepository;
import dev.social.like.realtime.dto.LikeChangedEvent;
import dev.social.shared.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeService.class);

    private final LikeProcedureRepository procedures;
    private final PostLikeRepository likes;
    private final PostLikeCounterRepository counters;
    private final ApplicationEventPublisher events;

    public LikeService(LikeProcedureRepository procedures,
                       PostLikeRepository likes,
                       PostLikeCounterRepository counters,
                       ApplicationEventPublisher events) {
        this.procedures = procedures;
        this.likes = likes;
        this.counters = counters;
        this.events = events;
    }

    /**
     * Likes or unlikes a post, atomically.
     *
     * <p>The whole cycle happens inside {@code sp_toggle_post_like}, so nothing
     * here has to reason about concurrency. What this method adds is the
     * broadcast - published as a Spring event rather than sent directly, so
     * {@code LikeEventBroadcaster} can wait for the commit. Sending inline would
     * tell every browser about a like that could still roll back, and a rollback
     * emits no correction.
     */
    @Transactional
    public LikeToggleResponse toggle(UUID postId, AuthenticatedUser user) {
        LikeProcedureRepository.ToggleResult result =
                procedures.toggleLike(postId, user.userId(), user.username());

        log.info("{} post {} for user {} (total now {})",
                result.liked() ? "Liked" : "Unliked", postId, user.username(), result.likeCount());

        events.publishEvent(LikeChangedEvent.of(
                postId, result.likeCount(), result.liked(), user.userId(), user.username()));

        return new LikeToggleResponse(postId, result.liked(), result.likeCount());
    }

    /**
     * Like state for a batch of posts, from one user's point of view.
     *
     * <p>Two queries for any number of posts - one for the totals, one for
     * "which of these did I like" - instead of two per post. This is what the
     * timeline calls once after loading a page of posts.
     *
     * <p>A post nobody has liked has no counter row, so it is reported as zero
     * rather than omitted: the client asked about it and deserves an answer for
     * every id it sent.
     */
    @Transactional(readOnly = true)
    public List<PostLikeSummary> summarise(List<UUID> postIds, UUID userId) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> totals = counters.findAllByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        PostLikeCounter::getPostId, PostLikeCounter::getLikeCount));

        Set<UUID> likedByUser = new HashSet<>(likes.findLikedPostIds(userId, postIds));

        return postIds.stream()
                .distinct()
                .map(postId -> new PostLikeSummary(
                        postId,
                        totals.getOrDefault(postId, 0L),
                        likedByUser.contains(postId)))
                .toList();
    }

    @Transactional(readOnly = true)
    public PostLikeSummary summariseOne(UUID postId, UUID userId) {
        return summarise(List.of(postId), userId).getFirst();
    }

    @Transactional(readOnly = true)
    public List<String> findLikers(UUID postId) {
        return likes.findByPostId(postId).stream()
                .map(PostLike::getUsername)
                .toList();
    }

    /**
     * Rebuilds every counter from the like rows.
     *
     * <p>Exposed for maintenance and used by the integration test that proves
     * the read model never drifts from the write model.
     *
     * @return number of counter rows after the rebuild
     */
    @Transactional
    public long rebuildCounters() {
        long rows = procedures.rebuildCounters();
        log.info("Rebuilt {} like counter row(s)", rows);
        return rows;
    }
}
