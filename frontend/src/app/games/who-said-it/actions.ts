"use server";

import {
    createRound,
    submitAnswer,
    type WhoSaidItAnswerResult,
    type WhoSaidItRound,
} from "@/lib/api/whoSaidIt";
import { unavailableMessage } from "@/lib/api/unavailable";

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
// - invalid: the backend rejected the answer before consuming the round (400).
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

export async function submitWhoSaidItAnswer(
    roundId: string,
    characterId: string,
): Promise<WhoSaidItAnswerResponse> {
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
                message: "That isn't a valid option for this round.",
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
