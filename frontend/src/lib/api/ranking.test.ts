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
    gameType: "lore-challenge",
    division: "easy",
    percentage: 93.5,
    playedAt: "2026-03-01T10:15:30Z",
};

const board = { game: "lore-challenge", division: "easy", entries: [entry] };

const OVERVIEW_PAIRS = [
    ["lore-challenge", "easy"],
    ["lore-challenge", "medium"],
    ["lore-challenge", "hard"],
    ["who-am-i", "easy"],
    ["who-am-i", "medium"],
    ["who-am-i", "hard"],
    ["where-in-middle-earth", "easy"],
    ["where-in-middle-earth", "medium"],
    ["where-in-middle-earth", "hard"],
    ["who-said-it", "standard"],
] as const;

function overviewBody(overrides: Record<string, unknown> = {}) {
    return {
        groups: OVERVIEW_PAIRS.map(([game, division]) => ({ game, division, entries: [] })),
        ...overrides,
    };
}

describe("fetchLeaderboard", () => {
    it("requests one division of one game", async () => {
        const { fetchLeaderboard } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(board));

        await expect(fetchLeaderboard("lore-challenge", "easy")).resolves.toEqual(board);
        expect(fetchMock.mock.calls[0][0]).toBe(
            "http://api.test/api/ranking?game=lore-challenge&division=easy",
        );
    });

    it.each(OVERVIEW_PAIRS)("sends %s and %s as the filters", async (game, division) => {
        const { fetchLeaderboard } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ game, division, entries: [] }));

        await fetchLeaderboard(game, division);
        expect(fetchMock.mock.calls[0][0]).toBe(
            `http://api.test/api/ranking?game=${game}&division=${division}`,
        );
    });

    it.each([
        ["who-said-it", "easy"],
        ["lore-challenge", "standard"],
        ["who-am-i", "standard"],
    ] as const)("never sends the invalid pair %s / %s", async (game, division) => {
        const { fetchLeaderboard } = await loadModules();

        await expect(fetchLeaderboard(game, division)).rejects.toThrow();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("is cached for 30 seconds", async () => {
        const { fetchLeaderboard, LEADERBOARD_REVALIDATE_SECONDS } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(board));

        await fetchLeaderboard("lore-challenge", "easy");

        expect(LEADERBOARD_REVALIDATE_SECONDS).toBe(30);
        expect(fetchMock.mock.calls[0][1]).toMatchObject({
            cache: "force-cache",
            next: { revalidate: 30 },
        });
    });

    it("waits for a sleeping backend: the read times out at the cold-start limit, not at 15 seconds", async () => {
        vi.useFakeTimers();

        try {
            const { fetchLeaderboard, ApiTimeoutError, API_TIMEOUT_MS, COLD_START_TIMEOUT_MS } =
                await loadModules();

            fetchMock.mockImplementation(
                (_url, init) =>
                    new Promise((_resolve, reject) => {
                        init?.signal?.addEventListener("abort", () => reject(init.signal?.reason));
                    }),
            );

            const outcome = fetchLeaderboard("lore-challenge", "easy").then(
                () => "resolved",
                (error: unknown) => error,
            );

            await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS + 1_000);
            // Still waiting after the default timeout...
            let settled = false;
            void outcome.then(() => {
                settled = true;
            });
            await Promise.resolve();
            expect(settled).toBe(false);

            // ...and aborted only at the cold-start limit.
            await vi.advanceTimersByTimeAsync(COLD_START_TIMEOUT_MS);
            const error = await outcome;
            expect(error).toBeInstanceOf(ApiTimeoutError);
            expect((error as InstanceType<typeof ApiTimeoutError>).timeoutMs).toBe(130_000);
        } finally {
            vi.useRealTimers();
        }
    });

    it("tags every division read with the tag a publish expires", async () => {
        const { fetchLeaderboard, LEADERBOARD_CACHE_TAG } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse({ game: "who-said-it", division: "standard", entries: [] }));

        await fetchLeaderboard("who-said-it", "standard");

        expect(LEADERBOARD_CACHE_TAG).toBe("leaderboard");
        expect(fetchMock.mock.calls[0][1]).toMatchObject({
            next: { revalidate: 30, tags: [LEADERBOARD_CACHE_TAG] },
        });
    });

    it("never serves a 503 as leaderboard data", async () => {
        const { fetchLeaderboard, ApiError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(board, { status: 503 }));

        await expect(fetchLeaderboard("lore-challenge", "easy")).rejects.toBeInstanceOf(ApiError);
    });

    it("keeps the entries as the backend sent them: positions, ties and order", async () => {
        const { fetchLeaderboard } = await loadModules();
        const entries = [
            { ...entry, position: 1, nickname: "Aria", percentage: 90 },
            { ...entry, position: 2, nickname: "Bo", percentage: 80 },
            { ...entry, position: 2, nickname: "Cy", percentage: 80 },
            { ...entry, position: 4, nickname: "Di", percentage: 70 },
        ];

        fetchMock.mockResolvedValue(jsonResponse({ ...board, entries }));

        await expect(fetchLeaderboard("lore-challenge", "easy")).resolves.toEqual({ ...board, entries });
    });

    it.each([
        ["an unknown game", { ...board, game: "nope" }],
        ["an unknown division", { ...board, division: "expert" }],
        ["a division that game does not have", { game: "who-said-it", division: "easy", entries: [] }],
        ["a missing division", { game: "lore-challenge", entries: [] }],
        ["a null game (the old global ranking)", { game: null, entries: [] }],
        ["a missing entries list", { game: "lore-challenge", division: "easy" }],
        ["a position of 0", { ...board, entries: [{ ...entry, position: 0 }] }],
        ["a percentage above 100", { ...board, entries: [{ ...entry, percentage: 101 }] }],
        ["an entry of another game", { ...board, entries: [{ ...entry, gameType: "who-am-i" }] }],
        ["an entry of another division", { ...board, entries: [{ ...entry, division: "hard" }] }],
        ["an entry without a division", { ...board, entries: [{ ...entry, division: undefined }] }],
        ["an invalid date", { ...board, entries: [{ ...entry, playedAt: "yesterday" }] }],
        ["a blank nickname", { ...board, entries: [{ ...entry, nickname: " " }] }],
    ])("rejects a payload with %s", async (_name, payload) => {
        const { fetchLeaderboard, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(payload));

        await expect(fetchLeaderboard("lore-challenge", "easy")).rejects.toBeInstanceOf(ApiResponseError);
    });

    it("fails with ApiError on a 503", async () => {
        const { fetchLeaderboard, ApiError } = await loadModules();

        fetchMock.mockResolvedValue(new Response(null, { status: 503 }));

        await expect(fetchLeaderboard("lore-challenge", "easy")).rejects.toBeInstanceOf(ApiError);
    });
});

