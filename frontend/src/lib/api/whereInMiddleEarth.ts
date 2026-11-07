import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isIntegerFrom, isNonBlankString, isRecord } from "@/lib/api/guards";

export const WHERE_IN_MIDDLE_EARTH_DIFFICULTIES = ["EASY", "MEDIUM", "HARD"] as const;

export type WhereInMiddleEarthDifficulty = (typeof WHERE_IN_MIDDLE_EARTH_DIFFICULTIES)[number];

// The map's own coordinate system: an SVG viewBox of 1000 x 700, x to the east and y to the
// south. Every position in the API is in these units (see contracts/where-in-middle-earth).
export const MAP_WIDTH = 1000;
export const MAP_HEIGHT = 700;

export const WHERE_IN_MIDDLE_EARTH_ACCURACIES = [
    "EXCELLENT",
    "VERY_GOOD",
    "GOOD",
    "CLOSE",
    "FAR",
] as const;

export type WhereInMiddleEarthAccuracy = (typeof WHERE_IN_MIDDLE_EARTH_ACCURACIES)[number];

export interface MapPosition {
    x: number;
    y: number;
}

// The round waiting for an answer. It carries the name and a short description of the place,
// never where it is.
export interface WhereInMiddleEarthRound {
    gameId: string;
    difficulty: WhereInMiddleEarthDifficulty;
    roundNumber: number;
    totalRounds: number;
    locationName: string;
    description: string;
    score: number;
    maxScore: number;
}

// What an answered round discloses: the real place, where it is and why.
export interface WhereInMiddleEarthReveal extends MapPosition {
    locationName: string;
    regionName: string;
    period: string;
    explanation: string;
}

export interface WhereInMiddleEarthRoundSummary {
    roundNumber: number;
    locationName: string;
    accuracy: WhereInMiddleEarthAccuracy;
    distance: number;
    points: number;
}

export interface WhereInMiddleEarthResult {
    gameId: string;
    difficulty: WhereInMiddleEarthDifficulty;
    totalRounds: number;
    score: number;
    maxScore: number;
    percentage: number;
    closeHits: number;
    averageDistance: number;
    rounds: WhereInMiddleEarthRoundSummary[];
}

// The round is always over once answered, so reveal is always set; result only after the last.
export interface WhereInMiddleEarthAnswer {
    gameId: string;
    roundNumber: number;
    submitted: MapPosition;
    distance: number;
    accuracy: WhereInMiddleEarthAccuracy;
    pointsAwarded: number;
    score: number;
    reveal: WhereInMiddleEarthReveal;
    gameFinished: boolean;
    result: WhereInMiddleEarthResult | null;
}

function isDifficulty(value: unknown): value is WhereInMiddleEarthDifficulty {
    return (
        typeof value === "string" &&
        (WHERE_IN_MIDDLE_EARTH_DIFFICULTIES as readonly string[]).includes(value)
    );
}

function isAccuracy(value: unknown): value is WhereInMiddleEarthAccuracy {
    return (
        typeof value === "string" &&
        (WHERE_IN_MIDDLE_EARTH_ACCURACIES as readonly string[]).includes(value)
    );
}

function isNumberBetween(value: unknown, minimum: number, maximum: number): value is number {
    return typeof value === "number" && Number.isFinite(value) && value >= minimum && value <= maximum;
}

function isMapPosition(value: unknown): value is MapPosition {
    return (
        isRecord(value) &&
        isNumberBetween(value.x, 0, MAP_WIDTH) &&
        isNumberBetween(value.y, 0, MAP_HEIGHT)
    );
}

function isDistance(value: unknown): value is number {
    return isNumberBetween(value, 0, Math.hypot(MAP_WIDTH, MAP_HEIGHT));
}

function isWhereInMiddleEarthRound(value: unknown): value is WhereInMiddleEarthRound {
    return (
        isRecord(value) &&
        isNonBlankString(value.gameId) &&
        isDifficulty(value.difficulty) &&
        isIntegerFrom(1, value.roundNumber) &&
        isIntegerFrom(1, value.totalRounds) &&
        isNonBlankString(value.locationName) &&
        isNonBlankString(value.description) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(0, value.maxScore)
    );
}

