package com.wunderlore.ranking;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// Created by Spring Data only when persistence is enabled (see PersistenceAutoConfigurationFilter).
public interface RankingEntryRepository extends JpaRepository<RankingEntry, UUID> {

    // One row of the leaderboard, as the queries below name the columns.
    interface Row {

        long getPosition();

        String getNickname();

        String getGameType();

        String getDivision();

        BigDecimal getPercentage();

        Instant getPlayedAt();
    }

    Optional<RankingEntry> findByGameTypeAndGameId(GameType gameType, String gameId);

    // One division of one game, best results first: higher percentage, then the
    // earlier game, then
    // id, so the order is total. Rows without a division (published before V2)
    // match no division
    // and never appear. RANK() orders by the percentage alone, so equal percentages
    // share a
    // position (1, 1, 3, ...) whatever their date or id. Its window is computed
    // over the rows
    // kept by LIMIT, which is exact: a row's rank only depends on the rows with a
    // strictly higher
    // percentage, and those always come before it in this order.
    @Query(value = """
            SELECT RANK() OVER (ORDER BY t.percentage DESC) AS position,
                t.nickname AS nickname,
                t.game_type AS gameType,
                t.division AS division,
                t.percentage AS percentage,
                t.played_at AS playedAt
            FROM (SELECT id, nickname, game_type, division, percentage, played_at
                FROM ranking_entries
                WHERE game_type = :gameType AND division = :division
                ORDER BY percentage DESC, played_at ASC, id ASC
                LIMIT :limit) t
            ORDER BY t.percentage DESC, t.played_at ASC, t.id ASC
            """, nativeQuery = true)
    List<Row> findTopByGameTypeAndDivision(
            @Param("gameType") String gameType, @Param("division") String division, @Param("limit") int limit);

    // The first places of every division of every game, in one query: RANK() is computed per
    // (game_type, division) over all its rows, by the percentage alone, and every row whose
    // position is at most :positions is kept. A tie at the cut is therefore kept whole (four
    // players tied for first all appear, and the next one is fifth). Rows without a division
    // (published before V2) are left out. Within a division the rows come in display order:
    // percentage, then the earlier game, then id; the groups themselves are ordered by the service.
    @Query(value = """
            SELECT t.position AS position,
                   t.nickname AS nickname,
                   t.game_type AS gameType,
                   t.division AS division,
                   t.percentage AS percentage,
                   t.played_at AS playedAt
            FROM (SELECT id, nickname, game_type, division, percentage, played_at,
                         RANK() OVER (PARTITION BY game_type, division ORDER BY percentage DESC) AS position
                  FROM ranking_entries
                  WHERE division IS NOT NULL) t
            WHERE t.position <= :positions
            ORDER BY t.game_type, t.division, t.percentage DESC, t.played_at ASC, t.id ASC
            """, nativeQuery = true)
    List<Row> findOverview(@Param("positions") int positions);
}