describe("fetchLeaderboardOverview", () => {
    it("requests the overview, with the same cache tag and lifetime as every ranking read", async () => {
        const { fetchLeaderboardOverview, LEADERBOARD_CACHE_TAG } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(overviewBody()));

        const overview = await fetchLeaderboardOverview();

        expect(fetchMock.mock.calls[0][0]).toBe("http://api.test/api/ranking/overview");
        expect(fetchMock.mock.calls[0][1]).toMatchObject({
            cache: "force-cache",
            next: { revalidate: 30, tags: [LEADERBOARD_CACHE_TAG] },
        });
        expect(overview.groups).toHaveLength(10);
    });

    it("keeps the groups and their entries as the backend sent them, empty ones included", async () => {
        const { fetchLeaderboardOverview } = await loadModules();
        const groups = OVERVIEW_PAIRS.map(([game, division]) => ({
            game,
            division,
            entries:
                game === "who-am-i" && division === "hard"
                    ? [
                          { ...entry, gameType: "who-am-i", division: "hard", position: 1, nickname: "A" },
                          { ...entry, gameType: "who-am-i", division: "hard", position: 1, nickname: "B" },
                          { ...entry, gameType: "who-am-i", division: "hard", position: 1, nickname: "C" },
                          { ...entry, gameType: "who-am-i", division: "hard", position: 1, nickname: "D" },
                      ]
                    : [],
        }));

        fetchMock.mockResolvedValue(jsonResponse({ groups }));

        await expect(fetchLeaderboardOverview()).resolves.toEqual({ groups });
    });

    it.each([
        ["no groups list", {}],
        ["nine groups", { groups: overviewBody().groups.slice(1) }],
        ["eleven groups", { groups: [...overviewBody().groups, overviewBody().groups[0]] }],
        [
            "a repeated group in place of another",
            { groups: [...overviewBody().groups.slice(1), overviewBody().groups[1]] },
        ],
        [
            "an invalid pair",
            {
                groups: overviewBody().groups.map((group, index) =>
                    index === 9 ? { ...group, division: "easy" } : group,
                ),
            },
        ],
        [
            "an unknown game (Which Character)",
            {
                groups: overviewBody().groups.map((group, index) =>
                    index === 0 ? { ...group, game: "which-character" } : group,
                ),
            },
        ],
        [
            "an entry under the wrong division",
            {
                groups: overviewBody().groups.map((group, index) =>
                    index === 0 ? { ...group, entries: [{ ...entry, division: "hard" }] } : group,
                ),
            },
        ],
        ["a group without entries", { groups: overviewBody().groups.map(({ game, division }) => ({ game, division })) }],
    ])("rejects a payload with %s", async (_name, payload) => {
        const { fetchLeaderboardOverview, ApiResponseError } = await loadModules();

        fetchMock.mockResolvedValue(jsonResponse(payload));

        await expect(fetchLeaderboardOverview()).rejects.toBeInstanceOf(ApiResponseError);
    });

    it("never serves a 503 as data", async () => {
        const { fetchLeaderboardOverview, ApiError } = await loadModules();

        fetchMock.mockResolvedValue(new Response(null, { status: 503 }));

        await expect(fetchLeaderboardOverview()).rejects.toBeInstanceOf(ApiError);
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

    it("keeps the default 15-second timeout", async () => {
        vi.useFakeTimers();

        try {
            const { postRankingEntry, ApiTimeoutError, API_TIMEOUT_MS } = await loadModules();

            fetchMock.mockImplementation(
                (_url, init) =>
                    new Promise((_resolve, reject) => {
                        init?.signal?.addEventListener("abort", () => reject(init.signal?.reason));
                    }),
            );

            const outcome = postRankingEntry("who-said-it", "game-1", "Frodo").then(
                () => "resolved",
                (error: unknown) => error,
            );

            await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS);

            const error = await outcome;
            expect(error).toBeInstanceOf(ApiTimeoutError);
            expect((error as InstanceType<typeof ApiTimeoutError>).timeoutMs).toBe(15_000);
        } finally {
            vi.useRealTimers();
        }
    });

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
