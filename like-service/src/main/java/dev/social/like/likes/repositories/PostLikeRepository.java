package dev.social.like.likes.repositories;

import dev.social.like.likes.domain.PostLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostLikeRepository extends JpaRepository<PostLike, UUID> {

    @Query("""
            SELECT l.postId
              FROM PostLike l
             WHERE l.userId = :userId
               AND l.postId IN :postIds
            """)
    List<UUID> findLikedPostIds(@Param("userId") UUID userId,
                                @Param("postIds") List<UUID> postIds);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @Query("""
            SELECT l
              FROM PostLike l
             WHERE l.postId = :postId
             ORDER BY l.createdAt DESC
            """)
    List<PostLike> findByPostId(@Param("postId") UUID postId);

    long countByPostId(UUID postId);
}
