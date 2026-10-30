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

const quote = {
    _id: "q1",
    dialog: "Deeds will not be less valiant because they are unpraised.",
    movie: "m1",
    character: "ch1",
    id: "q1",
};

const quotePage = {
    docs: [quote],
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

    it("accepts the page the backend sends, with a null offset", async () => {
        const { getQuotes } = await loadModules();
        const page = { ...quotePage, offset: null };
        fetchMock.mockResolvedValue(Response.json(page));

        await expect(getQuotes()).resolves.toEqual(page);
    });

    it("accepts a page without offset", async () => {
        const { getQuotes } = await loadModules();
        const page: Partial<typeof quotePage> = { ...quotePage };
        delete page.offset;
        fetchMock.mockResolvedValue(Response.json(page));

        await expect(getQuotes()).resolves.toEqual(page);
    });

    it.each([
        ["past the last page", { docs: [], page: 9, pages: 3 }],
        ["of an empty catalog", { docs: [], total: 0, pages: 0 }],
    ])("accepts a page %s", async (_, overrides) => {
        const { getQuotes } = await loadModules();
        const page = { ...quotePage, ...overrides };
        fetchMock.mockResolvedValue(Response.json(page));

        await expect(getQuotes()).resolves.toEqual(page);
    });

    // The catalog is unfiltered: ExternalQuoteController serves these on purpose.
    it.each([
        ["an empty dialog", { dialog: "" }],
        ["a null dialog", { dialog: null }],
        ["a null character", { character: null }],
        ["a null movie and id", { movie: null, id: null }],
    ])("accepts a quote with %s", async (_, overrides) => {
        const { getQuotes } = await loadModules();
        const page = { ...quotePage, docs: [{ ...quote, ...overrides }] };
        fetchMock.mockResolvedValue(Response.json(page));

        await expect(getQuotes()).resolves.toEqual(page);
    });

    it("keeps fields the backend may add in the future", async () => {
        const { getQuotes } = await loadModules();
        const page = {
            ...quotePage,
            hasMore: true,
            docs: [{ ...quote, language: "en" }],
        };
        fetchMock.mockResolvedValue(Response.json(page));

        await expect(getQuotes()).resolves.toEqual(page);
    });

    it.each([
        ["null", null],
        ["an array", [quotePage]],
        ["missing docs", { ...quotePage, docs: undefined }],
        ["docs that are not a list", { ...quotePage, docs: quote }],
        ["a null quote", { ...quotePage, docs: [null] }],
        ["a quote without _id", { ...quotePage, docs: [{ ...quote, _id: undefined }] }],
        ["a quote with a null _id", { ...quotePage, docs: [{ ...quote, _id: null }] }],
        ["a numeric dialog", { ...quotePage, docs: [{ ...quote, dialog: 7 }] }],
        ["an object as character", { ...quotePage, docs: [{ ...quote, character: {} }] }],
        ["a valid quote next to an invalid one", { ...quotePage, docs: [quote, {}] }],
        ["a missing total", { ...quotePage, total: undefined }],
        ["a fractional total", { ...quotePage, total: 1.5 }],
        ["a negative total", { ...quotePage, total: -1 }],
        ["a limit of zero", { ...quotePage, limit: 0 }],
        ["a text offset", { ...quotePage, offset: "0" }],
        ["a page of zero", { ...quotePage, page: 0 }],
        ["a text page", { ...quotePage, page: "1" }],
        ["null pages", { ...quotePage, pages: null }],
    ])("rejects %s", async (_, body) => {
        const { getQuotes, ApiResponseError } = await loadModules();
        fetchMock.mockResolvedValue(Response.json(body));

        const error = await getQuotes(2).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiResponseError);
        expect(error).toHaveProperty("endpoint", "/api/catalog/quotes");
    });
});
