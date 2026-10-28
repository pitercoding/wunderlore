import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

// client.ts reads API_URL when the module is evaluated, so every test stubs
// the environment first and then imports a fresh copy of the module.
async function loadClient(apiUrl: string | undefined) {
    vi.stubEnv("API_URL", apiUrl);
    vi.resetModules();
    return import("./client");
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

describe("ApiError", () => {
    it("keeps the message, status and error name", async () => {
        const { ApiError } = await loadClient("http://api.test");
        const error = new ApiError("Not found", 404);

        expect(error).toBeInstanceOf(Error);
        expect(error.name).toBe("ApiError");
        expect(error.message).toBe("Not found");
        expect(error.status).toBe(404);
    });
});

describe("apiFetch", () => {
    it.each([
        ["missing", undefined],
        ["empty", ""],
    ])("rejects without calling fetch when API_URL is %s", async (_, value) => {
        const { apiFetch } = await loadClient(value);

        await expect(apiFetch("/api/anything")).rejects.toThrow(
            "API_URL is not configured",
        );
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it.each([
        ["without a trailing slash", "http://api.test"],
        ["with a trailing slash", "http://api.test/"],
    ])("joins the base URL %s with the path", async (_, baseUrl) => {
        const { apiFetch } = await loadClient(baseUrl);
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/game/round");

        expect(fetchMock).toHaveBeenCalledWith(
            "http://api.test/api/game/round",
            expect.any(Object),
        );
    });

    it("preserves a base path and query string", async () => {
        const { apiFetch } = await loadClient("http://api.test/backend/");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/catalog/quotes?page=2&limit=20");

        expect(fetchMock).toHaveBeenCalledWith(
            "http://api.test/backend/api/catalog/quotes?page=2&limit=20",
            expect.any(Object),
        );
    });

    it("sends a JSON Accept header and disables caching by default", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/game/round");

        expect(fetchMock).toHaveBeenCalledWith(expect.any(String), {
            headers: { Accept: "application/json" },
            cache: "no-store",
        });
    });

    it("forwards the request options and merges custom headers", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/game/answer", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: '{"a":1}',
        });

        expect(fetchMock).toHaveBeenCalledWith(expect.any(String), {
            method: "POST",
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
            },
            body: '{"a":1}',
            cache: "no-store",
        });
    });

    it("lets callers override the Accept header but never the cache mode", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/x", {
            headers: { Accept: "text/plain" },
            cache: "force-cache",
        });

        expect(fetchMock).toHaveBeenCalledWith(expect.any(String), {
            headers: { Accept: "text/plain" },
            cache: "no-store",
        });
    });

    it("resolves with the parsed JSON body on success", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        const body = { roundId: "r1", quote: "Hello", options: [] };
        fetchMock.mockResolvedValue(jsonResponse(body));

        await expect(apiFetch("/api/game/round")).resolves.toEqual(body);
    });

    it.each([400, 404, 409, 500, 503])(
        "rejects with an ApiError carrying status %i",
        async (status) => {
            const { apiFetch, ApiError } = await loadClient("http://api.test");
            fetchMock.mockResolvedValue(
                jsonResponse({ message: "ignored" }, { status }),
            );

            const error = await apiFetch("/api/x").catch((e: unknown) => e);

            expect(error).toBeInstanceOf(ApiError);
            expect(error).toMatchObject({
                status,
                message: `API request failed with status ${status}`,
            });
        },
    );

    it("does not read the body of an error response", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(new Response("not json", { status: 502 }));

        await expect(apiFetch("/api/x")).rejects.toBeInstanceOf(ApiError);
    });

    it("propagates a parsing error when a successful body is not JSON", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(new Response("<html>oops</html>"));

        const error = await apiFetch("/api/x").catch((e: unknown) => e);

        expect(error).toBeInstanceOf(SyntaxError);
        expect(error).not.toBeInstanceOf(ApiError);
    });

    it("propagates a parsing error when a successful body is empty", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(new Response(null, { status: 200 }));

        await expect(apiFetch("/api/x")).rejects.toBeInstanceOf(SyntaxError);
    });

    it("propagates network errors from fetch unchanged", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        const networkError = new TypeError("fetch failed");
        fetchMock.mockRejectedValue(networkError);

        await expect(apiFetch("/api/x")).rejects.toBe(networkError);
    });
});
