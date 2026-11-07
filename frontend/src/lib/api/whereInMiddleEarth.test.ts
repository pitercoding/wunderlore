import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const GAME_ID = "123e4567-e89b-42d3-a456-426614174000";

const ROUND = {
    gameId: GAME_ID,
    difficulty: "EASY",
    roundNumber: 1,
    totalRounds: 10,
    locationName: "Rivendell",
    description: "Elrond's hidden refuge.",
    score: 0,
    maxScore: 1000,
};

const REVEAL = {
    locationName: "Rivendell",
    regionName: "Eriador",
    period: "Third Age",
    explanation: "It lay on the eastern edge of Eriador.",
    x: 385,
    y: 226,
};

const RESULT = {
    gameId: GAME_ID,
    difficulty: "HARD",
    totalRounds: 10,
    score: 420,
    maxScore: 1000,
    percentage: 42,
    closeHits: 7,
    averageDistance: 71.5,
    rounds: [
        { roundNumber: 1, locationName: "Rivendell", accuracy: "GOOD", distance: 40, points: 60 },
    ],
};

const ANSWER = {
    gameId: GAME_ID,
    roundNumber: 1,
    submitted: { x: 380.5, y: 230 },
    distance: 6,
    accuracy: "EXCELLENT",
    pointsAwarded: 100,
    score: 100,
    reveal: REVEAL,
    gameFinished: false,
    result: null,
};

const fetchMock = vi.fn<typeof fetch>();

function respond(body: unknown) {
    fetchMock.mockResolvedValue(
        new Response(JSON.stringify(body), { headers: { "Content-Type": "application/json" } }),
    );
}

async function load() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    return import("./whereInMiddleEarth");
}

beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

describe("Where in Middle-earth API client: requests", () => {
    it("creates a game with a POST that carries only the difficulty", async () => {
        const { createGame } = await load();

        respond(ROUND);
        await createGame("HARD");

        const [url, init] = fetchMock.mock.calls[0];

        expect(String(url)).toBe("http://api.test/api/games/where-in-middle-earth/games");
        expect(init?.method).toBe("POST");
        expect(JSON.parse(String(init?.body))).toEqual({ difficulty: "HARD" });
    });

    it("sends the round and the point only, never a score", async () => {
        const { submitAnswer } = await load();

        respond(ANSWER);
        await submitAnswer(GAME_ID, 1, { x: 380.5, y: 230 });

        const [url, init] = fetchMock.mock.calls[0];

        expect(String(url)).toBe(
            `http://api.test/api/games/where-in-middle-earth/games/${GAME_ID}/answer`,
        );
        expect(init?.method).toBe("POST");
        expect(JSON.parse(String(init?.body))).toEqual({ roundNumber: 1, x: 380.5, y: 230 });
    });

    it("reads the round and the result with GET", async () => {
        const { getCurrentRound, getResult } = await load();

        respond(ROUND);
        await getCurrentRound(GAME_ID);
        respond(RESULT);
        await getResult(GAME_ID);

        expect(String(fetchMock.mock.calls[0][0])).toBe(
            `http://api.test/api/games/where-in-middle-earth/games/${GAME_ID}/round`,
        );
        expect(String(fetchMock.mock.calls[1][0])).toBe(
            `http://api.test/api/games/where-in-middle-earth/games/${GAME_ID}/result`,
        );
    });

    it("refuses a blank game id before any request", async () => {
        const { getCurrentRound, getResult, submitAnswer } = await load();

        await expect(getCurrentRound(" ")).rejects.toThrow("gameId");
        await expect(getResult("")).rejects.toThrow("gameId");
        await expect(submitAnswer(" ", 1, { x: 1, y: 1 })).rejects.toThrow("gameId");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe("Where in Middle-earth API client: response validation", () => {
    it.each([
        ["an unknown difficulty", { ...ROUND, difficulty: "IMPOSSIBLE" }],
        ["a missing place name", { ...ROUND, locationName: " " }],
        ["a zero round number", { ...ROUND, roundNumber: 0 }],
        ["a fractional score", { ...ROUND, score: 1.5 }],
    ])("rejects a round with %s", async (_, body) => {
        const { getCurrentRound } = await load();

        respond(body);

        await expect(getCurrentRound(GAME_ID)).rejects.toThrow();
    });

    it.each([
        ["a point outside the map", { ...ANSWER, submitted: { x: 1000.5, y: 10 } }],
        ["a negative coordinate", { ...ANSWER, reveal: { ...REVEAL, y: -1 } }],
        ["a coordinate that is not a number", { ...ANSWER, submitted: { x: "10", y: 10 } }],
        ["an unknown accuracy", { ...ANSWER, accuracy: "PERFECT" }],
        ["a negative distance", { ...ANSWER, distance: -1 }],
        ["a missing reveal", { ...ANSWER, reveal: null }],
        ["a reveal without an explanation", { ...ANSWER, reveal: { ...REVEAL, explanation: "" } }],
        ["a game that ends without a result", { ...ANSWER, gameFinished: true, result: null }],
        ["a result before the end", { ...ANSWER, gameFinished: false, result: RESULT }],
    ])("rejects an answer with %s", async (_, body) => {
        const { submitAnswer } = await load();

        respond(body);

        await expect(submitAnswer(GAME_ID, 1, { x: 1, y: 1 })).rejects.toThrow();
    });

    it.each([
        ["a percentage above 100", { ...RESULT, percentage: 101 }],
        ["an unknown accuracy in a round", { ...RESULT, rounds: [{ ...RESULT.rounds[0], accuracy: "X" }] }],
        ["a round that is not an object", { ...RESULT, rounds: [null] }],
        ["a missing average distance", { ...RESULT, averageDistance: undefined }],
    ])("rejects a result with %s", async (_, body) => {
        const { getResult } = await load();

        respond(body);

        await expect(getResult(GAME_ID)).rejects.toThrow();
    });

    it("accepts an answer that finishes the game with its result", async () => {
        const { submitAnswer } = await load();

        respond({ ...ANSWER, roundNumber: 10, gameFinished: true, result: RESULT });

        await expect(submitAnswer(GAME_ID, 10, { x: 1, y: 1 })).resolves.toMatchObject({
            gameFinished: true,
            result: { score: 420 },
        });
    });
});
