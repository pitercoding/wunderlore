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

        BigDecimal getPercentage();

        Instant getPlayedAt();
    }

    Optional<RankingEntry> findByGameTypeAndGameId(GameType gameType, String gameId);

    // The best results first: higher percentage, then the earlier game, then id so the order is
    // total. RANK() gives equal percentages the same position (1, 1, 3, ...). Its window is
    // computed over the rows kept by LIMIT, which is exact: a row's rank only depends on the rows
    // with a strictly higher percentage, and those always come before it in this order.
    @Query(value = """
            SELECT RANK() OVER (ORDER BY t.percentage DESC) AS position,
                   t.nickname AS nickname,
                   t.game_type AS gameType,
                   t.percentage AS percentage,
                   t.played_at AS playedAt
            FROM (SELECT id, nickname, game_type, percentage, played_at
                  FROM ranking_entries
                  ORDER BY percentage DESC, played_at ASC, id ASC
                  LIMIT :limit) t
            ORDER BY t.percentage DESC, t.played_at ASC, t.id ASC
            """, nativeQuery = true)
    List<Row> findTop(@Param("limit") int limit);

    // The same, for one game: positions are counted inside that game only.
    @Query(value = """
            SELECT RANK() OVER (ORDER BY t.percentage DESC) AS position,
                   t.nickname AS nickname,
                   t.game_type AS gameType,
                   t.percentage AS percentage,
                   t.played_at AS playedAt
            FROM (SELECT id, nickname, game_type, percentage, played_at
                  FROM ranking_entries
                  WHERE game_type = :gameType
                  ORDER BY percentage DESC, played_at ASC, id ASC
                  LIMIT :limit) t
            ORDER BY t.percentage DESC, t.played_at ASC, t.id ASC
            """, nativeQuery = true)
    List<Row> findTopByGameType(@Param("gameType") String gameType, @Param("limit") int limit);
}
