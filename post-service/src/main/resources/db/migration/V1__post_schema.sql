-- =============================================================================
--  postdb / V1 - schema
--  Owner: post-service (:8083)
-- =============================================================================

CREATE OR REPLACE FUNCTION fn_touch_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- ----------------------------------------------------------------------------
-- posts : message + author + publication date, as required by the spec.
--
--  author_username / author_alias are DENORMALISED copies of data owned by
--  auth-service and profile-service. This is deliberate: listing the timeline
--  is the hottest read path in the app, and denormalising removes a
--  synchronous cross-service call per post. The copy is written once at
--  creation time from the verified JWT claims.
--
--  deleted implements soft delete so a post disappearing from the timeline
--  never orphans the likes stored in likedb.
-- ----------------------------------------------------------------------------
CREATE TABLE posts (
    id              UUID         NOT NULL DEFAULT gen_random_uuid(),
    author_id       UUID         NOT NULL,
    author_username VARCHAR(50)  NOT NULL,
    author_alias    VARCHAR(50),
    message         VARCHAR(500) NOT NULL,
    published_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT pk_posts                PRIMARY KEY (id),
    CONSTRAINT ck_posts_message_filled CHECK (length(btrim(message)) > 0),
    CONSTRAINT ck_posts_not_future     CHECK (published_at <= now() + INTERVAL '5 minutes')
);

COMMENT ON TABLE  posts                 IS 'User publications. Timeline is ordered by published_at DESC.';
COMMENT ON COLUMN posts.author_id       IS 'Logical FK to authdb.users.id - not enforced across databases by design.';
COMMENT ON COLUMN posts.author_username IS 'Denormalised copy taken from the JWT at creation time.';
COMMENT ON COLUMN posts.published_at    IS 'Defaults to now() on insert, as required by the spec.';
COMMENT ON COLUMN posts.deleted         IS 'Soft delete flag; deleted posts are excluded from every read.';

-- Timeline query: WHERE deleted = FALSE ORDER BY published_at DESC
CREATE INDEX idx_posts_timeline
    ON posts (published_at DESC)
    WHERE deleted = FALSE;

-- "Posts by this author", used by the profile screen
CREATE INDEX idx_posts_author
    ON posts (author_id, published_at DESC)
    WHERE deleted = FALSE;

CREATE TRIGGER trg_posts_touch_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION fn_touch_updated_at();
