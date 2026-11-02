"use server";

import {
    submitAnswers,
    type WhichCharacterAnswer,
    type WhichCharacterProfile,
} from "@/lib/api/whichCharacter";
import { unavailableMessage } from "@/lib/api/unavailable";

const QUESTION_COUNT = 10;

// - invalid: the answers cannot be accepted as they are (rejected locally or with a 400).
// - temporary: anything else. Nothing was consumed, so submitting again is safe.
export type WhichCharacterSubmitFailureKind = "invalid" | "temporary";

export interface WhichCharacterSubmitFailure {
    ok: false;
    kind: WhichCharacterSubmitFailureKind;
    message: string;
}

export type WhichCharacterSubmitResponse =
    | { ok: true; character: WhichCharacterProfile }
    | WhichCharacterSubmitFailure;

const INVALID_ANSWERS: WhichCharacterSubmitFailure = {
    ok: false,
    kind: "invalid",
    message: "Please answer all ten questions before finishing.",
};

function isNonBlank(value: unknown): value is string {
    return typeof value === "string" && value.trim().length > 0;
}

// Server Actions receive client input, so the shape is checked again here. Only the
// two known fields are copied: nothing else the client sent is forwarded.
function toPayload(input: unknown): WhichCharacterAnswer[] | undefined {
    if (!Array.isArray(input) || input.length !== QUESTION_COUNT) {
        return undefined;
    }

    const payload: WhichCharacterAnswer[] = [];
    const questionIds = new Set<string>();

    for (const item of input as unknown[]) {
        if (typeof item !== "object" || item === null) {
            return undefined;
        }

        const { questionId, answerId } = item as Record<string, unknown>;

        if (!isNonBlank(questionId) || !isNonBlank(answerId)) {
            return undefined;
        }

        if (questionIds.has(questionId)) {
            return undefined;
        }

        questionIds.add(questionId);
        payload.push({ questionId, answerId });
    }

    return payload;
}

export async function submitWhichCharacterAnswers(
    answers: WhichCharacterAnswer[],
): Promise<WhichCharacterSubmitResponse> {
    const payload = toPayload(answers);

    if (!payload) {
        return INVALID_ANSWERS;
    }

    try {
        const { character } = await submitAnswers(payload);

        return {
            ok: true as const,
            character: {
                id: character.id,
                name: character.name,
                description: character.description,
            },
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
                message: "Some answers could not be accepted. Please review them and try again.",
            };
        }

        const unavailable = unavailableMessage(error);

        // A 503 is expected while the service recovers, so only the other failures are logged.
        if (status !== 503) {
            console.error("Failed to submit Which Character Are You answers", error);
        }

        return {
            ok: false as const,
            kind: "temporary",
            message: unavailable ?? "Something went wrong. Please try again.",
        };
    }
}