function isReveal(value: unknown): value is WhereInMiddleEarthReveal {
    return (
        isRecord(value) &&
        isMapPosition(value) &&
        isNonBlankString(value.locationName) &&
        isNonBlankString(value.regionName) &&
        isNonBlankString(value.period) &&
        isNonBlankString(value.explanation)
    );
}

function isRoundSummary(value: unknown): value is WhereInMiddleEarthRoundSummary {
    return (
        isRecord(value) &&
        isIntegerFrom(1, value.roundNumber) &&
        isNonBlankString(value.locationName) &&
        isAccuracy(value.accuracy) &&
        isDistance(value.distance) &&
        isIntegerFrom(0, value.points)
    );
}

function isWhereInMiddleEarthResult(value: unknown): value is WhereInMiddleEarthResult {
    return (
        isRecord(value) &&
        isNonBlankString(value.gameId) &&
        isDifficulty(value.difficulty) &&
        isIntegerFrom(1, value.totalRounds) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(0, value.maxScore) &&
        isIntegerFrom(0, value.percentage) &&
        value.percentage <= 100 &&
        isIntegerFrom(0, value.closeHits) &&
        isDistance(value.averageDistance) &&
        Array.isArray(value.rounds) &&
        value.rounds.every(isRoundSummary)
    );
}

// result is always present, null until the last round. A game that ends without a result, or a
// result before the end, cannot be played.
function isWhereInMiddleEarthAnswer(value: unknown): value is WhereInMiddleEarthAnswer {
    return (
        isRecord(value) &&
        isNonBlankString(value.gameId) &&
        isIntegerFrom(1, value.roundNumber) &&
        isMapPosition(value.submitted) &&
        isDistance(value.distance) &&
        isAccuracy(value.accuracy) &&
        isIntegerFrom(0, value.pointsAwarded) &&
        isIntegerFrom(0, value.score) &&
        isReveal(value.reveal) &&
        typeof value.gameFinished === "boolean" &&
        (value.result === null || isWhereInMiddleEarthResult(value.result)) &&
        (value.result !== null) === value.gameFinished
    );
}

const GAMES_PATH = "/api/games/where-in-middle-earth/games";

function requireNonBlank(value: string, name: string): void {
    if (!value.trim()) {
        throw new Error(`${name} must be a non-empty string`);
    }
}

// Creates server-side state, so it is a POST. The first game of a visit may wake the backend; an
// unused game costs nothing.
export async function createGame(
    difficulty: WhereInMiddleEarthDifficulty,
): Promise<WhereInMiddleEarthRound> {
    return apiFetch(
        GAMES_PATH,
        isWhereInMiddleEarthRound,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ difficulty }),
        },
        { timeoutMs: COLD_START_TIMEOUT_MS },
    );
}

// The round the game is waiting on: the next one, once the previous was answered.
export async function getCurrentRound(gameId: string): Promise<WhereInMiddleEarthRound> {
    requireNonBlank(gameId, "gameId");

    return apiFetch(`${GAMES_PATH}/${encodeURIComponent(gameId)}/round`, isWhereInMiddleEarthRound);
}

// Only the round and the chosen point go to the backend: what the answer was worth is decided there.
export async function submitAnswer(
    gameId: string,
    roundNumber: number,
    position: MapPosition,
): Promise<WhereInMiddleEarthAnswer> {
    requireNonBlank(gameId, "gameId");

    return apiFetch(`${GAMES_PATH}/${encodeURIComponent(gameId)}/answer`, isWhereInMiddleEarthAnswer, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ roundNumber, x: position.x, y: position.y }),
    });
}

export async function getResult(gameId: string): Promise<WhereInMiddleEarthResult> {
    requireNonBlank(gameId, "gameId");

    return apiFetch(`${GAMES_PATH}/${encodeURIComponent(gameId)}/result`, isWhereInMiddleEarthResult);
}
