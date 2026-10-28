import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// Exercised through the real apiFetch; only fetch and API_URL are stubbed.
async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    const [quotes, client] = await Promise.all([
        import("./quotes"),
        import("./client"),
    ]);
    return { ...quotes, ...client };
}

const quotePage = {
    docs: [
        {
            _id: "q1",
            dialog: "Deeds will not be less valiant because they are unpraised.",
            movie: "m1",
            character: "ch1",
            id: "q1",
        },
    ],
    total: 1,
    limit: 20,
    offset: 0,
    page: 1,
    pages: 1,
};

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

function requestedUrl() {
    return new URL(fetchMock.mock.calls[0][0] as string);
}

describe("getQuotes", () => {
    it("uses page 1 and limit 20 by default", async () => {
        const { getQuotes } = await loadModules();
        fetchMock.mockResolvedValue(Response.json(quotePage));

        await expect(getQuotes()).resolves.toEqual(quotePage);

        const url = requestedUrl();
        expect(url.origin + url.pathname).toBe(
            "http://api.test/api/catalog/quotes",
        );
        expect(Object.fromEntries(url.searchParams)).toEqual({
            page: "1",
            limit: "20",
        });
    });

    it.each([
        [3, 1],
        [1, 100],
        [42, 50],
    ])("sends page=%i and limit=%i as query parameters", async (page, limit) => {
        const { getQuotes } = await loadModules();
        fetchMock.mockResolvedValue(Response.json(quotePage));

        await getQuotes(page, limit);

        expect(Object.fromEntries(requestedUrl().searchParams)).toEqual({
            page: String(page),
            limit: String(limit),
        });
    });

    it.each([0, -1, 1.5, Number.NaN, Number.POSITIVE_INFINITY])(
        "rejects page %s before calling the API",
        async (page) => {
            const { getQuotes } = await loadModules();

            await expect(getQuotes(page)).rejects.toThrow(
                "page must be a positive integer",
            );
            expect(fetchMock).not.toHaveBeenCalled();
        },
    );

    it.each([0, 101, -5, 2.5, Number.NaN])(
        "rejects limit %s before calling the API",
        async (limit) => {
            const { getQuotes } = await loadModules();

            await expect(getQuotes(1, limit)).rejects.toThrow(
                "limit must be an integer between 1 and 100",
            );
            expect(fetchMock).not.toHaveBeenCalled();
        },
    );

    it("propagates API errors with their status", async () => {
        const { getQuotes, ApiError } = await loadModules();
        fetchMock.mockResolvedValue(new Response(null, { status: 502 }));

        const error = await getQuotes(2).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toHaveProperty("status", 502);
    });
});
