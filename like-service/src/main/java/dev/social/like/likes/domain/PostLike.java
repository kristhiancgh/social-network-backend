package dev.social.like.likes.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One like, by one user, on one post - the write model.
 *
 * <p>Unliking deletes the row rather than flagging it. There is no history to
 * preserve here and a {@code (post_id, user_id)} unique index is what makes
 * "at most one like per user per post" true under concurrency; a soft-delete
 * column would break that guarantee and need a partial index to restore it.
 */
@Entity
@Table(name = "post_likes")
public class PostLike {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "post_id", nullable = false, updatable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "username", nullable = false, updatable = false, length = 50)
    private String username;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PostLike() {
        // required by JPA
    }

    public PostLike(UUID id, UUID postId, UUID userId, String username) {
        this.id = Objects.requireNonNull(id, "id");
        this.postId = Objects.requireNonNull(postId, "postId");
        this.userId = Objects.requireNonNull(userId, "userId");
        this.username = Objects.requireNonNull(username, "username").trim();
    }

    public static PostLike create(UUID postId, UUID userId, String username) {
        return new PostLike(UUID.randomUUID(), postId, userId, username);
    }

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof PostLike like && Objects.equals(id, like.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "PostLike{postId=%s, username='%s'}".formatted(postId, username);
    }
}
