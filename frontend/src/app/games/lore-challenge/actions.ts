"use server";

import { ApiError } from "@/lib/api/client";
import {
    createMatch,
    getCurrentQuestion,
    getResult,
    submitAnswer,
    LORE_CHALLENGE_CATEGORIES,
    LORE_CHALLENGE_DIFFICULTIES,
    type LoreChallengeAnswer,
    type LoreChallengeCategoryChoice,
    type LoreChallengeDifficulty,
    type LoreChallengeQuestion,
    type LoreChallengeResult,
} from "@/lib/api/loreChallenge";
import { formatWait, unavailableMessage } from "@/lib/api/unavailable";

// Server Actions are public endpoints: their arguments come from the client and are checked
// here before anything reaches the backend. The limits follow the backend contract:
// - match ids are UUIDs, as issued by LoreChallengeMatchRegistry.
// - question ids are lowercase slugs of at most 64 characters (LoreChallengeQuestion).
// - option ids are a single letter, "a" to "d".
const MATCH_ID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const QUESTION_ID_PATTERN = /^[a-z0-9-]{1,64}$/;
const OPTION_ID_PATTERN = /^[a-d]$/;

function isValidMatchId(value: unknown): value is string {
    return typeof value === "string" && MATCH_ID_PATTERN.test(value);
}

function isValidQuestionId(value: unknown): value is string {
    return typeof value === "string" && QUESTION_ID_PATTERN.test(value);
}

function isValidOptionId(value: unknown): value is string {
    return typeof value === "string" && OPTION_ID_PATTERN.test(value);
}

function isValidCategory(value: unknown): value is LoreChallengeCategoryChoice {
    return (
        value === "ALL" ||
        (LORE_CHALLENGE_CATEGORIES as readonly unknown[]).includes(value)
    );
}

function isValidDifficulty(value: unknown): value is LoreChallengeDifficulty {
    return (LORE_CHALLENGE_DIFFICULTIES as readonly unknown[]).includes(value);
}

function statusOf(error: unknown): number | undefined {
    return typeof error === "object" &&
        error !== null &&
        "status" in error &&
        typeof error.status === "number"
        ? error.status
        : undefined;
}

// - terminal: the match is gone (404: expired, evicted after a restart, or unknown).
// - stale: the client and the server disagree about where the match is (409): the answer was
//   already taken, the match is over, or the result was asked for too early. Asking the
//   server where the match stands resolves it.
// - invalid: rejected before it could change anything, by these checks or by the backend (400).
// - temporary: anything else. Nothing is known to have changed, so trying again is safe: if a
//   failed answer did reach the backend, the retry is a 409 and the game resynchronizes.
export type LoreChallengeFailureKind = "terminal" | "stale" | "invalid" | "temporary";

export interface LoreChallengeFailure {
    ok: false;
    kind: LoreChallengeFailureKind;
    message: string;
}

export type LoreChallengeQuestionResponse =
    | { ok: true; question: LoreChallengeQuestion }
    | LoreChallengeFailure;

export type LoreChallengeAnswerResponse =
    | { ok: true; answer: LoreChallengeAnswer }
    | LoreChallengeFailure;

export type LoreChallengeResultResponse =
    | { ok: true; result: LoreChallengeResult }
    | LoreChallengeFailure;

const INVALID_MESSAGE = "That request isn't valid. Please try again.";
const TERMINAL_MESSAGE = "This match has expired or is no longer available.";
const STALE_MESSAGE = "This match has moved on without you. Let's pick up where it stands.";

function failure(kind: LoreChallengeFailureKind, message: string): LoreChallengeFailure {
    return { ok: false, kind, message };
}

function invalid(): LoreChallengeFailure {
    return failure("invalid", INVALID_MESSAGE);
}

// The backend limits how many matches all players together may start per minute (429). Not a
// failure to log: it is expected under load. The wait is named only when Retry-After is usable.
function tooManyMatchesMessage(error: ApiError): string {
    const wait = error.retryAfterSeconds;

    if (wait !== undefined && Number.isInteger(wait) && wait > 0) {
        return `Too many matches were started in a short time. Please try again in about ${formatWait(wait)}.`;
    }

    return "Too many matches were started in a short time. Please try again in a moment.";
}

// Maps what went wrong with a request on an existing match. A 503 is expected while the service
// recovers, so only the other unexpected failures are logged.
function matchFailure(error: unknown, action: string): LoreChallengeFailure {
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

// The only way a match starts: the page itself never does, so rendering or reloading it creates
// no backend state. Starting has one failure kind: nothing was started, so asking again is safe.
export async function startLoreChallengeMatch(
    category: unknown,
    difficulty: unknown,
): Promise<LoreChallengeQuestionResponse> {
    if (!isValidCategory(category) || !isValidDifficulty(difficulty)) {
        return invalid();
    }

    try {
        return { ok: true as const, question: await createMatch(category, difficulty) };
    } catch (error: unknown) {
        const status = statusOf(error);

        if (error instanceof ApiError && status === 429) {
            return failure("temporary", tooManyMatchesMessage(error));
        }

        if (status === 422) {
            return failure(
                "temporary",
                "There aren't enough questions for that selection yet. Try another category or difficulty.",
            );
        }

        if (status !== 503) {
            console.error("Failed to start Lore Challenge match", error);
        }

        return failure(
            "temporary",
            unavailableMessage(error) ?? "Unable to start a match. Please try again.",
        );
    }
}

export async function fetchLoreChallengeQuestion(
    matchId: unknown,
): Promise<LoreChallengeQuestionResponse> {
    if (!isValidMatchId(matchId)) {
        return invalid();
    }

    try {
        return { ok: true as const, question: await getCurrentQuestion(matchId) };
    } catch (error: unknown) {
        return matchFailure(error, "load Lore Challenge question");
    }
}

export async function submitLoreChallengeAnswer(
    matchId: unknown,
    questionId: unknown,
    optionId: unknown,
): Promise<LoreChallengeAnswerResponse> {
    if (!isValidMatchId(matchId) || !isValidQuestionId(questionId) || !isValidOptionId(optionId)) {
        return invalid();
    }

    try {
        return { ok: true as const, answer: await submitAnswer(matchId, questionId, optionId) };
    } catch (error: unknown) {
        return matchFailure(error, "submit Lore Challenge answer");
    }
}

export async function fetchLoreChallengeResult(
    matchId: unknown,
): Promise<LoreChallengeResultResponse> {
    if (!isValidMatchId(matchId)) {
        return invalid();
    }

    try {
        return { ok: true as const, result: await getResult(matchId) };
    } catch (error: unknown) {
        return matchFailure(error, "load Lore Challenge result");
    }
}
