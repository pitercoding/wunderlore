import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import {
    createMatch,
    getCurrentQuestion,
    getResult,
    submitAnswer,
} from "@/lib/api/loreChallenge";
import {
    fetchLoreChallengeQuestion,
    fetchLoreChallengeResult,
    startLoreChallengeMatch,
    submitLoreChallengeAnswer,
} from "./actions";

vi.mock("@/lib/api/loreChallenge", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/lib/api/loreChallenge")>()),
    createMatch: vi.fn(),
    getCurrentQuestion: vi.fn(),
    submitAnswer: vi.fn(),
    getResult: vi.fn(),
}));

const createMatchMock = vi.mocked(createMatch);
const getQuestionMock = vi.mocked(getCurrentQuestion);
const submitAnswerMock = vi.mocked(submitAnswer);
const getResultMock = vi.mocked(getResult);

// The format LoreChallengeMatchRegistry issues (UUID.randomUUID()).
const MATCH_ID = "123e4567-e89b-42d3-a456-426614174000";

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";
const COOLDOWN =
    "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.";

const QUESTION = {
    matchId: MATCH_ID,
    category: "ALL" as const,
    difficulty: "EASY" as const,
    number: 1,
    total: 10,
    questionId: "places-easy-01",
    questionCategory: "PLACES" as const,
    prompt: "Where does Bilbo live?",
    options: [{ id: "a", text: "Bag End" }],
};

function status(code: number, retryAfter?: number) {
    return new ApiError(`API request failed with status ${code}`, code, retryAfter);
}

beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("startLoreChallengeMatch", () => {
    it("returns the first question of the match the backend created", async () => {
        createMatchMock.mockResolvedValue(QUESTION);

        await expect(startLoreChallengeMatch("ALL", "EASY")).resolves.toEqual({
            ok: true,
            question: QUESTION,
        });
        expect(createMatchMock).toHaveBeenCalledWith("ALL", "EASY");
    });

    it.each([
        ["an unknown category", "SPELLS", "EASY"],
        ["a lowercase category", "places", "EASY"],
        ["an unknown difficulty", "ALL", "NIGHTMARE"],
        ["non-string values", 1, {}],
        ["missing values", undefined, undefined],
    ])("refuses %s without calling the backend", async (_, category, difficulty) => {
        await expect(startLoreChallengeMatch(category, difficulty)).resolves.toMatchObject({
            ok: false,
            kind: "invalid",
        });
        expect(createMatchMock).not.toHaveBeenCalled();
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        ["without a retry time", status(503), UNAVAILABLE],
        ["with a retry time", status(503, 30), COOLDOWN],
    ])("reports a 503 %s as temporary without logging it", async (_, error, message) => {
        createMatchMock.mockRejectedValue(error);

        await expect(startLoreChallengeMatch("ALL", "EASY")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("names the wait of a 429 and does not log it", async () => {
        createMatchMock.mockRejectedValue(status(429, 45));

        const response = await startLoreChallengeMatch("ALL", "EASY");

        expect(response).toMatchObject({ ok: false, kind: "temporary" });
        expect(response.ok === false && response.message).toBe(
            "Too many matches were started in a short time. Please try again in about 45 seconds.",
        );
        expect(console.error).not.toHaveBeenCalled();
    });

    it("explains a 422 as too few questions for the selection", async () => {
        createMatchMock.mockRejectedValue(status(422));

        const response = await startLoreChallengeMatch("EVENTS", "HARD");

        expect(response.ok === false && response.message).toContain("aren't enough questions");
    });

    it.each([
        ["a timeout", new ApiTimeoutError(1000)],
        ["a network failure", new TypeError("fetch failed")],
    ])("reports %s as unavailable", async (_, error) => {
        createMatchMock.mockRejectedValue(error);

        await expect(startLoreChallengeMatch("ALL", "EASY")).resolves.toMatchObject({
            ok: false,
            message: UNAVAILABLE,
        });
    });

    it("logs and generalizes anything unexpected", async () => {
        createMatchMock.mockRejectedValue(new ApiResponseError("/api/games/lore-challenge/matches"));

        await expect(startLoreChallengeMatch("ALL", "EASY")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: "Unable to start a match. Please try again.",
        });
        expect(console.error).toHaveBeenCalledTimes(1);
    });
});

describe("submitLoreChallengeAnswer", () => {
    const ANSWER = {
        matchId: MATCH_ID,
        questionId: "places-easy-01",
        submittedOptionId: "a",
        correct: true,
        correctOptionId: "a",
        explanation: "Because.",
        pointsAwarded: 10,
        score: 10,
        answered: 1,
        total: 10,
        finished: false,
    };

    it("returns what the backend decided", async () => {
        submitAnswerMock.mockResolvedValue(ANSWER);

        await expect(
            submitLoreChallengeAnswer(MATCH_ID, "places-easy-01", "a"),
        ).resolves.toEqual({ ok: true, answer: ANSWER });
        expect(submitAnswerMock).toHaveBeenCalledWith(MATCH_ID, "places-easy-01", "a");
    });

    it.each([
        ["a match id that is not a UUID", "match-1", "places-easy-01", "a"],
        ["a path-like match id", "../../health", "places-easy-01", "a"],
        ["an uppercase question id", MATCH_ID, "PLACES-EASY-01", "a"],
        ["a question id that is too long", MATCH_ID, "q".repeat(65), "a"],
        ["an option outside a to d", MATCH_ID, "places-easy-01", "e"],
        ["an uppercase option", MATCH_ID, "places-easy-01", "A"],
        ["non-string arguments", { id: 1 }, 2, null],
    ])("refuses %s without calling the backend", async (_, matchId, questionId, optionId) => {
        await expect(
            submitLoreChallengeAnswer(matchId, questionId, optionId),
        ).resolves.toMatchObject({ ok: false, kind: "invalid" });
        expect(submitAnswerMock).not.toHaveBeenCalled();
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        [400, "invalid"],
        [404, "terminal"],
        [409, "stale"],
    ])("classifies a %i as %s without logging it", async (code, kind) => {
        submitAnswerMock.mockRejectedValue(status(code));

        await expect(
            submitLoreChallengeAnswer(MATCH_ID, "places-easy-01", "a"),
        ).resolves.toMatchObject({ ok: false, kind });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("treats a 503 as temporary and does not log it", async () => {
        submitAnswerMock.mockRejectedValue(status(503));

        await expect(
            submitLoreChallengeAnswer(MATCH_ID, "places-easy-01", "a"),
        ).resolves.toEqual({ ok: false, kind: "temporary", message: UNAVAILABLE });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("logs an unexpected failure and reports it as temporary", async () => {
        submitAnswerMock.mockRejectedValue(status(500));

        await expect(
            submitLoreChallengeAnswer(MATCH_ID, "places-easy-01", "a"),
        ).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: "Something went wrong. Please try again.",
        });
        expect(console.error).toHaveBeenCalledTimes(1);
    });
});

describe("fetchLoreChallengeQuestion and fetchLoreChallengeResult", () => {
    it("return the question and the result", async () => {
        getQuestionMock.mockResolvedValue(QUESTION);
        const result = {
            matchId: MATCH_ID,
            category: "ALL" as const,
            difficulty: "EASY" as const,
            totalQuestions: 10,
            correctAnswers: 10,
            incorrectAnswers: 0,
            score: 100,
            maxScore: 100,
            percentage: 100,
            durationSeconds: 30,
            rank: "Loremaster of Middle-earth",
        };

        getResultMock.mockResolvedValue(result);

        await expect(fetchLoreChallengeQuestion(MATCH_ID)).resolves.toEqual({
            ok: true,
            question: QUESTION,
        });
        await expect(fetchLoreChallengeResult(MATCH_ID)).resolves.toEqual({ ok: true, result });
    });

    it("refuse a malformed match id without calling the backend", async () => {
        await expect(fetchLoreChallengeQuestion("nope")).resolves.toMatchObject({ kind: "invalid" });
        await expect(fetchLoreChallengeResult(42)).resolves.toMatchObject({ kind: "invalid" });
        expect(getQuestionMock).not.toHaveBeenCalled();
        expect(getResultMock).not.toHaveBeenCalled();
    });

    it("tell a finished match (409) and an expired one (404) apart", async () => {
        getQuestionMock.mockRejectedValue(status(409));
        getResultMock.mockRejectedValue(status(404));

        await expect(fetchLoreChallengeQuestion(MATCH_ID)).resolves.toMatchObject({ kind: "stale" });
        await expect(fetchLoreChallengeResult(MATCH_ID)).resolves.toMatchObject({ kind: "terminal" });
    });
});
