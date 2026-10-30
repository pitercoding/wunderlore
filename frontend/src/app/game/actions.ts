"use server";

import { submitAnswer, type AnswerResult } from "@/lib/api/game";

// Follows GameRoundRegistry.answer, not the status class:
// - terminal: the round can no longer be answered (404 expired or unknown, 409 answered).
// - invalid: the backend rejected the answer before consuming the round (400).
// - temporary: anything else. The round may still be open, so answering again is safe:
//   if the failed attempt did reach the backend, the next one gets a 409.
export type GameAnswerFailureKind = "terminal" | "invalid" | "temporary";

export interface GameAnswerFailure {
    ok: false;
    kind: GameAnswerFailureKind;
    message: string;
}

export type GameAnswerResponse =
    | { ok: true; result: AnswerResult }
    | GameAnswerFailure;

export async function submitGameAnswer(
    roundId: string,
    characterId: string,
): Promise<GameAnswerResponse> {
    try {
        const result = await submitAnswer(roundId, characterId);

        return {
            ok: true as const,
            result,
        };
    } catch (error: unknown) {
        const status =
            typeof error === "object" &&
                error !== null &&
                "status" in error &&
                typeof error.status === "number"
                ? error.status
                : undefined;

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

        if (status === 503) {
            return {
                ok: false as const,
                kind: "temporary",
                message: "The game is temporarily unavailable. Please try again.",
            };
        }

        console.error("Failed to submit game answer", error);

        return {
            ok: false as const,
            kind: "temporary",
            message: "Something went wrong. Please try again.",
        };
    }
}
