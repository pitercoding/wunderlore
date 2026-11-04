import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const MATCH_ID = "123e4567-e89b-42d3-a456-426614174000";

const QUESTION = {
    matchId: MATCH_ID,
    category: "ALL",
    difficulty: "EASY",
    number: 1,
    total: 10,
    questionId: "places-easy-01",
    questionCategory: "PLACES",
    prompt: "Where does Bilbo live?",
    options: [
        { id: "a", text: "Bag End" },
        { id: "b", text: "Rivendell" },
        { id: "c", text: "Isengard" },
        { id: "d", text: "Orthanc" },
    ],
};

const ANSWER = {
    matchId: MATCH_ID,
    questionId: "places-easy-01",
    submittedOptionId: "a",
    correct: true,
    correctOptionId: "a",
    explanation: "A comfortable hobbit-hole.",
    pointsAwarded: 10,
    score: 10,
    answered: 1,
    total: 10,
    finished: false,
};

const RESULT = {
    matchId: MATCH_ID,
    category: "PLACES",
    difficulty: "HARD",
    totalQuestions: 10,
    correctAnswers: 4,
    incorrectAnswers: 6,
    score: 120,
    maxScore: 300,
    percentage: 40,
    durationSeconds: 61,
    rank: "Ranger of the North",
};

const fetchMock = vi.fn<typeof fetch>();

async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    return import("./loreChallenge");
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

describe("createMatch", () => {
    it("POSTs the chosen category and difficulty as JSON", async () => {
        const { createMatch } = await loadModules();
        respond(QUESTION);

        await expect(createMatch("PLACES", "HARD")).resolves.toEqual(QUESTION);

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe("http://api.test/api/games/lore-challenge/matches");
        expect(init?.method).toBe("POST");
        expect(JSON.parse(String(init?.body))).toEqual({ category: "PLACES", difficulty: "HARD" });
        expect(init?.headers).toMatchObject({ "Content-Type": "application/json" });
    });

    it.each([
        ["no options", { ...QUESTION, options: [] }],
        ["a blank prompt", { ...QUESTION, prompt: " " }],
        ["an unknown category", { ...QUESTION, questionCategory: "SPELLS" }],
        ["an unknown difficulty", { ...QUESTION, difficulty: "NIGHTMARE" }],
        ["a question number of zero", { ...QUESTION, number: 0 }],
        ["a malformed option", { ...QUESTION, options: [{ id: "a" }] }],
        ["a missing match id", { ...QUESTION, matchId: undefined }],
    ])("rejects a response with %s", async (_, body) => {
        const { createMatch } = await loadModules();
        const { ApiResponseError } = await import("./client");
        respond(body);

        await expect(createMatch("ALL", "EASY")).rejects.toBeInstanceOf(ApiResponseError);
    });
});

describe("getCurrentQuestion", () => {
    it("GETs the question of the match, with the id encoded", async () => {
        const { getCurrentQuestion } = await loadModules();
        respond(QUESTION);

        await getCurrentQuestion("a/b");

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe("http://api.test/api/games/lore-challenge/matches/a%2Fb/question");
        expect(init?.method).toBeUndefined();
    });

    it("refuses a blank match id without calling the backend", async () => {
        const { getCurrentQuestion } = await loadModules();

        await expect(getCurrentQuestion(" ")).rejects.toThrow("matchId");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe("submitAnswer", () => {
    it("POSTs only the question id and the option id", async () => {
        const { submitAnswer } = await loadModules();
        respond(ANSWER);

        await expect(submitAnswer(MATCH_ID, "places-easy-01", "a")).resolves.toEqual(ANSWER);

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe(`http://api.test/api/games/lore-challenge/matches/${MATCH_ID}/answer`);
        expect(init?.method).toBe("POST");
        // Nothing a client could use to claim a score.
        expect(JSON.parse(String(init?.body))).toEqual({
            questionId: "places-easy-01",
            optionId: "a",
        });
    });

    it.each([
        ["", "q", "a", "matchId"],
        [MATCH_ID, " ", "a", "questionId"],
        [MATCH_ID, "q", "", "optionId"],
    ])("refuses blank ids without calling the backend", async (matchId, questionId, optionId, name) => {
        const { submitAnswer } = await loadModules();

        await expect(submitAnswer(matchId, questionId, optionId)).rejects.toThrow(name);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([
        ["a negative score", { ...ANSWER, score: -1 }],
        ["a non-boolean correct", { ...ANSWER, correct: "yes" }],
        ["a missing explanation", { ...ANSWER, explanation: "" }],
        ["a fractional answered count", { ...ANSWER, answered: 1.5 }],
    ])("rejects a response with %s", async (_, body) => {
        const { submitAnswer } = await loadModules();
        const { ApiResponseError } = await import("./client");
        respond(body);

        await expect(submitAnswer(MATCH_ID, "places-easy-01", "a")).rejects.toBeInstanceOf(
            ApiResponseError,
        );
    });
});

describe("getResult", () => {
    it("GETs the result of the match", async () => {
        const { getResult } = await loadModules();
        respond(RESULT);

        await expect(getResult(MATCH_ID)).resolves.toEqual(RESULT);
        expect(fetchMock.mock.calls[0][0]).toBe(
            `http://api.test/api/games/lore-challenge/matches/${MATCH_ID}/result`,
        );
    });

    it.each([
        ["a percentage above 100", { ...RESULT, percentage: 101 }],
        ["a blank rank", { ...RESULT, rank: "" }],
        ["no questions", { ...RESULT, totalQuestions: 0 }],
        ["an unknown category", { ...RESULT, category: "ALL_OF_THEM" }],
    ])("rejects a response with %s", async (_, body) => {
        const { getResult } = await loadModules();
        const { ApiResponseError } = await import("./client");
        respond(body);

        await expect(getResult(MATCH_ID)).rejects.toBeInstanceOf(ApiResponseError);
    });
});
