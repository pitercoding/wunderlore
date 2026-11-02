import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The domain modules are exercised through the real apiFetch; only the
// network boundary (fetch) and the API_URL environment variable are stubbed.
async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    const [whichCharacter, client] = await Promise.all([
        import("./whichCharacter"),
        import("./client"),
    ]);
    return { ...whichCharacter, ...client };
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

const QUESTIONS_ENDPOINT = "/api/games/which-character/questions";
const RESULT_ENDPOINT = "/api/games/which-character/result";

const validOption = { id: "q1-a", text: "Take the ring yourself" };

const validQuestion = {
    id: "q1",
    text: "What would you do?",
    options: [validOption, { id: "q1-b", text: "Hide it in the Shire" }],
};

const validQuestions = {
    questions: Array.from({ length: 10 }, (_, index) => ({
        ...validQuestion,
        id: `q${index + 1}`,
    })),
};

// Every question stays valid except the first one, which is replaced.
function questionsWithFirst(question: unknown) {
    return { questions: [question, ...validQuestions.questions.slice(1)] };
}

const validAnswers = Array.from({ length: 10 }, (_, index) => ({
    questionId: `q${index + 1}`,
    answerId: `q${index + 1}-a`,
}));

const validCharacter = {
    id: "GANDALF",
    name: "Gandalf",
    description: "A wise guide who sees further than most.",
};

const validResult = { character: validCharacter };

describe("getQuestions", () => {
    it("requests the questions and returns them", async () => {
        const { getQuestions } = await loadModules();
        fetchMock.mockResolvedValue(jsonResponse(validQuestions));

        await expect(getQuestions()).resolves.toEqual(validQuestions);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe(`http://api.test${QUESTIONS_ENDPOINT}`);
        expect(init?.method).toBeUndefined();
        expect(init?.body).toBeUndefined();
    });

    it.each([400, 404, 500, 503])(
        "propagates an ApiError with status %i",
        async (status) => {
            const { getQuestions, ApiError } = await loadModules();
            fetchMock.mockResolvedValue(new Response(null, { status }));

            const error = await getQuestions().catch((e: unknown) => e);

            expect(error).toBeInstanceOf(ApiError);
            expect(error).toHaveProperty("status", status);
        },
    );

    it("keeps the Retry-After wait of an unavailable service", async () => {
        const { getQuestions } = await loadModules();
        fetchMock.mockResolvedValue(
            new Response(null, { status: 503, headers: { "Retry-After": "30" } }),
        );

        const error = await getQuestions().catch((e: unknown) => e);

        expect(error).toMatchObject({ status: 503, retryAfterSeconds: 30 });
    });

    it("propagates network errors unchanged", async () => {
        const { getQuestions } = await loadModules();
        const networkError = new TypeError("fetch failed");
        fetchMock.mockRejectedValue(networkError);

        await expect(getQuestions()).rejects.toBe(networkError);
    });

    it("rejects a successful response that is not JSON", async () => {
        const { getQuestions, ApiResponseError } = await loadModules();
        fetchMock.mockResolvedValue(new Response("<html>oops</html>"));

        await expect(getQuestions()).rejects.toBeInstanceOf(ApiResponseError);
    });

    it("keeps fields the backend may add in the future", async () => {
        const { getQuestions } = await loadModules();
        const body = { ...validQuestions, version: 2 };
        fetchMock.mockResolvedValue(jsonResponse(body));

        await expect(getQuestions()).resolves.toEqual(body);
    });

    it.each([
        ["null", null],
        ["an array", validQuestions.questions],
        ["missing questions", {}],
        ["null questions", { questions: null }],
        ["questions that are not a list", { questions: validQuestion }],
        ["an empty questions list", { questions: [] }],
        ["nine questions", { questions: validQuestions.questions.slice(1) }],
        ["eleven questions", { questions: [...validQuestions.questions, validQuestion] }],
        ["a null question", questionsWithFirst(null)],
        ["a question without id", questionsWithFirst({ ...validQuestion, id: undefined })],
        ["a blank question id", questionsWithFirst({ ...validQuestion, id: "  " })],
        ["a numeric question id", questionsWithFirst({ ...validQuestion, id: 1 })],
        ["a question without text", questionsWithFirst({ ...validQuestion, text: undefined })],
        ["a null question text", questionsWithFirst({ ...validQuestion, text: null })],
        ["a question without options", questionsWithFirst({ ...validQuestion, options: undefined })],
        ["options that are not a list", questionsWithFirst({ ...validQuestion, options: validOption })],
        ["an empty options list", questionsWithFirst({ ...validQuestion, options: [] })],
        ["a null option", questionsWithFirst({ ...validQuestion, options: [null] })],
        ["an option without id", questionsWithFirst({ ...validQuestion, options: [{ text: "Run" }] })],
        ["an option without text", questionsWithFirst({ ...validQuestion, options: [{ id: "q1-a" }] })],
        ["an option with a numeric id", questionsWithFirst({ ...validQuestion, options: [{ id: 1, text: "Run" }] })],
        ["an option with a blank text", questionsWithFirst({ ...validQuestion, options: [{ id: "q1-a", text: " " }] })],
        ["a valid option next to an invalid one", questionsWithFirst({ ...validQuestion, options: [validOption, {}] })],
    ])("rejects %s", async (_, body) => {
        const { getQuestions, ApiResponseError } = await loadModules();
        fetchMock.mockResolvedValue(jsonResponse(body));

        const error = await getQuestions().catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiResponseError);
        expect(error).toHaveProperty("endpoint", QUESTIONS_ENDPOINT);
    });
});

