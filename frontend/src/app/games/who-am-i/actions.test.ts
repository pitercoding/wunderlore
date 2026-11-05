import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import { createGame, getCurrentRound, getResult, submitAnswer } from "@/lib/api/whoAmI";
import {
    fetchWhoAmIResult,
    fetchWhoAmIRound,
    startWhoAmIGame,
    submitWhoAmIAnswer,
} from "./actions";

vi.mock("@/lib/api/whoAmI", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/lib/api/whoAmI")>()),
    createGame: vi.fn(),
    getCurrentRound: vi.fn(),
    submitAnswer: vi.fn(),
    getResult: vi.fn(),
}));

const createGameMock = vi.mocked(createGame);
const getRoundMock = vi.mocked(getCurrentRound);
const submitAnswerMock = vi.mocked(submitAnswer);
const getResultMock = vi.mocked(getResult);

// The format WhoAmIGameRegistry issues (UUID.randomUUID()).
const GAME_ID = "123e4567-e89b-42d3-a456-426614174000";

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";
const COOLDOWN =
    "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.";

const ROUND = {
    gameId: GAME_ID,
    difficulty: "EASY" as const,
    roundNumber: 1,
    totalRounds: 10,
    clueNumber: 1,
    totalClues: 5,
    clues: ["A first clue."],
    options: [{ id: "a", text: "Gandalf" }],
    score: 0,
    maxScore: 1000,
};

