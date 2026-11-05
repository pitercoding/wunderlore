import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isIntegerFrom, isNonBlankString, isRecord } from "@/lib/api/guards";

export const WHO_AM_I_DIFFICULTIES = ["EASY", "MEDIUM", "HARD"] as const;

export type WhoAmIDifficulty = (typeof WHO_AM_I_DIFFICULTIES)[number];

export const WHO_AM_I_TOTAL_CLUES = 5;

export interface WhoAmIOption {
    id: string;
    text: string;
}

// The round waiting for an answer. It carries the clues revealed so far and the four options,
// never the character, the later clues or which option is correct.
export interface WhoAmIRound {
    gameId: string;
    difficulty: WhoAmIDifficulty;
    roundNumber: number;
    totalRounds: number;
    clueNumber: number;
    totalClues: number;
    clues: string[];
    options: WhoAmIOption[];
    score: number;
    maxScore: number;
}

export interface WhoAmINextClue {
    clueNumber: number;
    clues: string[];
    options: WhoAmIOption[];
}

// What a finished round discloses.
export interface WhoAmIReveal {
    characterName: string;
    clues: string[];
}

export interface WhoAmIRoundSummary {
    roundNumber: number;
    characterName: string;
    cluesUsed: number;
    identified: boolean;
    points: number;
    wrongAttempts: number;
}

export interface WhoAmIResult {
    gameId: string;
    difficulty: WhoAmIDifficulty;
    totalRounds: number;
    score: number;
    maxScore: number;
    percentage: number;
    identified: number;
    incorrectAnswers: number;
    unidentified: number;
    rounds: WhoAmIRoundSummary[];
}

// Exactly one of next and reveal is set; result only after the last round.
export interface WhoAmIAnswer {
    gameId: string;
    roundNumber: number;
    clueNumber: number;
    submittedOptionId: string;
    correct: boolean;
    roundFinished: boolean;
    pointsAwarded: number;
    score: number;
    gameFinished: boolean;
    next: WhoAmINextClue | null;
    reveal: WhoAmIReveal | null;
    result: WhoAmIResult | null;
}

function isDifficulty(value: unknown): value is WhoAmIDifficulty {
    return typeof value === "string" && (WHO_AM_I_DIFFICULTIES as readonly string[]).includes(value);
}

function isOption(value: unknown): value is WhoAmIOption {
    return isRecord(value) && isNonBlankString(value.id) && isNonBlankString(value.text);
}

// Four options are expected, but only an empty list makes a round unplayable.
function isOptionList(value: unknown): value is WhoAmIOption[] {
    return Array.isArray(value) && value.length > 0 && value.every(isOption);
}

function isClueList(value: unknown): value is string[] {
    return (
        Array.isArray(value) &&
        value.length >= 1 &&
        value.length <= WHO_AM_I_TOTAL_CLUES &&
        value.every(isNonBlankString)
    );
}

function isWhoAmIRound(value: unknown): value is WhoAmIRound {
    return (
        isRecord(value) &&
        isNonBlankString(value.gameId) &&
        isDifficulty(value.difficulty) &&
        isIntegerFrom(1, value.roundNumber) &&
        isIntegerFrom(1, value.totalRounds) &&
        isIntegerFrom(1, value.clueNumber) &&
        isIntegerFrom(1, value.totalClues) &&
        isClueList(value.clues) &&
        value.clues.length === value.clueNumber &&
        isOptionList(value.options) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(0, value.maxScore)
    );
}

function isNextClue(value: unknown): value is WhoAmINextClue {
    return (
        isRecord(value) &&
        isIntegerFrom(2, value.clueNumber) &&
        isClueList(value.clues) &&
        value.clues.length === value.clueNumber &&
        isOptionList(value.options)
    );
}

function isReveal(value: unknown): value is WhoAmIReveal {
    return (
        isRecord(value) &&
        isNonBlankString(value.characterName) &&
        Array.isArray(value.clues) &&
        value.clues.length === WHO_AM_I_TOTAL_CLUES &&
        value.clues.every(isNonBlankString)
    );
}

