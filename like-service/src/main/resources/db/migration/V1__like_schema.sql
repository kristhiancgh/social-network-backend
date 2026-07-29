-- =============================================================================
--  likedb / V1 - schema
--  Owner: like-service (:8084)
-- =============================================================================

-- ----------------------------------------------------------------------------
-- post_likes : the write model. One row per (post, user) pair.
--              The UNIQUE constraint is what makes "one like per user per post"
--              true even under concurrent requests.
-- ----------------------------------------------------------------------------
CREATE TABLE post_likes (
    id         UUID        NOT NULL DEFAULT gen_random_uuid(),
    post_id    UUID        NOT NULL,
    user_id    UUID        NOT NULL,
    username   VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_post_likes           PRIMARY KEY (id),
    CONSTRAINT uk_post_likes_post_user UNIQUE (post_id, user_id)
);

COMMENT ON TABLE  post_likes         IS 'Write model: one row per like. Unliking deletes the row.';
COMMENT ON COLUMN post_likes.post_id IS 'Logical FK to postdb.posts.id - not enforced across databases by design.';
COMMENT ON COLUMN post_likes.user_id IS 'Logical FK to authdb.users.id - not enforced across databases by design.';

CREATE INDEX idx_post_likes_post ON post_likes (post_id);
CREATE INDEX idx_post_likes_user ON post_likes (user_id, created_at DESC);

-- ----------------------------------------------------------------------------
-- post_like_counters : the read model.
--
--  Counting likes with COUNT(*) on every timeline render is O(likes). This
--  table keeps the total pre-aggregated so the timeline reads it in O(1), and
--  it doubles as the lock target that serialises concurrent toggles on the
--  same post (see sp_toggle_post_like in V2).
-- ----------------------------------------------------------------------------
CREATE TABLE post_like_counters (
    post_id    UUID        NOT NULL,
    like_count BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT pk_post_like_counters      PRIMARY KEY (post_id),
    CONSTRAINT ck_post_like_count_natural CHECK (like_count >= 0)
);

COMMENT ON TABLE post_like_counters IS 'Read model: pre-aggregated like total per post, maintained by sp_toggle_post_like.';

CREATE INDEX idx_post_like_counters_ranking
    ON post_like_counters (like_count DESC, updated_at DESC);