const ANSWER = {
    gameId: GAME_ID,
    roundNumber: 1,
    clueNumber: 1,
    submittedOptionId: "a",
    correct: false,
    roundFinished: false,
    pointsAwarded: 0,
    score: 0,
    gameFinished: false,
    next: { clueNumber: 2, clues: ["One.", "Two."], options: [{ id: "a", text: "Gandalf" }] },
    reveal: null,
    result: null,
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

describe("startWhoAmIGame", () => {
    it("returns the first round of the game the backend created", async () => {
        createGameMock.mockResolvedValue(ROUND);

        await expect(startWhoAmIGame("EASY")).resolves.toEqual({ ok: true, round: ROUND });
        expect(createGameMock).toHaveBeenCalledWith("EASY");
    });

    it.each([
        ["an unknown difficulty", "NIGHTMARE"],
        ["a lowercase difficulty", "easy"],
        ["a non-string", 1],
        ["an object", {}],
        ["a missing value", undefined],
    ])("refuses %s without calling the backend", async (_, difficulty) => {
        await expect(startWhoAmIGame(difficulty)).resolves.toMatchObject({
            ok: false,
            kind: "invalid",
        });
        expect(createGameMock).not.toHaveBeenCalled();
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        ["without a retry time", status(503), UNAVAILABLE],
        ["with a retry time", status(503, 30), COOLDOWN],
    ])("reports a 503 %s as temporary without logging it", async (_, error, message) => {
        createGameMock.mockRejectedValue(error);

        await expect(startWhoAmIGame("EASY")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("names the wait of a 429 and does not log it", async () => {
        createGameMock.mockRejectedValue(status(429, 45));

        const response = await startWhoAmIGame("EASY");

        expect(response).toMatchObject({ ok: false, kind: "temporary" });
        expect(response.ok === false && response.message).toBe(
            "Too many games were started in a short time. Please try again in about 45 seconds.",
        );
        expect(console.error).not.toHaveBeenCalled();
    });

    it("does not name a wait when Retry-After is unusable", async () => {
        createGameMock.mockRejectedValue(status(429));

        const response = await startWhoAmIGame("EASY");

        expect(response.ok === false && response.message).toBe(
            "Too many games were started in a short time. Please try again in a moment.",
        );
    });

    it.each([
        ["a timeout", new ApiTimeoutError(1000)],
        ["a network failure", new TypeError("fetch failed")],
    ])("reports %s as unavailable", async (_, error) => {
        createGameMock.mockRejectedValue(error);

        await expect(startWhoAmIGame("EASY")).resolves.toMatchObject({
            ok: false,
            message: UNAVAILABLE,
        });
    });

    it("logs and generalizes anything unexpected", async () => {
        createGameMock.mockRejectedValue(new ApiResponseError("/api/games/who-am-i/games"));

        await expect(startWhoAmIGame("EASY")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: "Unable to start a game. Please try again.",
        });
        expect(console.error).toHaveBeenCalledTimes(1);
    });
});

describe("submitWhoAmIAnswer", () => {
    it("returns what the backend decided and sends only the position and the option", async () => {
        submitAnswerMock.mockResolvedValue(ANSWER);

        await expect(submitWhoAmIAnswer(GAME_ID, 1, 1, "a")).resolves.toEqual({
            ok: true,
            answer: ANSWER,
        });
        expect(submitAnswerMock).toHaveBeenCalledWith(GAME_ID, 1, 1, "a");
    });

    it.each([
        ["a game id that is not a UUID", "game-1", 1, 1, "a"],
        ["a path-like game id", "../../health", 1, 1, "a"],
        ["a round of zero", GAME_ID, 0, 1, "a"],
        ["a round above ten", GAME_ID, 11, 1, "a"],
        ["a fractional round", GAME_ID, 1.5, 1, "a"],
        ["a clue of zero", GAME_ID, 1, 0, "a"],
        ["a clue above five", GAME_ID, 1, 6, "a"],
        ["a string round", GAME_ID, "1", 1, "a"],
        ["an option outside a to d", GAME_ID, 1, 1, "e"],
        ["an uppercase option", GAME_ID, 1, 1, "A"],
        ["non-string arguments", { id: 1 }, null, undefined, 4],
    ])("refuses %s without calling the backend", async (_, gameId, round, clue, optionId) => {
        await expect(submitWhoAmIAnswer(gameId, round, clue, optionId)).resolves.toMatchObject({
            ok: false,
            kind: "invalid",
        });
        expect(submitAnswerMock).not.toHaveBeenCalled();
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        [400, "invalid"],
        [404, "terminal"],
        [409, "stale"],
    ])("classifies a %i as %s without logging it", async (code, kind) => {
        submitAnswerMock.mockRejectedValue(status(code));

        await expect(submitWhoAmIAnswer(GAME_ID, 1, 1, "a")).resolves.toMatchObject({
            ok: false,
            kind,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("treats a 503 as temporary and does not log it", async () => {
        submitAnswerMock.mockRejectedValue(status(503));

        await expect(submitWhoAmIAnswer(GAME_ID, 1, 1, "a")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: UNAVAILABLE,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("logs and generalizes an unexpected failure", async () => {
        submitAnswerMock.mockRejectedValue(new ApiResponseError("/answer"));

        await expect(submitWhoAmIAnswer(GAME_ID, 1, 1, "a")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: "Something went wrong. Please try again.",
        });
        expect(console.error).toHaveBeenCalledTimes(1);
    });
});

describe("fetchWhoAmIRound", () => {
    it("returns the current round", async () => {
        getRoundMock.mockResolvedValue(ROUND);

        await expect(fetchWhoAmIRound(GAME_ID)).resolves.toEqual({ ok: true, round: ROUND });
        expect(getRoundMock).toHaveBeenCalledWith(GAME_ID);
    });

    it("refuses an id that is not a UUID without calling the backend", async () => {
        await expect(fetchWhoAmIRound("nope")).resolves.toMatchObject({ ok: false, kind: "invalid" });
        expect(getRoundMock).not.toHaveBeenCalled();
    });

    it.each([
        [404, "terminal"],
        [409, "stale"],
    ])("classifies a %i as %s", async (code, kind) => {
        getRoundMock.mockRejectedValue(status(code));

        await expect(fetchWhoAmIRound(GAME_ID)).resolves.toMatchObject({ ok: false, kind });
    });
});

describe("fetchWhoAmIResult", () => {
    it("returns the result", async () => {
        const result = {
            gameId: GAME_ID,
            difficulty: "EASY" as const,
            totalRounds: 10,
            score: 0,
            maxScore: 1000,
            percentage: 0,
            identified: 0,
            incorrectAnswers: 0,
            unidentified: 0,
            rounds: [],
        };
        getResultMock.mockResolvedValue(result);

        await expect(fetchWhoAmIResult(GAME_ID)).resolves.toEqual({ ok: true, result });
    });

    it("refuses an id that is not a UUID and treats a 409 as stale", async () => {
        await expect(fetchWhoAmIResult("nope")).resolves.toMatchObject({ kind: "invalid" });

        getResultMock.mockRejectedValue(status(409));

        await expect(fetchWhoAmIResult(GAME_ID)).resolves.toMatchObject({ ok: false, kind: "stale" });
    });
});
