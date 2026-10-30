import { apiFetch } from "@/lib/api/client";
import { isNonBlankString, isRecord } from "@/lib/api/guards";

export interface CharacterOption {
    id: string;
    name: string;
}

export interface GameRound {
    roundId: string;
    quote: string;
    options: CharacterOption[];
}

export interface AnswerResult {
    roundId: string;
    submittedCharacterId: string;
    correct: boolean;
    correctCharacterId: string;
}

function isCharacterOption(value: unknown): value is CharacterOption {
    return (
        isRecord(value) &&
        isNonBlankString(value.id) &&
        isNonBlankString(value.name)
    );
}

// The number of options is not checked: only an empty list makes a round unplayable.
function isGameRound(value: unknown): value is GameRound {
    return (
        isRecord(value) &&
        isNonBlankString(value.roundId) &&
        isNonBlankString(value.quote) &&
        Array.isArray(value.options) &&
        value.options.length > 0 &&
        value.options.every(isCharacterOption)
    );
}

function isAnswerResult(value: unknown): value is AnswerResult {
    return (
        isRecord(value) &&
        isNonBlankString(value.roundId) &&
        isNonBlankString(value.submittedCharacterId) &&
        typeof value.correct === "boolean" &&
        isNonBlankString(value.correctCharacterId)
    );
}

export async function getRound(): Promise<GameRound> {
    return apiFetch("/api/game/round", isGameRound);
}

export async function submitAnswer(
    roundId: string,
    characterId: string,
): Promise<AnswerResult> {
    if (!roundId.trim()) {
        throw new Error("roundId must be a non-empty string");
    }

    if (!characterId.trim()) {
        throw new Error("characterId must be a non-empty string");
    }

    return apiFetch("/api/game/answer", isAnswerResult, {
        method: "POST",
        headers: { "Content-Type": "application/json",},
        body: JSON.stringify({roundId, characterId,}),
    });
}
