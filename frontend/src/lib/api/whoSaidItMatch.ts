import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isIntegerFrom, isNonBlankString, isRecord } from "@/lib/api/guards";

// The competitive Who Said It? match: ten quotes whose order, answers and score belong to the
// backend. The single-round game lives in whoSaidIt.ts and is unchanged.

export interface WhoSaidItMatchOption {
    id: string;
    name: string;
}

// The question waiting for an answer. It never says which option is right.
export interface WhoSaidItMatchQuestion {
    matchId: string;
    questionNumber: number;
    totalQuestions: number;
    quote: string;
    options: WhoSaidItMatchOption[];
}

export interface WhoSaidItMatchQuestionSummary {
    questionNumber: number;
    quote: string;
    submittedCharacterName: string;
    correctCharacterName: string;
    correct: boolean;
}

// Only exists once the match is finished; every figure is the backend's.
export interface WhoSaidItMatchResult {
    matchId: string;
    totalQuestions: number;
    correctAnswers: number;
    incorrectAnswers: number;
    score: number;
    maxScore: number;
    percentage: number;
    questions: WhoSaidItMatchQuestionSummary[];
}

// Exactly one of next and result is set: next while the match goes on, result after the last answer.
export interface WhoSaidItMatchAnswer {
    matchId: string;
    questionNumber: number;
    submittedCharacterId: string;
    matchFinished: boolean;
    next: WhoSaidItMatchQuestion | null;
    result: WhoSaidItMatchResult | null;
}

const OPTIONS_PER_QUESTION = 4;

function isOption(value: unknown): value is WhoSaidItMatchOption {
    return isRecord(value) && isNonBlankString(value.id) && isNonBlankString(value.name);
}

function isQuestion(value: unknown): value is WhoSaidItMatchQuestion {
    return (
        isRecord(value) &&
        isNonBlankString(value.matchId) &&
        isIntegerFrom(1, value.questionNumber) &&
        isIntegerFrom(1, value.totalQuestions) &&
        value.questionNumber <= value.totalQuestions &&
        isNonBlankString(value.quote) &&
        Array.isArray(value.options) &&
        value.options.length === OPTIONS_PER_QUESTION &&
        value.options.every(isOption) &&
        new Set(value.options.map((option) => option.id)).size === OPTIONS_PER_QUESTION
    );
}

function isQuestionSummary(value: unknown): value is WhoSaidItMatchQuestionSummary {
    return (
        isRecord(value) &&
        isIntegerFrom(1, value.questionNumber) &&
        isNonBlankString(value.quote) &&
        isNonBlankString(value.submittedCharacterName) &&
        isNonBlankString(value.correctCharacterName) &&
        typeof value.correct === "boolean"
    );
}

function isResult(value: unknown): value is WhoSaidItMatchResult {
    return (
        isRecord(value) &&
        isNonBlankString(value.matchId) &&
        isIntegerFrom(1, value.totalQuestions) &&
        isIntegerFrom(0, value.correctAnswers) &&
        isIntegerFrom(0, value.incorrectAnswers) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(1, value.maxScore) &&
        value.score <= value.maxScore &&
        isIntegerFrom(0, value.percentage) &&
        value.percentage <= 100 &&
        Array.isArray(value.questions) &&
        value.questions.every(isQuestionSummary)
    );
}

function isAnswer(value: unknown): value is WhoSaidItMatchAnswer {
    return (
        isRecord(value) &&
        isNonBlankString(value.matchId) &&
        isIntegerFrom(1, value.questionNumber) &&
        isNonBlankString(value.submittedCharacterId) &&
        typeof value.matchFinished === "boolean" &&
        (value.matchFinished
            ? value.next === null && isResult(value.result)
            : isQuestion(value.next) && value.result === null)
    );
}

const MATCHES_PATH = "/api/games/who-said-it/matches";

function requireNonBlank(value: string, name: string): void {
    if (!value.trim()) {
        throw new Error(`${name} must be a non-empty string`);
    }
}

// Creates server-side state, so it is a POST even though it sends nothing.
export async function createMatch(): Promise<WhoSaidItMatchQuestion> {
    // The first request of a visit may wake the backend; an unused match costs nothing.
    return apiFetch(
        MATCHES_PATH,
        isQuestion,
        { method: "POST" },
        { timeoutMs: COLD_START_TIMEOUT_MS },
    );
}

export async function getMatchQuestion(matchId: string): Promise<WhoSaidItMatchQuestion> {
    requireNonBlank(matchId, "matchId");

    return apiFetch(`${MATCHES_PATH}/${encodeURIComponent(matchId)}/question`, isQuestion);
}

// Only the question number and the chosen character are sent: the backend decides what is right.
export async function submitMatchAnswer(
    matchId: string,
    questionNumber: number,
    characterId: string,
): Promise<WhoSaidItMatchAnswer> {
    requireNonBlank(matchId, "matchId");
    requireNonBlank(characterId, "characterId");

    return apiFetch(`${MATCHES_PATH}/${encodeURIComponent(matchId)}/answer`, isAnswer, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ questionNumber, characterId }),
    });
}

export async function getMatchResult(matchId: string): Promise<WhoSaidItMatchResult> {
    requireNonBlank(matchId, "matchId");

    return apiFetch(`${MATCHES_PATH}/${encodeURIComponent(matchId)}/result`, isResult);
}
