package dev.social.post.posts.repositories;

import dev.social.post.posts.domain.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {


    @Query("""
            SELECT p
              FROM Post p
             WHERE p.deleted = FALSE
             ORDER BY p.publishedAt DESC
            """)
    Page<Post> findTimeline(Pageable pageable);

    @Query("""
            SELECT p
              FROM Post p
             WHERE p.deleted = FALSE
               AND p.authorId <> :excludedAuthorId
             ORDER BY p.publishedAt DESC
            """)
    Page<Post> findTimelineExcludingAuthor(@Param("excludedAuthorId") UUID excludedAuthorId,
                                           Pageable pageable);

    @Query("""
            SELECT p
              FROM Post p
             WHERE p.deleted = FALSE
               AND p.authorId = :authorId
             ORDER BY p.publishedAt DESC
            """)
    Page<Post> findByAuthor(@Param("authorId") UUID authorId, Pageable pageable);

    /** Soft-deleted posts are invisible to every read path, this one included. */
    @Query("SELECT p FROM Post p WHERE p.id = :id AND p.deleted = FALSE")
    Optional<Post> findActiveById(@Param("id") UUID id);
}
