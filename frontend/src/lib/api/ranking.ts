import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isNonBlankString, isRecord } from "@/lib/api/guards";
import {
    isDivisionOf,
    isRankingDivision,
    isRankingGame,
    RANKING_DIVISION_COUNT,
    type RankingDivision,
    type RankingGame,
} from "@/lib/ranking/games";

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
    // Counted by the backend inside this division only; equal percentages share a position.
    position: number;
    nickname: string;
    gameType: RankingGame;
    division: RankingDivision;
    // 0 to 100, computed by the backend.
    percentage: number;
    playedAt: string;
}

// The ranking of one division of one game, in the order the backend sent.
export interface LeaderboardResponse {
    game: RankingGame;
    division: RankingDivision;
    entries: LeaderboardEntry[];
}

// The first places of one division, empty when nobody has played it.
export interface LeaderboardOverviewGroup {
    game: RankingGame;
    division: RankingDivision;
    entries: LeaderboardEntry[];
}

// Every valid (game, division) pair exactly once, in the order the backend sent.
export interface LeaderboardOverviewResponse {
    groups: LeaderboardOverviewGroup[];
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

// An entry must belong to the division it is listed under: a mix-up is a broken contract, not
// something to show.
function isEntryOf(
    value: unknown,
    game: RankingGame,
    division: RankingDivision,
): value is LeaderboardEntry {
    return (
        isRecord(value) &&
        typeof value.position === "number" &&
        Number.isInteger(value.position) &&
        value.position >= 1 &&
        isNonBlankString(value.nickname) &&
        value.gameType === game &&
        value.division === division &&
        isPercentage(value.percentage) &&
        isInstant(value.playedAt)
    );
}

function isValidPair(game: unknown, division: unknown): boolean {
    return isRankingGame(game) && isRankingDivision(division) && isDivisionOf(game, division);
}

function isLeaderboardResponse(value: unknown): value is LeaderboardResponse {
    if (!isRecord(value) || !isValidPair(value.game, value.division)) {
        return false;
    }

    const game = value.game as RankingGame;
    const division = value.division as RankingDivision;

    return Array.isArray(value.entries) && value.entries.every((entry) => isEntryOf(entry, game, division));
}

function isOverviewGroup(value: unknown): value is LeaderboardOverviewGroup {
    if (!isRecord(value) || !isValidPair(value.game, value.division)) {
        return false;
    }

    const game = value.game as RankingGame;
    const division = value.division as RankingDivision;

    return Array.isArray(value.entries) && value.entries.every((entry) => isEntryOf(entry, game, division));
}

// The backend always answers every division, empty ones included, so exactly the valid pairs must
// be there, each once.
function isLeaderboardOverview(value: unknown): value is LeaderboardOverviewResponse {
    if (!isRecord(value) || !Array.isArray(value.groups)) {
        return false;
    }

    if (value.groups.length !== RANKING_DIVISION_COUNT || !value.groups.every(isOverviewGroup)) {
        return false;
    }

    const pairs = new Set(
        (value.groups as LeaderboardOverviewGroup[]).map((group) => `${group.game}/${group.division}`),
    );

    return pairs.size === RANKING_DIVISION_COUNT;
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

// Every leaderboard read (the overview and each division) carries this tag; publishing expires it.
export const LEADERBOARD_CACHE_TAG = "leaderboard";

// Every leaderboard read (the overview and each division) shares the options below: Next.js keeps
// one cached response per URL, so each division and the overview are stored separately, and they
// all carry the tag a publish expires.
const LEADERBOARD_FETCH_OPTIONS = {
    revalidateSeconds: LEADERBOARD_REVALIDATE_SECONDS,
    tags: [LEADERBOARD_CACHE_TAG],
    // The leaderboard is often the first page a visitor opens, so it may reach a sleeping
    // backend (see ColdStartHint on the page): wait as long as the game start-up calls do.
    timeoutMs: COLD_START_TIMEOUT_MS,
};

// The ranking of one division of one game. A pair that does not exist is refused here, before any
// request: the backend would answer 400.
export async function fetchLeaderboard(
    game: RankingGame,
    division: RankingDivision,
): Promise<LeaderboardResponse> {
    if (!isDivisionOf(game, division)) {
        throw new Error("That division does not exist in that game");
    }

    const query = `?game=${encodeURIComponent(game)}&division=${encodeURIComponent(division)}`;

    return apiFetch(`/api/ranking${query}`, isLeaderboardResponse, undefined, LEADERBOARD_FETCH_OPTIONS);
}

// The first places of every division, grouped by game and division. Never combined: there is no
// ranking across games or divisions.
export async function fetchLeaderboardOverview(): Promise<LeaderboardOverviewResponse> {
    return apiFetch("/api/ranking/overview", isLeaderboardOverview, undefined, LEADERBOARD_FETCH_OPTIONS);
}
