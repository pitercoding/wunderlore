import { apiFetch } from "@/lib/api/client";
import { isNonBlankString, isRecord } from "@/lib/api/guards";

export interface WhoSaidItCharacterOption {
    id: string;
    name: string;
}

export interface WhoSaidItRound {
    roundId: string;
    quote: string;
    options: WhoSaidItCharacterOption[];
}

export interface WhoSaidItAnswerResult {
    roundId: string;
    submittedCharacterId: string;
    correct: boolean;
    correctCharacterId: string;
}

function isWhoSaidItCharacterOption(value: unknown): value is WhoSaidItCharacterOption {
    return (
        isRecord(value) &&
        isNonBlankString(value.id) &&
        isNonBlankString(value.name)
    );
}

// The number of options is not checked: only an empty list makes a round unplayable.
function isWhoSaidItRound(value: unknown): value is WhoSaidItRound {
    return (
        isRecord(value) &&
        isNonBlankString(value.roundId) &&
        isNonBlankString(value.quote) &&
        Array.isArray(value.options) &&
        value.options.length > 0 &&
        value.options.every(isWhoSaidItCharacterOption)
    );
}

function isWhoSaidItAnswerResult(value: unknown): value is WhoSaidItAnswerResult {
    return (
        isRecord(value) &&
        isNonBlankString(value.roundId) &&
        isNonBlankString(value.submittedCharacterId) &&
        typeof value.correct === "boolean" &&
        isNonBlankString(value.correctCharacterId)
    );
}

const ROUNDS_PATH = "/api/games/who-said-it/rounds";

// Creates server-side state, so it is a POST even though it sends nothing.
export async function createRound(): Promise<WhoSaidItRound> {
    return apiFetch(ROUNDS_PATH, isWhoSaidItRound, { method: "POST" });
}

export async function submitAnswer(
    roundId: string,
    characterId: string,
): Promise<WhoSaidItAnswerResult> {
    if (!roundId.trim()) {
        throw new Error("roundId must be a non-empty string");
    }

    if (!characterId.trim()) {
        throw new Error("characterId must be a non-empty string");
    }

    return apiFetch(
        `${ROUNDS_PATH}/${encodeURIComponent(roundId)}/answer`,
        isWhoSaidItAnswerResult,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ characterId }),
        },
    );
}
