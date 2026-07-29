package dev.social.post.posts.services;

import dev.social.post.posts.domain.Post;
import dev.social.post.posts.dto.CreatePostRequest;
import dev.social.post.posts.dto.PostResponse;
import dev.social.post.posts.exceptions.PostErrorCode;
import dev.social.post.posts.mappers.PostMapper;
import dev.social.post.posts.repositories.PostProcedureRepository;
import dev.social.post.posts.repositories.PostRepository;
import dev.social.post.realtime.dto.PostCreatedEvent;
import dev.social.shared.error.ApplicationException;
import dev.social.shared.error.BusinessRuleException;
import dev.social.shared.error.ConflictException;
import dev.social.shared.error.NotFoundException;
import dev.social.shared.error.SqlErrorSupport;
import dev.social.shared.security.AuthenticatedUser;
import dev.social.shared.web.PageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);

    private final PostRepository posts;
    private final PostProcedureRepository procedures;
    private final PostMapper mapper;
    private final ApplicationEventPublisher events;

    public PostService(PostRepository posts,
                       PostProcedureRepository procedures,
                       PostMapper mapper,
                       ApplicationEventPublisher events) {
        this.posts = posts;
        this.procedures = procedures;
        this.mapper = mapper;
        this.events = events;
    }

    /**
     * The timeline.
     *
     * @param excludeAuthorId when set, that author's own posts are left out -
     *                        which is what the Posts screen wants, since the
     *                        brief describes it as "the other users' posts"
     */
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> findTimeline(UUID excludeAuthorId, Pageable pageable) {
        Page<Post> page = excludeAuthorId == null
                ? posts.findTimeline(pageable)
                : posts.findTimelineExcludingAuthor(excludeAuthorId, pageable);

        return PageResponse.from(page, mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PageResponse<PostResponse> findByAuthor(UUID authorId, Pageable pageable) {
        return PageResponse.from(posts.findByAuthor(authorId, pageable), mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public PostResponse findById(UUID postId) {
        return posts.findActiveById(postId)
                .map(mapper::toResponse)
                .orElseThrow(() -> NotFoundException.of(
                        PostErrorCode.POST_NOT_FOUND, "Post", postId));
    }

    /**
     * Publishes a post.
     *
     * <p>The author's username and alias are copied from the token onto the row,
     * which is what lets the timeline render without calling auth-service or
     * profile-service. Since the values come from a signed token rather than
     * from the request body, a caller cannot publish under someone else's name.
     */
    @Transactional
    public PostResponse create(AuthenticatedUser author, String authorAlias, CreatePostRequest request) {
        try {
            PostProcedureRepository.CreatedPost created = procedures.createPost(
                    author.userId(),
                    author.username(),
                    authorAlias,
                    request.message());

            log.info("User {} published post {}", author.username(), created.postId());

            Post saved = posts.findById(created.postId())
                    .orElseThrow(() -> new IllegalStateException(
                            "sp_create_post returned id %s but no row was found"
                                    .formatted(created.postId())));

            events.publishEvent(PostCreatedEvent.of(
                    saved.getId(),
                    saved.getAuthorId(),
                    saved.getAuthorUsername(),
                    saved.getAuthorAlias(),
                    saved.getMessage(),
                    saved.getPublishedAt()));

            return mapper.toResponse(saved);

        } catch (DataAccessException exception) {
            throw translateCreationFailure(exception);
        }
    }

    /**
     * Soft-deletes a post the caller owns.
     *
     * <p>Ownership is verified inside {@code sp_soft_delete_post}, under a row
     * lock, rather than with a read followed by a write here - otherwise two
     * requests could interleave between the check and the update.
     */
    @Transactional
    public void delete(UUID postId, UUID requesterId) {
        try {
            boolean deleted = procedures.softDeletePost(postId, requesterId);
            if (deleted) {
                log.info("Post {} soft-deleted by {}", postId, requesterId);
            } else {
                log.debug("Post {} was already deleted", postId);
            }
        } catch (DataAccessException exception) {
            throw translateDeletionFailure(exception, postId);
        }
    }

    private RuntimeException translateCreationFailure(DataAccessException exception) {
        return SqlErrorSupport.raisedBusinessCode(exception)
                .map(procedureCode -> {
                    PostErrorCode errorCode = PostErrorCode.fromProcedureCode(procedureCode);
                    return (RuntimeException) switch (errorCode) {
                        case EMPTY_MESSAGE -> new BusinessRuleException(errorCode,
                                "The post message cannot be blank");
                        case DUPLICATE_POST -> new ConflictException(errorCode,
                                "You just published this exact message. Wait a moment before repeating it.");
                        default -> new ConflictException(errorCode, "The post could not be created");
                    };
                })
                .orElse(exception);
    }

    private RuntimeException translateDeletionFailure(DataAccessException exception, UUID postId) {
        return SqlErrorSupport.raisedBusinessCode(exception)
                .map(procedureCode -> {
                    PostErrorCode errorCode = PostErrorCode.fromProcedureCode(procedureCode);
                    return (ApplicationException) switch (errorCode) {
                        case POST_NOT_FOUND -> NotFoundException.of(errorCode, "Post", postId);
                        case NOT_POST_OWNER -> new BusinessRuleException(errorCode,
                                "Only the author can delete this post");
                        default -> new ConflictException(errorCode, "The post could not be deleted");
                    };
                })
                .map(RuntimeException.class::cast)
                .orElse(exception);
    }
}
