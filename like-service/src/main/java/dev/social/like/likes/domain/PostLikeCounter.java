package dev.social.like.likes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The pre-aggregated like total for a post - the read model.
 *
 * <p>Counting with {@code SELECT COUNT(*)} per post is O(likes) and runs once
 * per card on every timeline render. This row makes it O(1).
 *
 * <p>It is also the lock target. {@code sp_toggle_post_like} takes
 * {@code FOR UPDATE} on this row before touching anything, which serialises
 * concurrent likes on the <em>same</em> post while leaving likes on different
 * posts fully parallel. Nothing in Java ever writes here; the procedure owns it.
 */
@Entity
@Table(name = "post_like_counters")
public class PostLikeCounter {

    @Id
    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "like_count", nullable = false, insertable = false, updatable = false)
    private long likeCount;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected PostLikeCounter() {
        // required by JPA
    }

    public UUID getPostId() {
        return postId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PostLikeCounter counter && Objects.equals(postId, counter.postId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(postId);
    }

    @Override
    public String toString() {
        return "PostLikeCounter{postId=%s, likeCount=%d}".formatted(postId, likeCount);
    }
}
