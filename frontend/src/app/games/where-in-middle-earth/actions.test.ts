import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import {
    createGame,
    getCurrentRound,
    getResult,
    submitAnswer,
} from "@/lib/api/whereInMiddleEarth";
import {
    fetchWhereInMiddleEarthResult,
    fetchWhereInMiddleEarthRound,
    startWhereInMiddleEarthGame,
    submitWhereInMiddleEarthAnswer,
} from "./actions";

vi.mock("@/lib/api/whereInMiddleEarth", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/lib/api/whereInMiddleEarth")>()),
    createGame: vi.fn(),
    getCurrentRound: vi.fn(),
    submitAnswer: vi.fn(),
    getResult: vi.fn(),
}));

const createGameMock = vi.mocked(createGame);
const getRoundMock = vi.mocked(getCurrentRound);
const submitAnswerMock = vi.mocked(submitAnswer);
const getResultMock = vi.mocked(getResult);

// The format WhereInMiddleEarthGameRegistry issues (UUID.randomUUID()).
const GAME_ID = "123e4567-e89b-42d3-a456-426614174000";

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";

const ROUND = {
    gameId: GAME_ID,
    difficulty: "EASY" as const,
    roundNumber: 1,
    totalRounds: 10,
    locationName: "Rivendell",
    description: "Elrond's hidden refuge.",
    score: 0,
    maxScore: 1000,
};

