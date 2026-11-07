// The four games whose results can be published to the leaderboard. The slugs are the backend's
// public names (GameType.slug()) and the same as the routes under /games.
export const RANKING_GAMES = [
    { slug: "who-said-it", label: "Who Said It?" },
    { slug: "lore-challenge", label: "Lore Challenge" },
    { slug: "who-am-i", label: "Who Am I?" },
    { slug: "where-in-middle-earth", label: "Where in Middle-earth" },
] as const;

export type RankingGame = (typeof RANKING_GAMES)[number]["slug"];

export function isRankingGame(value: unknown): value is RankingGame {
    return RANKING_GAMES.some((game) => game.slug === value);
}

export function gameLabel(slug: RankingGame): string {
    return RANKING_GAMES.find((game) => game.slug === slug)?.label ?? slug;
}
