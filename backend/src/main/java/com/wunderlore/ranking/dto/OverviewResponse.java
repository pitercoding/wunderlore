package com.wunderlore.ranking.dto;

import java.util.List;

import com.wunderlore.ranking.RankingService;

// The first places of every division, one group per game and division (slugs). Groups are never
// combined: positions are counted inside each group, and no ranking exists across games or
// divisions. A division nobody has played is still a group, with no entries. Groups come in a
// fixed order: the games in the order of the GameType constants and, within a game, its
// divisions in display order (easy, medium, hard; standard for Who Said It?).
public record OverviewResponse(List<Group> groups) {

    public record Group(String game, String division, List<LeaderboardResponse.Entry> entries) {
    }

    public static OverviewResponse from(List<RankingService.OverviewGroup> groups) {
        return new OverviewResponse(groups.stream()
                .map(group -> new Group(
                        group.game().slug(),
                        group.division().slug(),
                        group.entries().stream().map(LeaderboardResponse.Entry::from).toList()))
                .toList());
    }
}