const ANSWER = {
    gameId: GAME_ID,
    roundNumber: 1,
    submitted: { x: 380.5, y: 230 },
    distance: 6,
    accuracy: "EXCELLENT" as const,
    pointsAwarded: 100,
    score: 100,
    reveal: {
        locationName: "Rivendell",
        regionName: "Eriador",
        period: "Third Age",
        explanation: "It lay on the eastern edge of Eriador.",
        x: 385,
        y: 226,
    },
    gameFinished: false,
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

describe("startWhereInMiddleEarthGame", () => {
    it.each(["EASY", "MEDIUM", "HARD"])("creates a %s game", async (difficulty) => {
        createGameMock.mockResolvedValue(ROUND);

        await expect(startWhereInMiddleEarthGame(difficulty)).resolves.toEqual({
            ok: true,
            round: ROUND,
        });
        expect(createGameMock).toHaveBeenCalledWith(difficulty);
    });

    it.each([undefined, null, "", "easy", "IMPOSSIBLE", 1, {}])(
        "refuses the difficulty %j before any request",
        async (difficulty) => {
            const response = await startWhereInMiddleEarthGame(difficulty);

            expect(response).toMatchObject({ ok: false, kind: "invalid" });
            expect(createGameMock).not.toHaveBeenCalled();
        },
    );

    it("answers that the service is unavailable on a 503, without logging it", async () => {
        createGameMock.mockRejectedValue(status(503));

        await expect(startWhereInMiddleEarthGame("EASY")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: UNAVAILABLE,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("tells how long to wait when the backend limits game creation", async () => {
        createGameMock.mockRejectedValue(status(429, 30));

        const response = await startWhereInMiddleEarthGame("EASY");

        expect(response).toMatchObject({ ok: false, kind: "temporary" });
        expect(response.ok === false && response.message).toMatch(/Too many games.*30 seconds/);
        expect(console.error).not.toHaveBeenCalled();
    });

    it("does not invent a wait when Retry-After is missing", async () => {
        createGameMock.mockRejectedValue(status(429));

        const response = await startWhereInMiddleEarthGame("EASY");

        expect(response.ok === false && response.message).toBe(
            "Too many games were started in a short time. Please try again in a moment.",
        );
    });

    it.each([
        ["a timeout", new ApiTimeoutError(5000)],
        ["a malformed response", new ApiResponseError("bad body")],
        ["a 500", status(500)],
    ])("answers a temporary failure for %s and logs it", async (_, error) => {
        createGameMock.mockRejectedValue(error);

        await expect(startWhereInMiddleEarthGame("EASY")).resolves.toMatchObject({
            ok: false,
            kind: "temporary",
        });
        expect(console.error).toHaveBeenCalled();
    });
});

describe("fetchWhereInMiddleEarthRound", () => {
    it("returns the round the server is waiting on", async () => {
        getRoundMock.mockResolvedValue(ROUND);

        await expect(fetchWhereInMiddleEarthRound(GAME_ID)).resolves.toEqual({ ok: true, round: ROUND });
        expect(getRoundMock).toHaveBeenCalledWith(GAME_ID);
    });

    it.each([undefined, null, "", "not-a-uuid", "../etc/passwd", 7])(
        "refuses the game id %j before any request",
        async (gameId) => {
            await expect(fetchWhereInMiddleEarthRound(gameId)).resolves.toMatchObject({
                ok: false,
                kind: "invalid",
            });
            expect(getRoundMock).not.toHaveBeenCalled();
        },
    );

    it.each([
        [404, "terminal"],
        [409, "stale"],
        [400, "invalid"],
        [503, "temporary"],
        [500, "temporary"],
    ])("maps a %i to %s", async (code, kind) => {
        getRoundMock.mockRejectedValue(status(code));

        await expect(fetchWhereInMiddleEarthRound(GAME_ID)).resolves.toMatchObject({ ok: false, kind });
    });
});

describe("submitWhereInMiddleEarthAnswer", () => {
    it("sends the round and the point and returns the server's answer", async () => {
        submitAnswerMock.mockResolvedValue(ANSWER);

        await expect(
            submitWhereInMiddleEarthAnswer(GAME_ID, 1, 380.5, 230),
        ).resolves.toEqual({ ok: true, answer: ANSWER });
        expect(submitAnswerMock).toHaveBeenCalledWith(GAME_ID, 1, { x: 380.5, y: 230 });
    });

    it("accepts the borders of the map", async () => {
        submitAnswerMock.mockResolvedValue(ANSWER);

        await submitWhereInMiddleEarthAnswer(GAME_ID, 10, 0, 0);
        await submitWhereInMiddleEarthAnswer(GAME_ID, 10, 1000, 700);

        expect(submitAnswerMock).toHaveBeenCalledTimes(2);
    });

    it.each([
        ["a bad game id", "nope", 1, 10, 10],
        ["round 0", GAME_ID, 0, 10, 10],
        ["round 11", GAME_ID, 11, 10, 10],
        ["a fractional round", GAME_ID, 1.5, 10, 10],
        ["a round that is a string", GAME_ID, "1", 10, 10],
        ["x below the map", GAME_ID, 1, -0.1, 10],
        ["x beyond the map", GAME_ID, 1, 1000.1, 10],
        ["y below the map", GAME_ID, 1, 10, -1],
        ["y beyond the map", GAME_ID, 1, 10, 700.1],
        ["x as NaN", GAME_ID, 1, Number.NaN, 10],
        ["y as Infinity", GAME_ID, 1, 10, Number.POSITIVE_INFINITY],
        ["x as a string", GAME_ID, 1, "10", 10],
        ["a missing y", GAME_ID, 1, 10, undefined],
        ["a null x", GAME_ID, 1, null, 10],
    ])("refuses %s before any request", async (_, gameId, roundNumber, x, y) => {
        await expect(
            submitWhereInMiddleEarthAnswer(gameId, roundNumber, x, y),
        ).resolves.toMatchObject({ ok: false, kind: "invalid" });
        expect(submitAnswerMock).not.toHaveBeenCalled();
    });

    it.each([
        [404, "terminal"],
        [409, "stale"],
        [400, "invalid"],
        [503, "temporary"],
        [500, "temporary"],
    ])("maps a %i to %s", async (code, kind) => {
        submitAnswerMock.mockRejectedValue(status(code));

        await expect(submitWhereInMiddleEarthAnswer(GAME_ID, 1, 10, 10)).resolves.toMatchObject({
            ok: false,
            kind,
        });
    });

    it("answers a temporary failure when the answer cannot be read, so trying again is safe", async () => {
        submitAnswerMock.mockRejectedValue(new ApiResponseError("bad body"));

        await expect(submitWhereInMiddleEarthAnswer(GAME_ID, 1, 10, 10)).resolves.toMatchObject({
            ok: false,
            kind: "temporary",
        });
    });
});

describe("fetchWhereInMiddleEarthResult", () => {
    it("returns the result", async () => {
        const result = {
            gameId: GAME_ID,
            difficulty: "EASY" as const,
            totalRounds: 10,
            score: 0,
            maxScore: 1000,
            percentage: 0,
            closeHits: 0,
            averageDistance: 0,
            rounds: [],
        };

        getResultMock.mockResolvedValue(result);

        await expect(fetchWhereInMiddleEarthResult(GAME_ID)).resolves.toEqual({ ok: true, result });
    });

    it("refuses a bad id and maps a 409 to stale", async () => {
        await expect(fetchWhereInMiddleEarthResult("nope")).resolves.toMatchObject({
            ok: false,
            kind: "invalid",
        });

        getResultMock.mockRejectedValue(status(409));

        await expect(fetchWhereInMiddleEarthResult(GAME_ID)).resolves.toMatchObject({
            ok: false,
            kind: "stale",
        });
    });
});
