package dev.social.like.likes.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Calls {@code sp_toggle_post_like} - the heart of the real-time feature.
 *
 * <p>One {@code CALL} performs the entire like/unlike cycle and returns exactly
 * the two values the WebSocket broadcast needs: whether this user now likes the
 * post, and the fresh total.
 *
 * <p><b>Why this is not four Java statements.</b> Done in the application it
 * would read: does a like exist, delete or insert, recount, save. Two users
 * liking the same post at the same instant would both read the old total and
 * both write it back, and one like would silently vanish. The procedure takes
 * {@code FOR UPDATE} on the counter row first, so the pair is serialised on
 * that single row - and because the lock lives in the database, the guarantee
 * holds across as many like-service replicas as you care to run. No distributed
 * lock, nothing to get wrong.
 */
@Repository
public class LikeProcedureRepository {

    private static final String CALL_TOGGLE_POST_LIKE =
            "CALL sp_toggle_post_like(?, ?, ?, NULL, NULL)";

    private static final String CALL_REBUILD_LIKE_COUNTERS =
            "CALL sp_rebuild_like_counters(NULL)";

    private final JdbcTemplate jdbcTemplate;

    public LikeProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Likes the post if the user has not liked it, unlikes it if they have.
     *
     * @return the user's new state and the post's new total
     */
    public ToggleResult toggleLike(UUID postId, UUID userId, String username) {
        return jdbcTemplate.queryForObject(
                CALL_TOGGLE_POST_LIKE,
                (resultSet, rowNumber) -> new ToggleResult(
                        resultSet.getBoolean("p_liked"),
                        resultSet.getLong("p_like_count")),
                postId, userId, username);
    }

    /**
     * Rebuilds every counter from the underlying like rows.
     *
     * <p>Maintenance only - after a bulk import or a seed run. Also the
     * assertion an integration test uses to prove the counters never drift from
     * the write model.
     *
     * @return number of counter rows after the rebuild
     */
    public long rebuildCounters() {
        Long rows = jdbcTemplate.queryForObject(
                CALL_REBUILD_LIKE_COUNTERS,
                (resultSet, rowNumber) -> resultSet.getLong("p_rows_affected"));
        return rows == null ? 0L : rows;
    }

    /**
     * @param liked     whether the calling user now likes the post
     * @param likeCount the post's total after the toggle
     */
    public record ToggleResult(boolean liked, long likeCount) {
    }
}