describe("submitAnswers", () => {
    it("posts the ten answers as JSON and returns the result", async () => {
        const { submitAnswers } = await loadModules();
        fetchMock.mockResolvedValue(jsonResponse(validResult));

        await expect(submitAnswers(validAnswers)).resolves.toEqual(validResult);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe(`http://api.test${RESULT_ENDPOINT}`);
        expect(init).toMatchObject({
            method: "POST",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
            },
        });
        expect(JSON.parse(init?.body as string)).toEqual({ answers: validAnswers });
    });

    it("returns the character the backend chose without changing it", async () => {
        const { submitAnswers } = await loadModules();
        const character = { ...validCharacter, id: "A_FUTURE_CHARACTER" };
        fetchMock.mockResolvedValue(jsonResponse({ character }));

        await expect(submitAnswers(validAnswers)).resolves.toEqual({ character });
    });

    it("keeps fields the backend may add in the future", async () => {
        const { submitAnswers } = await loadModules();
        const body = {
            ...validResult,
            character: { ...validCharacter, imageUrl: "/gandalf.png" },
        };
        fetchMock.mockResolvedValue(jsonResponse(body));

        await expect(submitAnswers(validAnswers)).resolves.toEqual(body);
    });

    it.each([
        ["no answers", []],
        ["nine answers", validAnswers.slice(1)],
        ["eleven answers", [...validAnswers, validAnswers[0]]],
    ])("rejects %s before calling the API", async (_, answers) => {
        const { submitAnswers } = await loadModules();

        await expect(submitAnswers(answers)).rejects.toThrow(
            "answers must contain exactly 10 items",
        );
        expect(fetchMock).not.toHaveBeenCalled();
    });

    // Server actions receive their arguments at runtime, whatever the types say.
    it.each([
        ["undefined", undefined],
        ["null", null],
        ["an object", { answers: validAnswers }],
    ])("rejects %s instead of a list before calling the API", async (_, answers) => {
        const { submitAnswers } = await loadModules();

        await expect(submitAnswers(answers as never)).rejects.toThrow(
            "answers must contain exactly 10 items",
        );
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([
        ["a null answer", null],
        ["a missing questionId", { answerId: "q1-a" }],
        ["a missing answerId", { questionId: "q1" }],
        ["an empty questionId", { questionId: "", answerId: "q1-a" }],
        ["a blank answerId", { questionId: "q1", answerId: "\t " }],
        ["a numeric answerId", { questionId: "q1", answerId: 1 }],
    ])("rejects %s before calling the API", async (_, answer) => {
        const { submitAnswers } = await loadModules();
        const answers = [answer, ...validAnswers.slice(1)];

        await expect(submitAnswers(answers as never)).rejects.toThrow(
            "every answer must have a questionId and an answerId",
        );
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([400, 404, 500, 503])(
        "propagates an ApiError with status %i",
        async (status) => {
            const { submitAnswers, ApiError } = await loadModules();
            fetchMock.mockResolvedValue(new Response(null, { status }));

            const error = await submitAnswers(validAnswers).catch(
                (e: unknown) => e,
            );

            expect(error).toBeInstanceOf(ApiError);
            expect(error).toHaveProperty("status", status);
        },
    );

    it("does not expose the problem detail of a rejected submission", async () => {
        const { submitAnswers, ApiError } = await loadModules();
        fetchMock.mockResolvedValue(
            jsonResponse({ detail: "Invalid quiz answers" }, { status: 400 }),
        );

        const error = await submitAnswers(validAnswers).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toHaveProperty(
            "message",
            "API request failed with status 400",
        );
    });

    it("propagates network errors unchanged", async () => {
        const { submitAnswers } = await loadModules();
        const networkError = new TypeError("fetch failed");
        fetchMock.mockRejectedValue(networkError);

        await expect(submitAnswers(validAnswers)).rejects.toBe(networkError);
    });

    it("rejects a successful response that is not JSON", async () => {
        const { submitAnswers, ApiResponseError } = await loadModules();
        fetchMock.mockResolvedValue(new Response("<html>oops</html>"));

        await expect(submitAnswers(validAnswers)).rejects.toBeInstanceOf(
            ApiResponseError,
        );
    });

    it.each([
        ["null", null],
        ["an array", [validResult]],
        ["a bare character", validCharacter],
        ["a missing character", {}],
        ["a null character", { character: null }],
        ["a character that is a string", { character: "GANDALF" }],
        ["a character without id", { character: { ...validCharacter, id: undefined } }],
        ["a blank id", { character: { ...validCharacter, id: " " } }],
        ["a numeric id", { character: { ...validCharacter, id: 1 } }],
        ["a character without name", { character: { ...validCharacter, name: undefined } }],
        ["a null name", { character: { ...validCharacter, name: null } }],
        ["a character without description", { character: { ...validCharacter, description: undefined } }],
        ["a numeric description", { character: { ...validCharacter, description: 7 } }],
    ])("rejects %s", async (_, body) => {
        const { submitAnswers, ApiResponseError } = await loadModules();
        fetchMock.mockResolvedValue(jsonResponse(body));

        const error = await submitAnswers(validAnswers).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiResponseError);
        expect(error).toHaveProperty("endpoint", RESULT_ENDPOINT);
    });
});
