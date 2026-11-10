// The four games whose results can be published to the leaderboard. The slugs are the backend's
// public names (GameType.slug()) and the same as the routes under /games. The order is the one
// the backend uses in the overview.
export const RANKING_GAMES = [
    { slug: "lore-challenge", label: "Lore Challenge" },
    { slug: "who-am-i", label: "Who Am I?" },
    { slug: "where-in-middle-earth", label: "Where in Middle-earth" },
    { slug: "who-said-it", label: "Who Said It?" },
] as const;

export type RankingGame = (typeof RANKING_GAMES)[number]["slug"];

export function isRankingGame(value: unknown): value is RankingGame {
    return RANKING_GAMES.some((game) => game.slug === value);
}

export function gameLabel(slug: RankingGame): string {
    return RANKING_GAMES.find((game) => game.slug === slug)?.label ?? slug;
}

// The divisions a result can compete in (the backend's RankingDivision slugs). Each game has its
// own: easy, medium and hard for three of them, and the single standard division for Who Said It?,
// which has no difficulty levels. Results are only ever compared inside one division of one game.
export const RANKING_DIVISIONS = [
    { slug: "easy", label: "Easy" },
    { slug: "medium", label: "Medium" },
    { slug: "hard", label: "Hard" },
    { slug: "standard", label: "Standard" },
] as const;

export type RankingDivision = (typeof RANKING_DIVISIONS)[number]["slug"];

const DIFFICULTY_DIVISIONS: readonly RankingDivision[] = ["easy", "medium", "hard"];
const SINGLE_DIVISION: readonly RankingDivision[] = ["standard"];

export function isRankingDivision(value: unknown): value is RankingDivision {
    return RANKING_DIVISIONS.some((division) => division.slug === value);
}

export function divisionLabel(slug: RankingDivision): string {
    return RANKING_DIVISIONS.find((division) => division.slug === slug)?.label ?? slug;
}

// The divisions of a game, in display order.
export function divisionsOf(game: RankingGame): readonly RankingDivision[] {
    return game === "who-said-it" ? SINGLE_DIVISION : DIFFICULTY_DIVISIONS;
}

export function isDivisionOf(game: RankingGame, division: RankingDivision): boolean {
    return divisionsOf(game).includes(division);
}

// Easy, or standard for the game that has no levels.
export function defaultDivisionOf(game: RankingGame): RankingDivision {
    return divisionsOf(game)[0];
}

// How many (game, division) pairs exist: three games with three divisions and one with one.
export const RANKING_DIVISION_COUNT = RANKING_GAMES.reduce(
    (total, game) => total + divisionsOf(game.slug).length,
    0,
);

// The division a finished game competes in, for showing it to the player. The backend decides the
// real one from the game it kept; this only labels the result screen and is never sent.
export function divisionFromDifficulty(difficulty: "EASY" | "MEDIUM" | "HARD"): RankingDivision {
    switch (difficulty) {
        case "EASY":
            return "easy";
        case "MEDIUM":
            return "medium";
        case "HARD":
            return "hard";
    }
}

export const OVERVIEW_HREF = "/leaderboard";

// The link to the full ranking of one division. Only built from a valid pair.
export function rankingHref(game: RankingGame, division: RankingDivision): string {
    return `/leaderboard?game=${game}&division=${division}`;
}
