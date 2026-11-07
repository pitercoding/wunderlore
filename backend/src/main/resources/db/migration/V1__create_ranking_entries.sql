-- Published results of finished games, one row per game played. There are no users: the nickname
-- is only a display name and may repeat. The percentage is computed by the backend from the
-- score the server kept for the game and is stored with enough precision to order by it exactly
-- (the displayed two decimals are never used for ordering).
CREATE TABLE ranking_entries (
    id          UUID PRIMARY KEY,
    game_type   VARCHAR(32)   NOT NULL,
    game_id     VARCHAR(64)   NOT NULL,
    nickname    VARCHAR(16)   NOT NULL,
    score       INTEGER       NOT NULL,
    max_score   INTEGER       NOT NULL,
    percentage  NUMERIC(9, 6) NOT NULL,
    played_at   TIMESTAMPTZ   NOT NULL,

    CONSTRAINT ranking_entries_game_type_check
        CHECK (game_type IN ('LORE_CHALLENGE', 'WHO_AM_I', 'WHERE_IN_MIDDLE_EARTH', 'WHO_SAID_IT')),
    CONSTRAINT ranking_entries_score_check CHECK (max_score > 0 AND score >= 0 AND score <= max_score),
    CONSTRAINT ranking_entries_percentage_check CHECK (percentage >= 0 AND percentage <= 100),
    CONSTRAINT ranking_entries_nickname_length_check CHECK (char_length(nickname) BETWEEN 3 AND 16),

    -- A game can be published once. This constraint, not application code, is what stops two
    -- concurrent requests for the same game from both succeeding.
    CONSTRAINT ranking_entries_game_unique UNIQUE (game_type, game_id)
);

-- Global ranking: best percentage first, earlier game first on an exact tie.
CREATE INDEX ranking_entries_global_order_idx
    ON ranking_entries (percentage DESC, played_at ASC, id ASC);

-- Ranking filtered by game.
CREATE INDEX ranking_entries_game_order_idx
    ON ranking_entries (game_type, percentage DESC, played_at ASC, id ASC);
