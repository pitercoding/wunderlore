import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// The domain modules are exercised through the real apiFetch; only the
// network boundary (fetch) and the API_URL environment variable are stubbed.
async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    const [game, client] = await Promise.all([
        import("./game"),
        import("./client"),
    ]);
    return { ...game, ...client };
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

describe("getRound", () => {
    it("requests a new round and returns it", async () => {
        const { getRound } = await loadModules();
        const round = {
            roundId: "round-1",
            quote: "You shall not pass!",
            options: [
                { id: "c1", name: "Gandalf" },
                { id: "c2", name: "Frodo Baggins" },
            ],
        };
        fetchMock.mockResolvedValue(jsonResponse(round));

        await expect(getRound()).resolves.toEqual(round);
        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith(
            "http://api.test/api/game/round",
            expect.not.objectContaining({ method: expect.anything() }),
        );
    });

    it("propagates API errors with their status", async () => {
        const { getRound, ApiError } = await loadModules();
        fetchMock.mockResolvedValue(new Response(null, { status: 503 }));

        const error = await getRound().catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toHaveProperty("status", 503);
    });
});

describe("submitAnswer", () => {
    it("posts the answer as JSON and returns the result", async () => {
        const { submitAnswer } = await loadModules();
        const result = {
            roundId: "round-1",
            submittedCharacterId: "c2",
            correct: false,
            correctCharacterId: "c1",
        };
        fetchMock.mockResolvedValue(jsonResponse(result));

        await expect(submitAnswer("round-1", "c2")).resolves.toEqual(result);

        expect(fetchMock).toHaveBeenCalledTimes(1);
        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe("http://api.test/api/game/answer");
        expect(init).toMatchObject({
            method: "POST",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
            },
        });
        expect(JSON.parse(init?.body as string)).toEqual({
            roundId: "round-1",
            characterId: "c2",
        });
    });

    it.each([
        ["empty", ""],
        ["blank", "   "],
    ])("rejects a %s roundId before calling the API", async (_, roundId) => {
        const { submitAnswer } = await loadModules();

        await expect(submitAnswer(roundId, "c1")).rejects.toThrow(
            "roundId must be a non-empty string",
        );
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([
        ["empty", ""],
        ["blank", "\t "],
    ])("rejects a %s characterId before calling the API", async (_, characterId) => {
        const { submitAnswer } = await loadModules();

        await expect(submitAnswer("round-1", characterId)).rejects.toThrow(
            "characterId must be a non-empty string",
        );
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([400, 404, 409])(
        "propagates an ApiError with status %i",
        async (status) => {
            const { submitAnswer, ApiError } = await loadModules();
            fetchMock.mockResolvedValue(new Response(null, { status }));

            const error = await submitAnswer("round-1", "c1").catch(
                (e: unknown) => e,
            );

            expect(error).toBeInstanceOf(ApiError);
            expect(error).toHaveProperty("status", status);
        },
    );
});
