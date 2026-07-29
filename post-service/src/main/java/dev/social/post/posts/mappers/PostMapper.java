package dev.social.post.posts.mappers;

import dev.social.post.posts.domain.Post;
import dev.social.post.posts.dto.PostResponse;
import org.springframework.stereotype.Component;

/** Entity to DTO. */
@Component
public class PostMapper {

    public PostResponse toResponse(Post post) {
        return new PostResponse(
                post.getId(),
                post.getAuthorId(),
                post.getAuthorUsername(),
                post.getAuthorAlias(),
                post.getMessage(),
                post.getPublishedAt());
    }
}
