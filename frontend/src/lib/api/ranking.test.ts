import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    const [ranking, client] = await Promise.all([import("./ranking"), import("./client")]);
    return { ...ranking, ...client };
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

const entry = {
    position: 1,
    nickname: "Frodo",
    gameType: "who-said-it",
    percentage: 93.5,
    playedAt: "2026-03-01T10:15:30Z",
};

describe("fetchLeaderboard", () => {
    it("requests the global ranking without a filter", async () => {
        const { fetchLeaderboard } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ game: null, entries: [entry] }));

        await expect(fetchLeaderboard()).resolves.toEqual({ game: null, entries: [entry] });
        expect(fetchMock.mock.calls[0][0]).toBe("http://api.test/api/ranking");
    });

    it("is the only ranking call cached, for 30 seconds", async () => {
        const { fetchLeaderboard, LEADERBOARD_REVALIDATE_SECONDS } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ game: null, entries: [] }));

        await fetchLeaderboard();

        expect(LEADERBOARD_REVALIDATE_SECONDS).toBe(30);
        expect(fetchMock.mock.calls[0][1]).toMatchObject({
            cache: "force-cache",
            next: { revalidate: 30 },
        });
    });

    it.each([undefined, "who-said-it" as const])(
        "tags every leaderboard read (game: %s) with the tag a publish expires",
        async (game) => {
            const { fetchLeaderboard, LEADERBOARD_CACHE_TAG } = await loadModules();

            fetchMock.mockResolvedValue(jsonResponse({ game: game ?? null, entries: [] }));

            await fetchLeaderboard(game);

            expect(LEADERBOARD_CACHE_TAG).toBe("leaderboard");
            expect(fetchMock.mock.calls[0][1]).toMatchObject({
                next: { revalidate: 30, tags: [LEADERBOARD_CACHE_TAG] },
            });
        },
    );

    it("never serves a 503 as leaderboard data", async () => {
        const { fetchLeaderboard, ApiError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ game: null, entries: [] }, { status: 503 }));

        await expect(fetchLeaderboard()).rejects.toBeInstanceOf(ApiError);
    });

    it("sends the game filter", async () => {
        const { fetchLeaderboard } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ game: "who-am-i", entries: [] }));

        await fetchLeaderboard("who-am-i");
        expect(fetchMock.mock.calls[0][0]).toBe("http://api.test/api/ranking?game=who-am-i");
    });

    it.each([
        ["an unknown game", { game: "nope", entries: [] }],
        ["a missing entries list", { game: null }],
        ["a position of 0", { game: null, entries: [{ ...entry, position: 0 }] }],
        ["a percentage above 100", { game: null, entries: [{ ...entry, percentage: 101 }] }],
        ["an unknown game in an entry", { game: null, entries: [{ ...entry, gameType: "x" }] }],
        ["an invalid date", { game: null, entries: [{ ...entry, playedAt: "yesterday" }] }],
        ["a blank nickname", { game: null, entries: [{ ...entry, nickname: " " }] }],
    ])("rejects a payload with %s", async (_name, payload) => {
        const { fetchLeaderboard, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(payload));

        await expect(fetchLeaderboard()).rejects.toBeInstanceOf(ApiResponseError);
    });

    it("fails with ApiError on a 503", async () => {
        const { fetchLeaderboard, ApiError } = await loadModules();

        fetchMock.mockResolvedValue(new Response(null, { status: 503 }));

        await expect(fetchLeaderboard()).rejects.toBeInstanceOf(ApiError);
    });
});

describe("postRankingEntry", () => {
    const published = {
        gameType: "who-said-it",
        nickname: "Frodo",
        percentage: 70,
        playedAt: "2026-03-01T10:15:30Z",
        alreadyPublished: false,
    };

    it("sends only the game, its id and the nickname", async () => {
        const { postRankingEntry } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(published, { status: 201 }));

        await expect(postRankingEntry("who-said-it", "game-1", "Frodo")).resolves.toEqual(published);

        const [url, init] = fetchMock.mock.calls[0];

        expect(url).toBe("http://api.test/api/ranking/entries");
        expect(init?.method).toBe("POST");
        expect(init?.cache).toBe("no-store");
        expect(init).not.toHaveProperty("next");
        expect(JSON.parse(String(init?.body))).toEqual({
            gameType: "who-said-it",
            gameId: "game-1",
            nickname: "Frodo",
        });
    });

    it("rejects a response without the alreadyPublished flag", async () => {
        const { postRankingEntry, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ ...published, alreadyPublished: undefined }));

        await expect(postRankingEntry("who-said-it", "g", "Frodo")).rejects.toBeInstanceOf(
            ApiResponseError,
        );
    });
});
