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
            signal: expect.any(AbortSignal),
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
            signal: expect.any(AbortSignal),
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
            signal: expect.any(AbortSignal),
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

describe("apiFetch timeout and cancellation", () => {
    // A fetch that never settles on its own, only when its signal aborts,
    // rejecting with the abort reason like the platform fetch does.
    function hangingFetch() {
        fetchMock.mockImplementation(
            (_input, init) =>
                new Promise<Response>((_, reject) => {
                    const signal = init?.signal;
                    signal?.addEventListener("abort", () =>
                        reject(signal.reason),
                    );
                }),
        );
    }

    function signalPassedToFetch() {
        const signal = fetchMock.mock.calls[0]?.[1]?.signal;
        if (!signal) {
            throw new Error("fetch was called without a signal");
        }
        return signal;
    }

    beforeEach(() => {
        vi.useFakeTimers();
    });

    afterEach(() => {
        vi.useRealTimers();
    });

    it("uses a 15 second default timeout", async () => {
        const { API_TIMEOUT_MS } = await loadClient("http://api.test");

        expect(API_TIMEOUT_MS).toBe(15_000);
    });

    it("resolves normally and clears the timer when the response arrives in time", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({ ok: true }));

        await expect(apiFetch("/api/x")).resolves.toEqual({ ok: true });

        expect(vi.getTimerCount()).toBe(0);
        expect(signalPassedToFetch().aborted).toBe(false);
    });

    it("clears the timer when the request fails with an HTTP error", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}, { status: 500 }));

        await expect(apiFetch("/api/x")).rejects.toBeInstanceOf(ApiError);

        expect(vi.getTimerCount()).toBe(0);
    });

    it("clears the timer when fetch fails with a network error", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        const networkError = new TypeError("fetch failed");
        fetchMock.mockRejectedValue(networkError);

        await expect(apiFetch("/api/x")).rejects.toBe(networkError);

        expect(vi.getTimerCount()).toBe(0);
    });

    it("aborts the request and rejects with an ApiTimeoutError after the timeout", async () => {
        const { apiFetch, ApiError, ApiTimeoutError, API_TIMEOUT_MS } =
            await loadClient("http://api.test");
        hangingFetch();

        const result = apiFetch("/api/x").catch((e: unknown) => e);

        await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS - 1);
        expect(signalPassedToFetch().aborted).toBe(false);

        await vi.advanceTimersByTimeAsync(1);
        const error = await result;

        expect(signalPassedToFetch().aborted).toBe(true);
        expect(error).toBeInstanceOf(ApiTimeoutError);
        expect(error).not.toBeInstanceOf(ApiError);
        expect(error).not.toHaveProperty("status");
        expect(error).toMatchObject({
            name: "ApiTimeoutError",
            message: "API request timed out after 15000 ms",
            timeoutMs: 15_000,
        });
        expect(vi.getTimerCount()).toBe(0);
    });

    it("reports a timeout even if fetch rejects with a generic abort error", async () => {
        const { apiFetch, ApiTimeoutError, API_TIMEOUT_MS } =
            await loadClient("http://api.test");
        fetchMock.mockImplementation(
            (_input, init) =>
                new Promise<Response>((_, reject) => {
                    init?.signal?.addEventListener("abort", () =>
                        reject(new DOMException("aborted", "AbortError")),
                    );
                }),
        );

        const result = apiFetch("/api/x").catch((e: unknown) => e);
        await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS);

        await expect(result).resolves.toBeInstanceOf(ApiTimeoutError);
    });

    it("applies the timeout while reading a stalled response body", async () => {
        const { apiFetch, ApiTimeoutError, API_TIMEOUT_MS } =
            await loadClient("http://api.test");
        fetchMock.mockImplementation(async (_input, init) => {
            const signal = init?.signal;
            return {
                ok: true,
                status: 200,
                json: () =>
                    new Promise((_, reject) => {
                        signal?.addEventListener("abort", () =>
                            reject(signal.reason),
                        );
                    }),
            } as Response;
        });

        const result = apiFetch("/api/x").catch((e: unknown) => e);
        await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS);

        await expect(result).resolves.toBeInstanceOf(ApiTimeoutError);
    });

    it("lets the caller cancel the request with its own signal", async () => {
        const { apiFetch, ApiTimeoutError } = await loadClient("http://api.test");
        hangingFetch();
        const caller = new AbortController();
        const reason = new Error("navigated away");

        const result = apiFetch("/api/x", { signal: caller.signal }).catch(
            (e: unknown) => e,
        );
        caller.abort(reason);
        const error = await result;

        expect(signalPassedToFetch().aborted).toBe(true);
        expect(error).toBe(reason);
        expect(error).not.toBeInstanceOf(ApiTimeoutError);
        expect(vi.getTimerCount()).toBe(0);
    });

    it("hands fetch an aborted signal when the caller signal is already aborted", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockImplementation(async (_input, init) => {
            throw init?.signal?.reason;
        });
        const caller = new AbortController();
        const reason = new Error("already cancelled");
        caller.abort(reason);

        await expect(
            apiFetch("/api/x", { signal: caller.signal }),
        ).rejects.toBe(reason);
        expect(signalPassedToFetch().aborted).toBe(true);
        expect(vi.getTimerCount()).toBe(0);
    });

    it("keeps the default timeout when the caller provides a signal", async () => {
        const { apiFetch, ApiTimeoutError, API_TIMEOUT_MS } =
            await loadClient("http://api.test");
        hangingFetch();
        const caller = new AbortController();

        const result = apiFetch("/api/x", { signal: caller.signal }).catch(
            (e: unknown) => e,
        );
        await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS);

        await expect(result).resolves.toBeInstanceOf(ApiTimeoutError);
        expect(caller.signal.aborted).toBe(false);
    });

    it("stops listening to the caller signal once the request settles", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}));
        const caller = new AbortController();
        const removeSpy = vi.spyOn(caller.signal, "removeEventListener");

        await apiFetch("/api/x", { signal: caller.signal });
        caller.abort();

        expect(signalPassedToFetch()).not.toBe(caller.signal);
        expect(removeSpy).toHaveBeenCalledWith("abort", expect.any(Function));
        expect(signalPassedToFetch().aborted).toBe(false);
    });
});
