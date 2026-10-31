import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { isRecord } from "./guards";

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

// For tests about transport: parsed JSON is never undefined, so all of it passes.
function acceptAny(payload: unknown): payload is unknown {
    return payload !== undefined;
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

        await expect(apiFetch("/api/anything", acceptAny)).rejects.toThrow(
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

        await apiFetch("/api/games/who-said-it/rounds", acceptAny);

        expect(fetchMock).toHaveBeenCalledWith(
            "http://api.test/api/games/who-said-it/rounds",
            expect.any(Object),
        );
    });

    it("preserves a base path and query string", async () => {
        const { apiFetch } = await loadClient("http://api.test/backend/");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/catalog/quotes?page=2&limit=20", acceptAny);

        expect(fetchMock).toHaveBeenCalledWith(
            "http://api.test/backend/api/catalog/quotes?page=2&limit=20",
            expect.any(Object),
        );
    });

    it("sends a JSON Accept header and disables caching by default", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/games/who-said-it/rounds", acceptAny);

        expect(fetchMock).toHaveBeenCalledWith(expect.any(String), {
            headers: { Accept: "application/json" },
            cache: "no-store",
            signal: expect.any(AbortSignal),
        });
    });

    it("forwards the request options and merges custom headers", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}));

        await apiFetch("/api/games/who-said-it/rounds/round-1/answer", acceptAny, {
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

        await apiFetch("/api/x", acceptAny, {
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

        await expect(apiFetch("/api/games/who-said-it/rounds", acceptAny)).resolves.toEqual(body);
    });

    it.each([400, 404, 409, 500, 503])(
        "rejects with an ApiError carrying status %i",
        async (status) => {
            const { apiFetch, ApiError } = await loadClient("http://api.test");
            fetchMock.mockResolvedValue(
                jsonResponse({ message: "ignored" }, { status }),
            );

            const error = await apiFetch("/api/x", acceptAny).catch((e: unknown) => e);

            expect(error).toBeInstanceOf(ApiError);
            expect(error).toMatchObject({
                status,
                message: `API request failed with status ${status}`,
            });
        },
    );

    it.each([
        ["30", 30],
        [" 12 ", 12],
        ["1", 1],
        ["3600", 3600],
    ])("reads Retry-After %j as %i seconds", async (header, seconds) => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(
            new Response(null, { status: 503, headers: { "Retry-After": header } }),
        );

        const error = await apiFetch("/api/x", acceptAny).catch((e: unknown) => e);

        expect(error).toMatchObject({ status: 503, retryAfterSeconds: seconds });
    });

    it.each([
        ["absent", undefined],
        ["empty", ""],
        ["zero", "0"],
        ["negative", "-5"],
        ["fractional", "1.5"],
        ["non-numeric", "soon"],
        ["an HTTP date", "Wed, 21 Oct 2026 07:28:00 GMT"],
        ["above one hour", "3601"],
        ["not a safe integer", "99999999999999999999"],
    ])("treats a %s Retry-After as no known wait", async (_, header) => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(
            new Response(null, {
                status: 503,
                headers: header === undefined ? {} : { "Retry-After": header },
            }),
        );

        const error = await apiFetch("/api/x", acceptAny).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({ status: 503, retryAfterSeconds: undefined });
    });

    it("does not read the body of an error response", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(new Response("not json", { status: 502 }));

        await expect(apiFetch("/api/x", acceptAny)).rejects.toBeInstanceOf(ApiError);
    });

    it("propagates network errors from fetch unchanged", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        const networkError = new TypeError("fetch failed");
        fetchMock.mockRejectedValue(networkError);

        await expect(apiFetch("/api/x", acceptAny)).rejects.toBe(networkError);
    });
});

