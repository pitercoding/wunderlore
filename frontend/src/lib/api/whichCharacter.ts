import { apiFetch, COLD_START_TIMEOUT_MS } from "@/lib/api/client";
import { isNonBlankString, isRecord } from "@/lib/api/guards";

export interface WhichCharacterOption {
    id: string;
    text: string;
}

export interface WhichCharacterQuestion {
    id: string;
    text: string;
    options: WhichCharacterOption[];
}

export interface WhichCharacterQuestions {
    questions: WhichCharacterQuestion[];
}

export interface WhichCharacterAnswer {
    questionId: string;
    answerId: string;
}

export interface WhichCharacterProfile {
    id: string;
    name: string;
    description: string;
}

export interface WhichCharacterResult {
    character: WhichCharacterProfile;
}

const QUESTION_COUNT = 10;

function isWhichCharacterOption(value: unknown): value is WhichCharacterOption {
    return (
        isRecord(value) &&
        isNonBlankString(value.id) &&
        isNonBlankString(value.text)
    );
}

function isWhichCharacterQuestion(value: unknown): value is WhichCharacterQuestion {
    return (
        isRecord(value) &&
        isNonBlankString(value.id) &&
        isNonBlankString(value.text) &&
        Array.isArray(value.options) &&
        value.options.length > 0 &&
        value.options.every(isWhichCharacterOption)
    );
}

// The count is checked: the result endpoint only accepts one answer per question,
// so a quiz with any other number of questions could never be finished.
function isWhichCharacterQuestions(value: unknown): value is WhichCharacterQuestions {
    return (
        isRecord(value) &&
        Array.isArray(value.questions) &&
        value.questions.length === QUESTION_COUNT &&
        value.questions.every(isWhichCharacterQuestion)
    );
}

// The id is not checked against a list of characters: the backend owns that list.
function isWhichCharacterProfile(value: unknown): value is WhichCharacterProfile {
    return (
        isRecord(value) &&
        isNonBlankString(value.id) &&
        isNonBlankString(value.name) &&
        isNonBlankString(value.description)
    );
}

function isWhichCharacterResult(value: unknown): value is WhichCharacterResult {
    return isRecord(value) && isWhichCharacterProfile(value.character);
}

function isWhichCharacterAnswer(value: unknown): value is WhichCharacterAnswer {
    return (
        isRecord(value) &&
        isNonBlankString(value.questionId) &&
        isNonBlankString(value.answerId)
    );
}

const QUESTIONS_PATH = "/api/games/which-character/questions";
const RESULT_PATH = "/api/games/which-character/result";

export async function getQuestions(): Promise<WhichCharacterQuestions> {
    return apiFetch(QUESTIONS_PATH, isWhichCharacterQuestions, undefined, {
        timeoutMs: COLD_START_TIMEOUT_MS,
    });
}

// Only the shape is checked: the backend validates and scores the answers.
export async function submitAnswers(
    answers: WhichCharacterAnswer[],
): Promise<WhichCharacterResult> {
    if (!Array.isArray(answers) || answers.length !== QUESTION_COUNT) {
        throw new Error(`answers must contain exactly ${QUESTION_COUNT} items`);
    }

    if (!answers.every(isWhichCharacterAnswer)) {
        throw new Error("every answer must have a questionId and an answerId");
    }

    return apiFetch(RESULT_PATH, isWhichCharacterResult, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answers }),
    });
}
