-- The division a result competes in: results are only compared inside one (game, division) pair.
-- EASY, MEDIUM and HARD for Lore Challenge, Who Am I? and Where in Middle-earth; STANDARD, the
-- single division of Who Said It?, which has no difficulty levels.
--
-- The column stays nullable on purpose. A row without a division is a result published before
-- this migration whose division cannot be recovered, and an instance of the previous version of
-- the application, which does not know the column, may still insert rows while a new version is
-- starting. No NOT NULL or "division IS NOT NULL" check is added for that reason (not even NOT
-- VALID, which would still apply to new rows).
ALTER TABLE ranking_entries ADD COLUMN division VARCHAR(16);

-- Lore Challenge: max_score is 10 questions times 10, 20 or 30 points, one value per difficulty.
-- A row with any other max_score stays NULL instead of failing the migration.
UPDATE ranking_entries
   SET division = CASE max_score WHEN 100 THEN 'EASY' WHEN 200 THEN 'MEDIUM' WHEN 300 THEN 'HARD' END
 WHERE game_type = 'LORE_CHALLENGE'
   AND division IS NULL;

-- Who Said It? has one mode only.
UPDATE ranking_entries
   SET division = 'STANDARD'
 WHERE game_type = 'WHO_SAID_IT'
   AND division IS NULL;

-- Who Am I? and Where in Middle-earth stay NULL: their max_score is 1000 whatever the difficulty,
-- so it cannot be recovered. A NULL passes this check (no value, nothing to contradict).
ALTER TABLE ranking_entries
    ADD CONSTRAINT ranking_entries_division_check CHECK (
        division IS NULL
        OR (game_type = 'WHO_SAID_IT' AND division = 'STANDARD')
        OR (game_type IN ('LORE_CHALLENGE', 'WHO_AM_I', 'WHERE_IN_MIDDLE_EARTH')
            AND division IN ('EASY', 'MEDIUM', 'HARD'))
    );

-- Ranking of one division of one game. The indexes created by V1 are kept: the previous version of
-- the application still reads the leaderboard through them.
CREATE INDEX ranking_entries_division_order_idx
    ON ranking_entries (game_type, division, percentage DESC, played_at ASC, id ASC);
