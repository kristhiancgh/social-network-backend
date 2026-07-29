package dev.social.like.likes.repositories;

import dev.social.like.likes.domain.PostLikeCounter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostLikeCounterRepository extends JpaRepository<PostLikeCounter, UUID> {

    @Query("""
            SELECT c
              FROM PostLikeCounter c
             WHERE c.postId IN :postIds
            """)
    List<PostLikeCounter> findAllByPostIds(@Param("postIds") List<UUID> postIds);
}
