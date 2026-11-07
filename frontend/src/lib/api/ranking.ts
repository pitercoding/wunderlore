import { apiFetch } from "@/lib/api/client";
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

export async function fetchLeaderboard(game?: RankingGame): Promise<Leaderboard> {
    const query = game ? `?game=${encodeURIComponent(game)}` : "";

    return apiFetch(`/api/ranking${query}`, isLeaderboard);
}
