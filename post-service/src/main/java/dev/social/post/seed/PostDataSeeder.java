package dev.social.post.seed;

import dev.social.post.posts.domain.Post;
import dev.social.post.posts.repositories.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Exactly one publication per demo user, as the brief requires.
 *
 * <p>Publication times are staggered backwards so the timeline
 * ({@code ORDER BY published_at DESC}) has a meaningful order instead of five
 * rows sharing one timestamp - which would make paging non-deterministic and
 * the screenshots in the manual inconsistent.
 */
@Component
@ConditionalOnProperty(prefix = "app.seeder", name = "enabled", havingValue = "true")
public class PostDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PostDataSeeder.class);

    private record SeedPost(UUID postId,
                            UUID authorId,
                            String authorUsername,
                            String authorAlias,
                            String message,
                            long hoursAgo) {
    }

    private static final List<SeedPost> SEED_POSTS = List.of(
            new SeedPost(
                    UUID.fromString("22222222-2222-2222-2222-222222220101"),
                    UUID.fromString("11111111-1111-1111-1111-111111110101"),
                    "jdoe", "johnny",
                    "First post on the network. The stored procedure that toggles likes is prettier than I expected.",
                    5),
            new SeedPost(
                    UUID.fromString("22222222-2222-2222-2222-222222220102"),
                    UUID.fromString("11111111-1111-1111-1111-111111110102"),
                    "mgarcia", "mary_g",
                    "Redesigned the timeline card today. Fewer borders, more breathing room.",
                    4),
            new SeedPost(
                    UUID.fromString("22222222-2222-2222-2222-222222220103"),
                    UUID.fromString("11111111-1111-1111-1111-111111110103"),
                    "lchen", "li_chen",
                    "Denormalising the author name into the posts table removed an entire service call per row.",
                    3),
            new SeedPost(
                    UUID.fromString("22222222-2222-2222-2222-222222220104"),
                    UUID.fromString("11111111-1111-1111-1111-111111110104"),
                    "arossi", "aisha_r",
                    "Signals plus a SignalStore made the like counter update without a single manual subscription.",
                    2),
            new SeedPost(
                    UUID.fromString("22222222-2222-2222-2222-222222220105"),
                    UUID.fromString("11111111-1111-1111-1111-111111110105"),
                    "kcamilo", "kris",
                    "Five services, four databases, one docker compose up. Open two browser tabs and watch the likes sync.",
                    1));

    private final PostRepository posts;

    public PostDataSeeder(PostRepository posts) {
        this.posts = posts;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = Instant.now();
        int created = 0;

        for (SeedPost seed : SEED_POSTS) {
            if (posts.existsById(seed.postId())) {
                continue;
            }
            posts.save(new Post(
                    seed.postId(),
                    seed.authorId(),
                    seed.authorUsername(),
                    seed.authorAlias(),
                    seed.message(),
                    now.minus(Duration.ofHours(seed.hoursAgo()))));
            created++;
        }

        if (created > 0) {
            log.info("Seeded {} demo post(s)", created);
        } else {
            log.info("Demo posts already present, seeder skipped");
        }
    }
}
