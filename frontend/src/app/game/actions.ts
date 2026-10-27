"use server";

import { submitAnswer } from "@/lib/api/game";

export async function submitGameAnswer(
    roundId: string,
    characterId: string,
) {
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
                message: "Invalid answer. Please try again.",
            };
        }

        if (status === 404) {
            return {
                ok: false as const,
                message: "This round has expired. Start a new round.",
            };
        }

        if (status === 409) {
            return {
                ok: false as const,
                message: "This round has already been answered.",
            };
        }

        if (status === 503) {
            return {
                ok: false as const,
                message: "The game is temporarily unavailable. Please try again.",
            };
        }

        console.error("Failed to submit game answer", error);

        return {
            ok: false as const,
            message: "Something went wrong. Please try again.",
        };
    }
}
