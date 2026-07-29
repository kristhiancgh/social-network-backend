package dev.social.shared.web;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A page of results, in a shape that is safe to publish.
 *
 * <p>Spring's own {@code Page} is not. Serialising a {@code PageImpl} produces
 * a large, unstable JSON structure - {@code pageable}, {@code sort},
 * {@code empty}, nested objects - that is an implementation detail of Spring
 * Data, changes between versions, and Spring Boot itself now warns about
 * returning directly. Clients would be coupled to that shape forever.
 *
 * <p>This exposes only the six numbers a client actually needs to render
 * pagination.
 *
 * @param <T> element type
 */
@Schema(name = "PageResponse", description = "A page of results")
public record PageResponse<T>(

        @Schema(description = "Elements on this page")
        List<T> content,

        @Schema(description = "Zero-based page index", example = "0")
        int page,

        @Schema(description = "Requested page size", example = "20")
        int size,

        @Schema(description = "Total elements across all pages", example = "137")
        long totalElements,

        @Schema(description = "Total number of pages", example = "7")
        int totalPages,

        @Schema(description = "Whether this is the last page", example = "false")
        boolean last
) {

    /** Wraps a Spring {@link Page}, mapping each element on the way out. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }
}
