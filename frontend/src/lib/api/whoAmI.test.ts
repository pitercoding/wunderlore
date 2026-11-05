import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const GAME_ID = "123e4567-e89b-42d3-a456-426614174000";

const OPTIONS = [
    { id: "a", text: "Gandalf" },
    { id: "b", text: "Saruman" },
    { id: "c", text: "Aragorn" },
    { id: "d", text: "Legolas" },
];

const ROUND = {
    gameId: GAME_ID,
    difficulty: "EASY",
    roundNumber: 1,
    totalRounds: 10,
    clueNumber: 1,
    totalClues: 5,
    clues: ["A first clue."],
    options: OPTIONS,
    score: 0,
    maxScore: 1000,
};

const RESULT = {
    gameId: GAME_ID,
    difficulty: "HARD",
    totalRounds: 10,
    score: 420,
    maxScore: 1000,
    percentage: 42,
    identified: 7,
    incorrectAnswers: 12,
    unidentified: 3,
    rounds: [
        { roundNumber: 1, characterName: "Gandalf", cluesUsed: 2, identified: true, points: 80, wrongAttempts: 1 },
    ],
};

const REVEAL = {
    characterName: "Gandalf",
    clues: ["One.", "Two.", "Three.", "Four.", "Five."],
};

const INCORRECT = {
    gameId: GAME_ID,
    roundNumber: 1,
    clueNumber: 1,
    submittedOptionId: "b",
    correct: false,
    roundFinished: false,
    pointsAwarded: 0,
    score: 0,
    gameFinished: false,
    next: { clueNumber: 2, clues: ["One.", "Two."], options: OPTIONS },
    reveal: null,
    result: null,
};

const CORRECT = {
    ...INCORRECT,
    submittedOptionId: "a",
    correct: true,
    roundFinished: true,
    pointsAwarded: 100,
    score: 100,
    next: null,
    reveal: REVEAL,
};

const fetchMock = vi.fn<typeof fetch>();

async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    return import("./whoAmI");
}

function respond(body: unknown) {
    fetchMock.mockResolvedValue(
        new Response(JSON.stringify(body), {
            headers: { "Content-Type": "application/json" },
        }),
    );
}

beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

describe("createGame", () => {
    it("POSTs only the chosen difficulty as JSON", async () => {
        const { createGame } = await loadModules();
        respond(ROUND);

        await expect(createGame("HARD")).resolves.toEqual(ROUND);

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe("http://api.test/api/games/who-am-i/games");
        expect(init?.method).toBe("POST");
        expect(JSON.parse(String(init?.body))).toEqual({ difficulty: "HARD" });
        expect(init?.headers).toMatchObject({ "Content-Type": "application/json" });
    });

    it.each([
        ["no options", { ...ROUND, options: [] }],
        ["a malformed option", { ...ROUND, options: [{ id: "a" }] }],
        ["no clues", { ...ROUND, clues: [] }],
        ["a blank clue", { ...ROUND, clues: [" "] }],
        ["more clues than the clue number", { ...ROUND, clues: ["One.", "Two."] }],
        ["six clues", { ...ROUND, clueNumber: 6, clues: Array(6).fill("A clue.") }],
        ["an unknown difficulty", { ...ROUND, difficulty: "NIGHTMARE" }],
        ["a round number of zero", { ...ROUND, roundNumber: 0 }],
        ["a missing game id", { ...ROUND, gameId: undefined }],
        ["a negative score", { ...ROUND, score: -1 }],
    ])("rejects a response with %s", async (_, body) => {
        const { createGame } = await loadModules();
        const { ApiResponseError } = await import("./client");
        respond(body);

        await expect(createGame("EASY")).rejects.toBeInstanceOf(ApiResponseError);
    });
});

