package dev.social.post.posts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A publication: a message, its author and a publication date - exactly the
 * three things the brief asks for.
 *
 * <p><b>The author's name is stored on the post.</b> {@code authorUsername} and
 * {@code authorAlias} are copies of data owned by auth-service and
 * profile-service, written once from the verified JWT at creation time.
 *
 * <p>That is a deliberate trade. Listing a timeline is the most frequent read
 * in the whole application; without the copy, rendering twenty posts means
 * twenty cross-service lookups (or one batch call and a join in memory) on
 * every page load, and post-service stops being able to answer at all when
 * profile-service is down. The cost is that a user who later changes their
 * alias keeps the old one on existing posts - acceptable for an authorship
 * label, and fixable with a background backfill if it ever matters.
 */
@Entity
@Table(name = "posts")
public class Post {

    public static final int MAX_MESSAGE_LENGTH = 500;

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "author_id", nullable = false, updatable = false)
    private UUID authorId;

    @Column(name = "author_username", nullable = false, updatable = false, length = 50)
    private String authorUsername;

    @Column(name = "author_alias", updatable = false, length = 50)
    private String authorAlias;

    @Column(name = "message", nullable = false, length = MAX_MESSAGE_LENGTH)
    private String message;

    @Column(name = "published_at", nullable = false, updatable = false)
    private Instant publishedAt;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;

    protected Post() {
        // required by JPA
    }

    public Post(UUID id,
                UUID authorId,
                String authorUsername,
                String authorAlias,
                String message,
                Instant publishedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.authorId = Objects.requireNonNull(authorId, "authorId");
        this.authorUsername = Objects.requireNonNull(authorUsername, "authorUsername");
        this.authorAlias = authorAlias;
        this.message = Objects.requireNonNull(message, "message").trim();
        this.publishedAt = Objects.requireNonNull(publishedAt, "publishedAt");
        this.deleted = false;
    }

    public boolean isAuthoredBy(UUID userId) {
        return authorId.equals(userId);
    }

    public void markDeleted() {
        this.deleted = true;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorUsername() {
        return authorUsername;
    }

    public String getAuthorAlias() {
        return authorAlias;
    }

    public String getMessage() {
        return message;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Post post && Objects.equals(id, post.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Post{id=%s, author='%s', publishedAt=%s}".formatted(id, authorUsername, publishedAt);
    }
}
