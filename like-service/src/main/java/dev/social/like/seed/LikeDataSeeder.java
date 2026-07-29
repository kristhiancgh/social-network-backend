package dev.social.like.seed;

import dev.social.like.likes.domain.PostLike;
import dev.social.like.likes.repositories.LikeProcedureRepository;
import dev.social.like.likes.repositories.PostLikeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cross-likes between the demo users, so the timeline does not start at zero
 * everywhere.
 *
 * <p>Nobody likes their own post, which makes "the heart starts unpressed for
 * the author" trivial to verify by hand.
 *
 * <p>The counters are not written directly. They are rebuilt from the inserted
 * rows by {@code sp_rebuild_like_counters}, so if that procedure is ever wrong
 * the seed fails loudly here instead of quietly producing totals that disagree
 * with the likes behind them.
 */
@Component
@ConditionalOnProperty(prefix = "app.seeder", name = "enabled", havingValue = "true")
public class LikeDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LikeDataSeeder.class);

    private static final UUID POST_1 = UUID.fromString("22222222-2222-2222-2222-222222220101");
    private static final UUID POST_2 = UUID.fromString("22222222-2222-2222-2222-222222220102");
    private static final UUID POST_3 = UUID.fromString("22222222-2222-2222-2222-222222220103");
    private static final UUID POST_4 = UUID.fromString("22222222-2222-2222-2222-222222220104");
    private static final UUID POST_5 = UUID.fromString("22222222-2222-2222-2222-222222220105");

    private static final UUID JDOE = UUID.fromString("11111111-1111-1111-1111-111111110101");
    private static final UUID MGARCIA = UUID.fromString("11111111-1111-1111-1111-111111110102");
    private static final UUID LCHEN = UUID.fromString("11111111-1111-1111-1111-111111110103");
    private static final UUID AROSSI = UUID.fromString("11111111-1111-1111-1111-111111110104");
    private static final UUID KCAMILO = UUID.fromString("11111111-1111-1111-1111-111111110105");

    private record SeedLike(UUID postId, UUID userId, String username) {
    }


    private static final List<SeedLike> SEED_LIKES = List.of(
            new SeedLike(POST_1, MGARCIA, "mgarcia"),
            new SeedLike(POST_1, LCHEN, "lchen"),
            new SeedLike(POST_1, KCAMILO, "kcamilo"),

            new SeedLike(POST_2, JDOE, "jdoe"),
            new SeedLike(POST_2, AROSSI, "arossi"),

            new SeedLike(POST_3, JDOE, "jdoe"),
            new SeedLike(POST_3, KCAMILO, "kcamilo"),

            new SeedLike(POST_4, MGARCIA, "mgarcia"),

            new SeedLike(POST_5, JDOE, "jdoe"),
            new SeedLike(POST_5, MGARCIA, "mgarcia"),
            new SeedLike(POST_5, LCHEN, "lchen"),
            new SeedLike(POST_5, AROSSI, "arossi"));

    private final PostLikeRepository likes;
    private final LikeProcedureRepository procedures;

    public LikeDataSeeder(PostLikeRepository likes, LikeProcedureRepository procedures) {
        this.likes = likes;
        this.procedures = procedures;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int created = 0;

        for (SeedLike seed : SEED_LIKES) {
            if (likes.existsByPostIdAndUserId(seed.postId(), seed.userId())) {
                continue;
            }
            likes.save(PostLike.create(seed.postId(), seed.userId(), seed.username()));
            created++;
        }

        if (created == 0) {
            log.info("Demo likes already present, seeder skipped");
            return;
        }

        likes.flush();
        long counterRows = procedures.rebuildCounters();

        log.info("Seeded {} demo like(s) across {} post(s)", created, counterRows);
    }
}
