import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    const [match, client] = await Promise.all([import("./whoSaidItMatch"), import("./client")]);
    return { ...match, ...client };
}

function jsonResponse(body: unknown, init?: ResponseInit) {
    return new Response(JSON.stringify(body), {
        headers: { "Content-Type": "application/json" },
        ...init,
    });
}

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

const question = {
    matchId: "m1",
    questionNumber: 1,
    totalQuestions: 10,
    quote: "You shall not pass!",
    options: [
        { id: "a", name: "Gandalf" },
        { id: "b", name: "Frodo" },
        { id: "c", name: "Sam" },
        { id: "d", name: "Aragorn" },
    ],
};

const result = {
    matchId: "m1",
    totalQuestions: 10,
    correctAnswers: 7,
    incorrectAnswers: 3,
    score: 70,
    maxScore: 100,
    percentage: 70,
    questions: [
        {
            questionNumber: 1,
            quote: "You shall not pass!",
            submittedCharacterName: "Gandalf",
            correctCharacterName: "Gandalf",
            correct: true,
        },
    ],
};

describe("createMatch", () => {
    it("POSTs and returns the first question", async () => {
        const { createMatch } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(question));

        await expect(createMatch()).resolves.toEqual(question);
        expect(fetchMock.mock.calls[0][0]).toBe("http://api.test/api/games/who-said-it/matches");
        expect(fetchMock.mock.calls[0][1]?.method).toBe("POST");
    });

    it.each([
        ["three options", { ...question, options: question.options.slice(0, 3) }],
        ["five options", { ...question, options: [...question.options, { id: "e", name: "E" }] }],
        ["repeated option ids", { ...question, options: question.options.map((o) => ({ ...o, id: "a" })) }],
        ["a question number beyond the total", { ...question, questionNumber: 11 }],
        ["a blank quote", { ...question, quote: " " }],
    ])("rejects %s", async (_name, payload) => {
        const { createMatch, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(payload));

        await expect(createMatch()).rejects.toBeInstanceOf(ApiResponseError);
    });
});

describe("submitMatchAnswer", () => {
    it("sends only the question number and the character", async () => {
        const { submitMatchAnswer } = await loadModules();

        fetchMock.mockResolvedValue(
            jsonResponse({
                matchId: "m1",
                questionNumber: 1,
                submittedCharacterId: "a",
                matchFinished: false,
                next: { ...question, questionNumber: 2 },
                result: null,
            }),
        );

        await submitMatchAnswer("m1", 1, "a");

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe("http://api.test/api/games/who-said-it/matches/m1/answer");
        expect(JSON.parse(String(init?.body))).toEqual({ questionNumber: 1, characterId: "a" });
    });

    it("accepts the finished answer with its result", async () => {
        const { submitMatchAnswer } = await loadModules();

        fetchMock.mockResolvedValue(
            jsonResponse({
                matchId: "m1",
                questionNumber: 10,
                submittedCharacterId: "a",
                matchFinished: true,
                next: null,
                result,
            }),
        );

        await expect(submitMatchAnswer("m1", 10, "a")).resolves.toMatchObject({
            matchFinished: true,
            result: { score: 70 },
        });
    });

    it.each([
        ["finished without a result", { matchFinished: true, next: null, result: null }],
        ["unfinished without a next question", { matchFinished: false, next: null, result: null }],
        ["unfinished with a result", { matchFinished: false, next: question, result }],
    ])("rejects an inconsistent answer: %s", async (_name, extra) => {
        const { submitMatchAnswer, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(
            jsonResponse({ matchId: "m1", questionNumber: 1, submittedCharacterId: "a", ...extra }),
        );

        await expect(submitMatchAnswer("m1", 1, "a")).rejects.toBeInstanceOf(ApiResponseError);
    });

    it("refuses blank arguments before any request", async () => {
        const { submitMatchAnswer } = await loadModules();

        await expect(submitMatchAnswer(" ", 1, "a")).rejects.toThrow("matchId");
        await expect(submitMatchAnswer("m1", 1, " ")).rejects.toThrow("characterId");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe("getMatchQuestion and getMatchResult", () => {
    it("read the question and the result", async () => {
        const { getMatchQuestion, getMatchResult } = await loadModules();

        fetchMock.mockResolvedValueOnce(jsonResponse(question)).mockResolvedValueOnce(jsonResponse(result));

        await expect(getMatchQuestion("m1")).resolves.toEqual(question);
        await expect(getMatchResult("m1")).resolves.toEqual(result);
        expect(fetchMock.mock.calls[1][0]).toBe("http://api.test/api/games/who-said-it/matches/m1/result");
    });

    it("rejects a result whose score exceeds the maximum", async () => {
        const { getMatchResult, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ ...result, score: 110 }));

        await expect(getMatchResult("m1")).rejects.toBeInstanceOf(ApiResponseError);
    });
});
