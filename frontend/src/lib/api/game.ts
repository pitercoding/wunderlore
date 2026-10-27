import { apiFetch } from "@/lib/api/client";

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

export async function getRound(): Promise<GameRound> {
    return apiFetch<GameRound>("/api/game/round");
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

    return apiFetch<AnswerResult>("/api/game/answer", {
        method: "POST",
        headers: { "Content-Type": "application/json",},
        body: JSON.stringify({roundId, characterId,}),
    });
}
