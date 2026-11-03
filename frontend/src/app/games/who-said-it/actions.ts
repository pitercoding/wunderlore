"use server";

import {
    createRound,
    submitAnswer,
    type WhoSaidItAnswerResult,
    type WhoSaidItRound,
} from "@/lib/api/whoSaidIt";
import { unavailableMessage } from "@/lib/api/unavailable";

// Server Actions are public endpoints: their arguments come from the client and are checked
// here before anything reaches the backend. The limits follow the backend contract:
// - round ids are UUIDs, as issued by WhoSaidItRoundRegistry.
// - character ids are the upstream ids (24 hex characters in The One API); the backend accepts
//   at most 64 characters (WhoSaidItAnswerRequest), which also covers a UUID.
const ROUND_ID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_CHARACTER_ID_LENGTH = 64;

function isValidRoundId(value: unknown): value is string {
    return typeof value === "string" && ROUND_ID_PATTERN.test(value);
}

function isValidCharacterId(value: unknown): value is string {
    return (
        typeof value === "string" &&
        value.trim() !== "" &&
        value.length <= MAX_CHARACTER_ID_LENGTH
    );
}

const INVALID_OPTION_MESSAGE = "That isn't a valid option for this round.";

function statusOf(error: unknown): number | undefined {
    return typeof error === "object" &&
        error !== null &&
        "status" in error &&
        typeof error.status === "number"
        ? error.status
        : undefined;
}

// Creating a round has one failure kind: nothing was started, so asking again is always safe.
export type WhoSaidItCreateResponse =
    | { ok: true; round: WhoSaidItRound }
    | { ok: false; message: string };

// The only way the game starts a round: the page itself never does, so rendering or
// reloading it creates no backend state.
export async function createWhoSaidItRound(): Promise<WhoSaidItCreateResponse> {
    try {
        const round = await createRound();

        return {
            ok: true as const,
            round,
        };
    } catch (error: unknown) {
        const unavailable = unavailableMessage(error);

        // A 503 is expected while the service recovers, so only the other failures are logged.
        if (statusOf(error) !== 503) {
            console.error("Failed to create Who Said It round", error);
        }

        return {
            ok: false as const,
            message: unavailable ?? "Unable to load a game round. Please try again.",
        };
    }
}

// Follows WhoSaidItRoundRegistry.answer, not the status class:
// - terminal: the round can no longer be answered (404 expired or unknown, 409 answered).
// - invalid: the answer was rejected before consuming the round: by the backend (400) or by
//   the checks above, which never call the backend.
// - temporary: anything else. The round may still be open, so answering again is safe:
//   if the failed attempt did reach the backend, the next one gets a 409.
export type WhoSaidItAnswerFailureKind = "terminal" | "invalid" | "temporary";

export interface WhoSaidItAnswerFailure {
    ok: false;
    kind: WhoSaidItAnswerFailureKind;
    message: string;
}

export type WhoSaidItAnswerResponse =
    | { ok: true; result: WhoSaidItAnswerResult }
    | WhoSaidItAnswerFailure;

// The arguments are unknown, not string: nothing guarantees what a caller sent. Malformed ones
// are an expected, quiet outcome, not a server error, so they are neither logged nor forwarded.
export async function submitWhoSaidItAnswer(
    roundId: unknown,
    characterId: unknown,
): Promise<WhoSaidItAnswerResponse> {
    if (!isValidRoundId(roundId) || !isValidCharacterId(characterId)) {
        return {
            ok: false as const,
            kind: "invalid",
            message: INVALID_OPTION_MESSAGE,
        };
    }

    try {
        const result = await submitAnswer(roundId, characterId);

        return {
            ok: true as const,
            result,
        };
    } catch (error: unknown) {
        const status = statusOf(error);

        if (status === 400) {
            return {
                ok: false as const,
                kind: "invalid",
                message: INVALID_OPTION_MESSAGE,
            };
        }

        if (status === 404) {
            return {
                ok: false as const,
                kind: "terminal",
                message: "This round has expired or is no longer available.",
            };
        }

        if (status === 409) {
            return {
                ok: false as const,
                kind: "terminal",
                message: "This round has already been answered.",
            };
        }

        const unavailable = unavailableMessage(error);

        // A 503 is expected while the service recovers, so only the other failures are logged.
        if (status !== 503) {
            console.error("Failed to submit Who Said It answer", error);
        }

        return {
            ok: false as const,
            kind: "temporary",
            message: unavailable ?? "Something went wrong. Please try again.",
        };
    }
}
