import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isNonBlankString, isRecord } from "@/lib/api/guards";
import { isRankingGame, type RankingGame } from "@/lib/ranking/games";

export interface PublishedEntry {
    gameType: RankingGame;
    nickname: string;
    // 0 to 100, computed by the backend.
    percentage: number;
    // An instant in UTC (ISO 8601).
    playedAt: string;
    // True when the game had been published before; the nickname is the original one.
    alreadyPublished: boolean;
}

export interface LeaderboardEntry {
    position: number;
    nickname: string;
    gameType: RankingGame;
    percentage: number;
    playedAt: string;
}

export interface Leaderboard {
    // The filter that was applied; null for every game.
    game: RankingGame | null;
    entries: LeaderboardEntry[];
}

function isPercentage(value: unknown): value is number {
    return typeof value === "number" && Number.isFinite(value) && value >= 0 && value <= 100;
}

function isInstant(value: unknown): value is string {
    return isNonBlankString(value) && !Number.isNaN(Date.parse(value));
}

function isPublishedEntry(value: unknown): value is PublishedEntry {
    return (
        isRecord(value) &&
        isRankingGame(value.gameType) &&
        isNonBlankString(value.nickname) &&
        isPercentage(value.percentage) &&
        isInstant(value.playedAt) &&
        typeof value.alreadyPublished === "boolean"
    );
}

function isLeaderboardEntry(value: unknown): value is LeaderboardEntry {
    return (
        isRecord(value) &&
        typeof value.position === "number" &&
        Number.isInteger(value.position) &&
        value.position >= 1 &&
        isNonBlankString(value.nickname) &&
        isRankingGame(value.gameType) &&
        isPercentage(value.percentage) &&
        isInstant(value.playedAt)
    );
}

function isLeaderboard(value: unknown): value is Leaderboard {
    return (
        isRecord(value) &&
        (value.game === null || isRankingGame(value.game)) &&
        Array.isArray(value.entries) &&
        value.entries.every(isLeaderboardEntry)
    );
}

// Publishes a finished game. Only the game and the nickname are sent: the score is read by the
// backend from the game it kept.
export async function postRankingEntry(
    gameType: RankingGame,
    gameId: string,
    nickname: string,
): Promise<PublishedEntry> {
    return apiFetch(
        "/api/ranking/entries",
        isPublishedEntry,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ gameType, gameId, nickname }),
        },
    );
}

// How long the public leaderboard read may be served from Next.js's cache. Publishing a result
// expires LEADERBOARD_CACHE_TAG at once, so the player sees it right away; for results published
// by others (or after a restart of the cache) it can take up to this long to appear. The
// publish itself is never cached.
export const LEADERBOARD_REVALIDATE_SECONDS = 30;

// Every leaderboard read (all games and each filter) carries this tag; publishing expires it.
export const LEADERBOARD_CACHE_TAG = "leaderboard";

export async function fetchLeaderboard(game?: RankingGame): Promise<Leaderboard> {
    const query = game ? `?game=${encodeURIComponent(game)}` : "";

    return apiFetch(`/api/ranking${query}`, isLeaderboard, undefined, {
        revalidateSeconds: LEADERBOARD_REVALIDATE_SECONDS,
        tags: [LEADERBOARD_CACHE_TAG],
        // The leaderboard is often the first page a visitor opens, so it may reach a sleeping
        // backend (see ColdStartHint on the page): wait as long as the game start-up calls do.
        timeoutMs: COLD_START_TIMEOUT_MS,
    });
}