describe("getCurrentRound", () => {
    it("GETs the round of the game, with the id encoded", async () => {
        const { getCurrentRound } = await loadModules();
        respond(ROUND);

        await getCurrentRound("a/b");

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe("http://api.test/api/games/who-am-i/games/a%2Fb/round");
        expect(init?.method).toBeUndefined();
    });

    it("refuses a blank game id without calling the backend", async () => {
        const { getCurrentRound } = await loadModules();

        await expect(getCurrentRound(" ")).rejects.toThrow("gameId must be a non-empty string");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe("submitAnswer", () => {
    it("POSTs the round, the clue and the option, and nothing about score or correctness", async () => {
        const { submitAnswer } = await loadModules();
        respond(INCORRECT);

        await expect(submitAnswer(GAME_ID, 1, 1, "b")).resolves.toEqual(INCORRECT);

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe(`http://api.test/api/games/who-am-i/games/${GAME_ID}/answer`);
        expect(init?.method).toBe("POST");
        expect(JSON.parse(String(init?.body))).toEqual({
            roundNumber: 1,
            clueNumber: 1,
            optionId: "b",
        });
    });

    it("accepts a correct answer with the reveal", async () => {
        const { submitAnswer } = await loadModules();
        respond(CORRECT);

        await expect(submitAnswer(GAME_ID, 1, 1, "a")).resolves.toEqual(CORRECT);
    });

    it("accepts the last answer when it carries the result", async () => {
        const { submitAnswer } = await loadModules();
        const last = { ...CORRECT, gameFinished: true, result: RESULT };
        respond(last);

        await expect(submitAnswer(GAME_ID, 10, 1, "a")).resolves.toEqual(last);
    });

    it.each([
        ["next and reveal both set", { ...INCORRECT, reveal: REVEAL }],
        ["neither next nor reveal", { ...INCORRECT, next: null }],
        ["a next clue for a finished round", { ...CORRECT, next: INCORRECT.next }],
        ["an unfinished round with a reveal", { ...CORRECT, roundFinished: false }],
        ["a reveal of four clues", { ...CORRECT, reveal: { ...REVEAL, clues: ["1.", "2.", "3.", "4."] } }],
        ["a next clue that is not the clue number", { ...INCORRECT, next: { ...INCORRECT.next, clueNumber: 3 } }],
        ["a finished game without a result", { ...CORRECT, gameFinished: true }],
        ["a result for a game that goes on", { ...CORRECT, result: RESULT }],
        ["a missing next", { ...CORRECT, next: undefined }],
        ["a non-boolean correct", { ...CORRECT, correct: "yes" }],
        ["a percentage above 100", { ...CORRECT, gameFinished: true, result: { ...RESULT, percentage: 101 } }],
    ])("rejects a response with %s", async (_, body) => {
        const { submitAnswer } = await loadModules();
        const { ApiResponseError } = await import("./client");
        respond(body);

        await expect(submitAnswer(GAME_ID, 1, 1, "a")).rejects.toBeInstanceOf(ApiResponseError);
    });

    it("refuses blank ids without calling the backend", async () => {
        const { submitAnswer } = await loadModules();

        await expect(submitAnswer("", 1, 1, "a")).rejects.toThrow("gameId");
        await expect(submitAnswer(GAME_ID, 1, 1, " ")).rejects.toThrow("optionId");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe("getResult", () => {
    it("GETs the result of the game", async () => {
        const { getResult } = await loadModules();
        respond(RESULT);

        await expect(getResult(GAME_ID)).resolves.toEqual(RESULT);

        expect(fetchMock.mock.calls[0][0]).toBe(
            `http://api.test/api/games/who-am-i/games/${GAME_ID}/result`,
        );
    });

    it.each([
        ["a round of six clues", { ...RESULT, rounds: [{ ...RESULT.rounds[0], cluesUsed: 6 }] }],
        ["a non-integer score", { ...RESULT, score: 10.5 }],
        ["a missing rounds list", { ...RESULT, rounds: undefined }],
    ])("rejects a response with %s", async (_, body) => {
        const { getResult } = await loadModules();
        const { ApiResponseError } = await import("./client");
        respond(body);

        await expect(getResult(GAME_ID)).rejects.toBeInstanceOf(ApiResponseError);
    });
});
