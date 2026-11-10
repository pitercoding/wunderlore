import {
    defaultDivisionOf,
    isDivisionOf,
    isRankingDivision,
    isRankingGame,
    type RankingDivision,
    type RankingGame,
} from "./games";

export type LeaderboardSelection =
    | { kind: "overview" }
    | { kind: "division"; game: RankingGame; division: RankingDivision };

function first(value: string | string[] | undefined): string | undefined {
    return Array.isArray(value) ? value[0] : value;
}

// What the URL asks for, reduced to something the backend will accept:
// - no (or an unknown) game: the overview;
// - a game and one of its divisions: that division;
// - a game without a division, or with one that game does not have (standard for Lore Challenge,
//   easy for Who Said It?, anything unknown): the game's first division, so an invalid pair is
//   never sent to the backend.
export function resolveSelection(params: {
    game?: string | string[];
    division?: string | string[];
}): LeaderboardSelection {
    const game = first(params.game);

    if (!isRankingGame(game)) {
        return { kind: "overview" };
    }

    const division = first(params.division);

    return {
        kind: "division",
        game,
        division:
            isRankingDivision(division) && isDivisionOf(game, division)
                ? division
                : defaultDivisionOf(game),
    };
}
