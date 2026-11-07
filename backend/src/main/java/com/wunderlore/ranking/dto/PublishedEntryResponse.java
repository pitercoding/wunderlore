package com.wunderlore.ranking.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.wunderlore.ranking.RankingEntry;

// What publishing returns: the entry as stored. alreadyPublished is true when the game had been
// published before (the original nickname is kept).
public record PublishedEntryResponse(
        String gameType,
        String nickname,
        BigDecimal percentage,
        Instant playedAt,
        boolean alreadyPublished) {

    public static PublishedEntryResponse from(RankingEntry entry, boolean alreadyPublished) {
        return new PublishedEntryResponse(
                entry.getGameType().slug(),
                entry.getNickname(),
                entry.getPercentage(),
                entry.getPlayedAt(),
                alreadyPublished);
    }
}
