package com.wunderlore.ranking.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.wunderlore.ranking.GameType;
import com.wunderlore.ranking.RankingDivision;
import com.wunderlore.ranking.RankingEntryRepository;

// One division of one game: game and division are the filter that was applied (slugs). Positions
// are counted inside that division only. playedAt is an instant in UTC; the browser shows it in
// its own time zone. gameType and division repeat on every entry, which is redundant in a
// filtered ranking but keeps an entry self-describing.
public record LeaderboardResponse(String game, String division, List<Entry> entries) {

        public record Entry(
                        long position,
                        String nickname,
                        String gameType,
                        String division,
                        BigDecimal percentage,
                        Instant playedAt) {
        }

        public static LeaderboardResponse from(String game, String division, List<RankingEntryRepository.Row> rows) {
                return new LeaderboardResponse(
                                game,
                                division,
                                rows.stream()
                                                .map(row -> new Entry(
                                                                row.getPosition(),
                                                                row.getNickname(),
                                                                GameType.valueOf(row.getGameType()).slug(),
                                                                RankingDivision.valueOf(row.getDivision()).slug(),
                                                                row.getPercentage(),
                                                                row.getPlayedAt()))
                                                .toList());
        }
}