describe("apiFetch payload validation", () => {
    it("returns a payload accepted by the guard", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({ ok: true }));

        await expect(apiFetch("/api/x", isRecord)).resolves.toEqual({ ok: true });
    });

    it.each([
        ["null", null],
        ["an array", [{ ok: true }]],
        ["a string", "ok"],
    ])("rejects %s when the guard expects an object", async (_, body) => {
        const { apiFetch, ApiResponseError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse(body));

        await expect(apiFetch("/api/x", isRecord)).rejects.toBeInstanceOf(
            ApiResponseError,
        );
    });

    it.each([
        ["malformed JSON", "<html>oops</html>"],
        ["an empty body", null],
    ])("rejects %s from a successful response", async (_, body) => {
        const { apiFetch, ApiResponseError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(new Response(body));

        await expect(apiFetch("/api/x", acceptAny)).rejects.toBeInstanceOf(
            ApiResponseError,
        );
    });

    it("identifies the endpoint without exposing the query or the body", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(new Response("token=abc123 is not JSON"));

        const error = await apiFetch("/api/x?page=2", acceptAny).catch(
            (e: unknown) => e,
        );

        expect(error).toMatchObject({
            name: "ApiResponseError",
            endpoint: "/api/x",
            message: "API response from /api/x is invalid",
        });
        expect(error).not.toHaveProperty("cause");
    });

    it("has no status, so it is never mistaken for an HTTP error", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse(null));

        const error = await apiFetch("/api/x", isRecord).catch((e: unknown) => e);

        expect(error).not.toBeInstanceOf(ApiError);
        expect(error).not.toHaveProperty("status");
    });

    it("reports an HTTP error without validating its body", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        let validated = false;
        const guard = (payload: unknown): payload is unknown => {
            validated = true;
            return acceptAny(payload);
        };
        fetchMock.mockResolvedValue(jsonResponse(null, { status: 503 }));

        const error = await apiFetch("/api/x", guard).catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toHaveProperty("status", 503);
        expect(validated).toBe(false);
    });

    it("propagates a connection lost while reading the body unchanged", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        const networkError = new TypeError("terminated");
        fetchMock.mockResolvedValue({
            ok: true,
            status: 200,
            json: () => Promise.reject(networkError),
        } as Response);

        await expect(apiFetch("/api/x", acceptAny)).rejects.toBe(networkError);
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

        await expect(apiFetch("/api/x", acceptAny)).resolves.toEqual({ ok: true });

        expect(vi.getTimerCount()).toBe(0);
        expect(signalPassedToFetch().aborted).toBe(false);
    });

    it("clears the timer when the request fails with an HTTP error", async () => {
        const { apiFetch, ApiError } = await loadClient("http://api.test");
        fetchMock.mockResolvedValue(jsonResponse({}, { status: 500 }));

        await expect(apiFetch("/api/x", acceptAny)).rejects.toBeInstanceOf(ApiError);

        expect(vi.getTimerCount()).toBe(0);
    });

    it("clears the timer when fetch fails with a network error", async () => {
        const { apiFetch } = await loadClient("http://api.test");
        const networkError = new TypeError("fetch failed");
        fetchMock.mockRejectedValue(networkError);

        await expect(apiFetch("/api/x", acceptAny)).rejects.toBe(networkError);

        expect(vi.getTimerCount()).toBe(0);
    });

    it("aborts the request and rejects with an ApiTimeoutError after the timeout", async () => {
        const { apiFetch, ApiError, ApiTimeoutError, API_TIMEOUT_MS } =
            await loadClient("http://api.test");
        hangingFetch();

        const result = apiFetch("/api/x", acceptAny).catch((e: unknown) => e);

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

        const result = apiFetch("/api/x", acceptAny).catch((e: unknown) => e);
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

        const result = apiFetch("/api/x", acceptAny).catch((e: unknown) => e);
        await vi.advanceTimersByTimeAsync(API_TIMEOUT_MS);

        await expect(result).resolves.toBeInstanceOf(ApiTimeoutError);
    });

    it("lets the caller cancel the request with its own signal", async () => {
        const { apiFetch, ApiTimeoutError } = await loadClient("http://api.test");
        hangingFetch();
        const caller = new AbortController();
        const reason = new Error("navigated away");

        const result = apiFetch("/api/x", acceptAny, { signal: caller.signal }).catch(
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
            apiFetch("/api/x", acceptAny, { signal: caller.signal }),
        ).rejects.toBe(reason);
        expect(signalPassedToFetch().aborted).toBe(true);
        expect(vi.getTimerCount()).toBe(0);
    });

    it("keeps the default timeout when the caller provides a signal", async () => {
        const { apiFetch, ApiTimeoutError, API_TIMEOUT_MS } =
            await loadClient("http://api.test");
        hangingFetch();
        const caller = new AbortController();

        const result = apiFetch("/api/x", acceptAny, { signal: caller.signal }).catch(
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

        await apiFetch("/api/x", acceptAny, { signal: caller.signal });
        caller.abort();

        expect(signalPassedToFetch()).not.toBe(caller.signal);
        expect(removeSpy).toHaveBeenCalledWith("abort", expect.any(Function));
        expect(signalPassedToFetch().aborted).toBe(false);
    });
});
