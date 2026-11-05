import type { WhoAmIDifficulty } from "@/lib/api/whoAmI";

// What each clue is worth, for the explanation on the menu. Display only: the backend decides
// the points of every answer.
export const CLUE_POINTS = [100, 80, 60, 40, 20] as const;

export const DIFFICULTY_CHOICES: {
    value: WhoAmIDifficulty;
    label: string;
    hint: string;
}[] = [
    { value: "EASY", label: "Easy", hint: "Well-known figures of the tales" },
    { value: "MEDIUM", label: "Medium", hint: "For attentive readers" },
    { value: "HARD", label: "Hard", hint: "Deep in the legendarium" },
];

export function difficultyLabel(value: WhoAmIDifficulty): string {
    return DIFFICULTY_CHOICES.find((choice) => choice.value === value)?.label ?? value;
}

// How a round ended, for the summary: the clue that gave it away, or that none did.
export function cluesUsedLabel(cluesUsed: number, identified: boolean): string {
    return identified
        ? `Identified at clue ${cluesUsed} of 5`
        : `Not identified after ${cluesUsed} clues`;
}
