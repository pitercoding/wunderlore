import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isIntegerFrom, isNonBlankString, isRecord } from "@/lib/api/guards";

export const LORE_CHALLENGE_CATEGORIES = [
    "CHARACTERS",
    "PLACES",
    "PEOPLES",
    "HISTORY",
    "EVENTS",
] as const;

export const LORE_CHALLENGE_DIFFICULTIES = ["EASY", "MEDIUM", "HARD"] as const;

export type LoreChallengeCategory = (typeof LORE_CHALLENGE_CATEGORIES)[number];
export type LoreChallengeCategoryChoice = "ALL" | LoreChallengeCategory;
export type LoreChallengeDifficulty = (typeof LORE_CHALLENGE_DIFFICULTIES)[number];

export interface LoreChallengeOption {
    id: string;
    text: string;
}

// A question waiting for an answer. It never carries the correct option.
export interface LoreChallengeQuestion {
    matchId: string;
    category: LoreChallengeCategoryChoice;
    difficulty: LoreChallengeDifficulty;
    number: number;
    total: number;
    questionId: string;
    questionCategory: LoreChallengeCategory;
    prompt: string;
    options: LoreChallengeOption[];
}

export interface LoreChallengeAnswer {
    matchId: string;
    questionId: string;
    submittedOptionId: string;
    correct: boolean;
    correctOptionId: string;
    explanation: string;
    pointsAwarded: number;
    score: number;
    answered: number;
    total: number;
    finished: boolean;
}

export interface LoreChallengeResult {
    matchId: string;
    category: LoreChallengeCategoryChoice;
    difficulty: LoreChallengeDifficulty;
    totalQuestions: number;
    correctAnswers: number;
    incorrectAnswers: number;
    score: number;
    maxScore: number;
    percentage: number;
    durationSeconds: number;
    rank: string;
}

function isOneOf<T extends string>(values: readonly T[], value: unknown): value is T {
    return typeof value === "string" && (values as readonly string[]).includes(value);
}

function isCategory(value: unknown): value is LoreChallengeCategory {
    return isOneOf(LORE_CHALLENGE_CATEGORIES, value);
}

function isCategoryChoice(value: unknown): value is LoreChallengeCategoryChoice {
    return value === "ALL" || isCategory(value);
}

function isDifficulty(value: unknown): value is LoreChallengeDifficulty {
    return isOneOf(LORE_CHALLENGE_DIFFICULTIES, value);
}

function isOption(value: unknown): value is LoreChallengeOption {
    return isRecord(value) && isNonBlankString(value.id) && isNonBlankString(value.text);
}

// Four options are expected, but only an empty list makes a question unplayable.
function isLoreChallengeQuestion(value: unknown): value is LoreChallengeQuestion {
    return (
        isRecord(value) &&
        isNonBlankString(value.matchId) &&
        isCategoryChoice(value.category) &&
        isDifficulty(value.difficulty) &&
        isIntegerFrom(1, value.number) &&
        isIntegerFrom(1, value.total) &&
        isNonBlankString(value.questionId) &&
        isCategory(value.questionCategory) &&
        isNonBlankString(value.prompt) &&
        Array.isArray(value.options) &&
        value.options.length > 0 &&
        value.options.every(isOption)
    );
}

function isLoreChallengeAnswer(value: unknown): value is LoreChallengeAnswer {
    return (
        isRecord(value) &&
        isNonBlankString(value.matchId) &&
        isNonBlankString(value.questionId) &&
        isNonBlankString(value.submittedOptionId) &&
        typeof value.correct === "boolean" &&
        isNonBlankString(value.correctOptionId) &&
        isNonBlankString(value.explanation) &&
        isIntegerFrom(0, value.pointsAwarded) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(1, value.answered) &&
        isIntegerFrom(1, value.total) &&
        typeof value.finished === "boolean"
    );
}

function isLoreChallengeResult(value: unknown): value is LoreChallengeResult {
    return (
        isRecord(value) &&
        isNonBlankString(value.matchId) &&
        isCategoryChoice(value.category) &&
        isDifficulty(value.difficulty) &&
        isIntegerFrom(1, value.totalQuestions) &&
        isIntegerFrom(0, value.correctAnswers) &&
        isIntegerFrom(0, value.incorrectAnswers) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(0, value.maxScore) &&
        isIntegerFrom(0, value.percentage) &&
        value.percentage <= 100 &&
        isIntegerFrom(0, value.durationSeconds) &&
        isNonBlankString(value.rank)
    );
}

const MATCHES_PATH = "/api/games/lore-challenge/matches";

function requireNonBlank(value: string, name: string): void {
    if (!value.trim()) {
        throw new Error(`${name} must be a non-empty string`);
    }
}

// Creates server-side state, so it is a POST. The first match of a visit may wake the backend;
// an unused match costs nothing.
export async function createMatch(
    category: LoreChallengeCategoryChoice,
    difficulty: LoreChallengeDifficulty,
): Promise<LoreChallengeQuestion> {
    return apiFetch(
        MATCHES_PATH,
        isLoreChallengeQuestion,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ category, difficulty }),
        },
        { timeoutMs: COLD_START_TIMEOUT_MS },
    );
}

// The question the match is waiting on: the next one, once the previous was answered.
export async function getCurrentQuestion(matchId: string): Promise<LoreChallengeQuestion> {
    requireNonBlank(matchId, "matchId");

    return apiFetch(
        `${MATCHES_PATH}/${encodeURIComponent(matchId)}/question`,
        isLoreChallengeQuestion,
    );
}

export async function submitAnswer(
    matchId: string,
    questionId: string,
    optionId: string,
): Promise<LoreChallengeAnswer> {
    requireNonBlank(matchId, "matchId");
    requireNonBlank(questionId, "questionId");
    requireNonBlank(optionId, "optionId");

    return apiFetch(
        `${MATCHES_PATH}/${encodeURIComponent(matchId)}/answer`,
        isLoreChallengeAnswer,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ questionId, optionId }),
        },
    );
}

export async function getResult(matchId: string): Promise<LoreChallengeResult> {
    requireNonBlank(matchId, "matchId");

    return apiFetch(
        `${MATCHES_PATH}/${encodeURIComponent(matchId)}/result`,
        isLoreChallengeResult,
    );
}
