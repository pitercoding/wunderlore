import type {
    LoreChallengeCategoryChoice,
    LoreChallengeDifficulty,
} from "@/lib/api/loreChallenge";

export const CATEGORY_CHOICES: {
    value: LoreChallengeCategoryChoice;
    label: string;
    hint: string;
}[] = [
    { value: "ALL", label: "All categories", hint: "A mix of everything" },
    { value: "CHARACTERS", label: "Characters", hint: "Heroes, villains and kings" },
    { value: "PLACES", label: "Places", hint: "Realms, forests and fortresses" },
    { value: "PEOPLES", label: "Peoples", hint: "Elves, Dwarves, Men and more" },
    { value: "HISTORY", label: "History", hint: "The Ages of Middle-earth" },
    { value: "EVENTS", label: "Events", hint: "Battles, quests and turning points" },
];

export const DIFFICULTY_CHOICES: {
    value: LoreChallengeDifficulty;
    label: string;
    hint: string;
    points: number;
}[] = [
    { value: "EASY", label: "Easy", hint: "Well-known tales", points: 10 },
    { value: "MEDIUM", label: "Medium", hint: "For attentive readers", points: 20 },
    { value: "HARD", label: "Hard", hint: "Deep in the appendices", points: 30 },
];

export function categoryLabel(value: LoreChallengeCategoryChoice): string {
    return CATEGORY_CHOICES.find((choice) => choice.value === value)?.label ?? value;
}

export function difficultyLabel(value: LoreChallengeDifficulty): string {
    return DIFFICULTY_CHOICES.find((choice) => choice.value === value)?.label ?? value;
}

// "1 min 35 s", "42 s": the match time as a player reads it.
export function formatDuration(totalSeconds: number): string {
    const minutes = Math.floor(totalSeconds / 60);
    const seconds = totalSeconds % 60;

    return minutes > 0 ? `${minutes} min ${seconds} s` : `${seconds} s`;
}