function isRoundSummary(value: unknown): value is WhoAmIRoundSummary {
    return (
        isRecord(value) &&
        isIntegerFrom(1, value.roundNumber) &&
        isNonBlankString(value.characterName) &&
        isIntegerFrom(1, value.cluesUsed) &&
        value.cluesUsed <= WHO_AM_I_TOTAL_CLUES &&
        typeof value.identified === "boolean" &&
        isIntegerFrom(0, value.points) &&
        isIntegerFrom(0, value.wrongAttempts)
    );
}

function isWhoAmIResult(value: unknown): value is WhoAmIResult {
    return (
        isRecord(value) &&
        isNonBlankString(value.gameId) &&
        isDifficulty(value.difficulty) &&
        isIntegerFrom(1, value.totalRounds) &&
        isIntegerFrom(0, value.score) &&
        isIntegerFrom(0, value.maxScore) &&
        isIntegerFrom(0, value.percentage) &&
        value.percentage <= 100 &&
        isIntegerFrom(0, value.identified) &&
        isIntegerFrom(0, value.incorrectAnswers) &&
        isIntegerFrom(0, value.unidentified) &&
        Array.isArray(value.rounds) &&
        value.rounds.every(isRoundSummary)
    );
}

// next, reveal and result are always present, null when they do not apply. A response where the
// round both goes on and ends, or where the game ends without a result, cannot be played.
function isWhoAmIAnswer(value: unknown): value is WhoAmIAnswer {
    if (
        !(
            isRecord(value) &&
            isNonBlankString(value.gameId) &&
            isIntegerFrom(1, value.roundNumber) &&
            isIntegerFrom(1, value.clueNumber) &&
            isNonBlankString(value.submittedOptionId) &&
            typeof value.correct === "boolean" &&
            typeof value.roundFinished === "boolean" &&
            isIntegerFrom(0, value.pointsAwarded) &&
            isIntegerFrom(0, value.score) &&
            typeof value.gameFinished === "boolean" &&
            (value.next === null || isNextClue(value.next)) &&
            (value.reveal === null || isReveal(value.reveal)) &&
            (value.result === null || isWhoAmIResult(value.result))
        )
    ) {
        return false;
    }

    const hasNext = value.next !== null;
    const hasReveal = value.reveal !== null;

    if (hasNext === hasReveal || hasNext === value.roundFinished) {
        return false;
    }

    return (value.result !== null) === value.gameFinished && (!value.gameFinished || hasReveal);
}

const GAMES_PATH = "/api/games/who-am-i/games";

function requireNonBlank(value: string, name: string): void {
    if (!value.trim()) {
        throw new Error(`${name} must be a non-empty string`);
    }
}

// Creates server-side state, so it is a POST. The first game of a visit may wake the backend; an
// unused game costs nothing.
export async function createGame(difficulty: WhoAmIDifficulty): Promise<WhoAmIRound> {
    return apiFetch(
        GAMES_PATH,
        isWhoAmIRound,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ difficulty }),
        },
        { timeoutMs: COLD_START_TIMEOUT_MS },
    );
}

// The round the game is waiting on: the next one, once the previous was finished.
export async function getCurrentRound(gameId: string): Promise<WhoAmIRound> {
    requireNonBlank(gameId, "gameId");

    return apiFetch(`${GAMES_PATH}/${encodeURIComponent(gameId)}/round`, isWhoAmIRound);
}

export async function submitAnswer(
    gameId: string,
    roundNumber: number,
    clueNumber: number,
    optionId: string,
): Promise<WhoAmIAnswer> {
    requireNonBlank(gameId, "gameId");
    requireNonBlank(optionId, "optionId");

    return apiFetch(`${GAMES_PATH}/${encodeURIComponent(gameId)}/answer`, isWhoAmIAnswer, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ roundNumber, clueNumber, optionId }),
    });
}

export async function getResult(gameId: string): Promise<WhoAmIResult> {
    requireNonBlank(gameId, "gameId");

    return apiFetch(`${GAMES_PATH}/${encodeURIComponent(gameId)}/result`, isWhoAmIResult);
}
