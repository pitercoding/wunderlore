"use server";

import { ApiError } from "@/lib/api/client";
import {
    createGame,
    getCurrentRound,
    getResult,
    submitAnswer,
    WHO_AM_I_DIFFICULTIES,
    WHO_AM_I_TOTAL_CLUES,
    type WhoAmIAnswer,
    type WhoAmIDifficulty,
    type WhoAmIResult,
    type WhoAmIRound,
} from "@/lib/api/whoAmI";
import { formatWait, unavailableMessage } from "@/lib/api/unavailable";

// Server Actions are public endpoints: their arguments come from the client and are checked
// here before anything reaches the backend. The limits follow the backend contract:
// - game ids are UUIDs, as issued by WhoAmIGameRegistry.
// - option ids are a single letter, "a" to "d".
// - rounds are 1 to 10 and clues 1 to 5.
const GAME_ID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const OPTION_ID_PATTERN = /^[a-d]$/;
const MAX_ROUND = 10;

function isValidGameId(value: unknown): value is string {
    return typeof value === "string" && GAME_ID_PATTERN.test(value);
}

function isValidOptionId(value: unknown): value is string {
    return typeof value === "string" && OPTION_ID_PATTERN.test(value);
}

function isIntegerBetween(value: unknown, min: number, max: number): value is number {
    return typeof value === "number" && Number.isInteger(value) && value >= min && value <= max;
}

function isValidDifficulty(value: unknown): value is WhoAmIDifficulty {
    return (WHO_AM_I_DIFFICULTIES as readonly unknown[]).includes(value);
}

function statusOf(error: unknown): number | undefined {
    return typeof error === "object" &&
        error !== null &&
        "status" in error &&
        typeof error.status === "number"
        ? error.status
        : undefined;
}

// - terminal: the game is gone (404: expired, evicted after a restart, or unknown).
// - stale: the client and the server disagree about where the game is (409): the clue was
//   already answered, or the game is over. Asking the server where the game stands resolves it.
// - invalid: rejected before it could change anything, by these checks or by the backend (400).
// - temporary: anything else. Nothing is known to have changed, so trying again is safe: if a
//   failed answer did reach the backend, the retry is a 409 and the game resynchronizes.
export type WhoAmIFailureKind = "terminal" | "stale" | "invalid" | "temporary";

export interface WhoAmIFailure {
    ok: false;
    kind: WhoAmIFailureKind;
    message: string;
}

export type WhoAmIRoundResponse = { ok: true; round: WhoAmIRound } | WhoAmIFailure;

export type WhoAmIAnswerResponse = { ok: true; answer: WhoAmIAnswer } | WhoAmIFailure;

export type WhoAmIResultResponse = { ok: true; result: WhoAmIResult } | WhoAmIFailure;

const INVALID_MESSAGE = "That request isn't valid. Please try again.";
const TERMINAL_MESSAGE = "This game has expired or is no longer available.";
const STALE_MESSAGE = "This game has moved on without you. Let's pick up where it stands.";

function failure(kind: WhoAmIFailureKind, message: string): WhoAmIFailure {
    return { ok: false, kind, message };
}

function invalid(): WhoAmIFailure {
    return failure("invalid", INVALID_MESSAGE);
}

// The backend limits how many games all players together may start per minute (429). Not a
// failure to log: it is expected under load. The wait is named only when Retry-After is usable.
function tooManyGamesMessage(error: ApiError): string {
    const wait = error.retryAfterSeconds;

    if (wait !== undefined && Number.isInteger(wait) && wait > 0) {
        return `Too many games were started in a short time. Please try again in about ${formatWait(wait)}.`;
    }

    return "Too many games were started in a short time. Please try again in a moment.";
}

// Maps what went wrong with a request on an existing game. A 503 is expected while the service
// recovers, so only the other unexpected failures are logged.
function gameFailure(error: unknown, action: string): WhoAmIFailure {
    const status = statusOf(error);

    if (status === 400) {
        return invalid();
    }

    if (status === 404) {
        return failure("terminal", TERMINAL_MESSAGE);
    }

    if (status === 409) {
        return failure("stale", STALE_MESSAGE);
    }

    if (status !== 503) {
        console.error(`Failed to ${action}`, error);
    }

    return failure(
        "temporary",
        unavailableMessage(error) ?? "Something went wrong. Please try again.",
    );
}

// The only way a game starts: the page itself never does, so rendering or reloading it creates
// no backend state. Starting has one failure kind: nothing was started, so asking again is safe.
export async function startWhoAmIGame(difficulty: unknown): Promise<WhoAmIRoundResponse> {
    if (!isValidDifficulty(difficulty)) {
        return invalid();
    }

    try {
        return { ok: true as const, round: await createGame(difficulty) };
    } catch (error: unknown) {
        const status = statusOf(error);

        if (error instanceof ApiError && status === 429) {
            return failure("temporary", tooManyGamesMessage(error));
        }

        if (status !== 503) {
            console.error("Failed to start Who Am I? game", error);
        }

        return failure(
            "temporary",
            unavailableMessage(error) ?? "Unable to start a game. Please try again.",
        );
    }
}

export async function fetchWhoAmIRound(gameId: unknown): Promise<WhoAmIRoundResponse> {
    if (!isValidGameId(gameId)) {
        return invalid();
    }

    try {
        return { ok: true as const, round: await getCurrentRound(gameId) };
    } catch (error: unknown) {
        return gameFailure(error, "load Who Am I? round");
    }
}

// Only the position (round and clue) and the chosen option go to the backend. What the answer
// was worth, and whether it was right, is decided there.
export async function submitWhoAmIAnswer(
    gameId: unknown,
    roundNumber: unknown,
    clueNumber: unknown,
    optionId: unknown,
): Promise<WhoAmIAnswerResponse> {
    if (
        !isValidGameId(gameId) ||
        !isIntegerBetween(roundNumber, 1, MAX_ROUND) ||
        !isIntegerBetween(clueNumber, 1, WHO_AM_I_TOTAL_CLUES) ||
        !isValidOptionId(optionId)
    ) {
        return invalid();
    }

    try {
        return {
            ok: true as const,
            answer: await submitAnswer(gameId, roundNumber, clueNumber, optionId),
        };
    } catch (error: unknown) {
        return gameFailure(error, "submit Who Am I? answer");
    }
}

export async function fetchWhoAmIResult(gameId: unknown): Promise<WhoAmIResultResponse> {
    if (!isValidGameId(gameId)) {
        return invalid();
    }

    try {
        return { ok: true as const, result: await getResult(gameId) };
    } catch (error: unknown) {
        return gameFailure(error, "load Who Am I? result");
    }
}
