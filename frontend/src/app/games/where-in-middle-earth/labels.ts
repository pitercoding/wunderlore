import type {
    WhereInMiddleEarthAccuracy,
    WhereInMiddleEarthDifficulty,
} from "@/lib/api/whereInMiddleEarth";

export const DIFFICULTY_CHOICES: {
    value: WhereInMiddleEarthDifficulty;
    label: string;
    hint: string;
}[] = [
    { value: "EASY", label: "Easy", hint: "Famous places of the tales" },
    { value: "MEDIUM", label: "Medium", hint: "For attentive readers" },
    { value: "HARD", label: "Hard", hint: "Deep in the geography" },
];

export function difficultyLabel(value: WhereInMiddleEarthDifficulty): string {
    return DIFFICULTY_CHOICES.find((choice) => choice.value === value)?.label ?? value;
}

// How close a click was. A word and a symbol each, so that a result never depends on color alone.
export const ACCURACY_LABELS: Record<
    WhereInMiddleEarthAccuracy,
    { label: string; symbol: string }
> = {
    EXCELLENT: { label: "Excellent", symbol: "◎" },
    VERY_GOOD: { label: "Very good", symbol: "●" },
    GOOD: { label: "Good", symbol: "◐" },
    CLOSE: { label: "Close", symbol: "○" },
    FAR: { label: "Far away", symbol: "✕" },
};

// What each band is worth, for the explanation on the menu. Display only: the backend decides the
// distance, the band and the points of every answer (see contracts/where-in-middle-earth).
export const ACCURACY_BANDS: {
    accuracy: WhereInMiddleEarthAccuracy;
    range: string;
    points: number;
}[] = [
    { accuracy: "EXCELLENT", range: "within 15 units", points: 100 },
    { accuracy: "VERY_GOOD", range: "within 30 units", points: 80 },
    { accuracy: "GOOD", range: "within 55 units", points: 60 },
    { accuracy: "CLOSE", range: "within 90 units", points: 40 },
    { accuracy: "FAR", range: "further away", points: 0 },
];

// Distances are in map units: the map is 1000 units wide and 700 high.
export function formatDistance(distance: number): string {
    const text = Number.isInteger(distance) ? String(distance) : distance.toFixed(1);

    return `${text} ${distance === 1 ? "unit" : "units"}`;
}
