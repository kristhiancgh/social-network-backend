package dev.social.post.posts.repositories;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;

/**
 * Calls {@code sp_create_post} and {@code sp_soft_delete_post}.
 *
 * <p>Both encode a rule that cannot be enforced correctly from Java. The
 * anti-flood check in {@code sp_create_post} and the ownership check in
 * {@code sp_soft_delete_post} are both check-then-act sequences: performed as
 * two statements from the application they leave a window in which a second
 * request slips between the check and the write. Inside a procedure they run
 * under one transaction and, for the delete, one row lock.
 */
@Repository
public class PostProcedureRepository {

    private static final String CALL_CREATE_POST =
            "CALL sp_create_post(?, ?, ?, ?, NULL, NULL)";

    private static final String CALL_SOFT_DELETE_POST =
            "CALL sp_soft_delete_post(?, ?, NULL)";

    private final JdbcTemplate jdbcTemplate;

    public PostProcedureRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Publishes a post with {@code published_at = now()}, as the brief requires,
     * and refuses an identical message from the same author within 30 seconds.
     *
     * @return the generated id and the timestamp the database actually stamped,
     *         so the response never has to guess at it
     */
    public CreatedPost createPost(UUID authorId,
                                  String authorUsername,
                                  String authorAlias,
                                  String message) {

        return jdbcTemplate.queryForObject(
                CALL_CREATE_POST,
                (resultSet, rowNumber) -> new CreatedPost(
                        resultSet.getObject("p_post_id", UUID.class),
                        toInstant(resultSet.getTimestamp("p_published_at"))),
                authorId, authorUsername, authorAlias, message);
    }

    /**
     * Soft-deletes a post after checking ownership under a row lock.
     *
     * @return {@code true} when this call did the deleting, {@code false} when
     *         the post was already gone - which lets the caller stay idempotent
     *         instead of failing a repeated delete
     */
    public boolean softDeletePost(UUID postId, UUID authorId) {
        Boolean deleted = jdbcTemplate.queryForObject(
                CALL_SOFT_DELETE_POST,
                (resultSet, rowNumber) -> resultSet.getObject("p_deleted", Boolean.class),
                postId, authorId);

        return Boolean.TRUE.equals(deleted);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    public record CreatedPost(UUID postId, Instant publishedAt) {
    }
}
