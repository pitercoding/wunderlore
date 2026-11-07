package com.wunderlore.ranking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.wunderlore.ranking.GameType;
import com.wunderlore.ranking.RankingEntryRepository;

// game is the filter that was applied, or null for the global ranking. Positions are counted
// inside that set. playedAt is an instant in UTC; the browser shows it in its own time zone.
public record LeaderboardResponse(String game, List<Entry> entries) {

        public record Entry(
                        long position,
                        String nickname,
                        String gameType,
                        BigDecimal percentage,
                        Instant playedAt) {
        }

        public static LeaderboardResponse from(String game, List<RankingEntryRepository.Row> rows) {
                return new LeaderboardResponse(
                                game == null || game.isBlank() ? null : game,
                                rows.stream()
                                                .map(row -> new Entry(
                                                                row.getPosition(),
                                                                row.getNickname(),
                                                                GameType.valueOf(row.getGameType()).slug(),
                                                                row.getPercentage(),
                                                                row.getPlayedAt()))
                                                .toList());
        }
}
