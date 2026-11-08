const API_URL = process.env.API_URL;

export const API_TIMEOUT_MS = 15_000;

// For the first calls of a visit, which may reach a sleeping backend (Render Free): waking
// takes about a minute, and the lore load that follows can take up to 60 s more
// (wunderlore.lore.load-timeout). Stays well under the 300 s a Vercel Hobby function may run.
// Only for calls that are safe to wait for; everything else keeps API_TIMEOUT_MS, because by
// then the backend is awake and a long wait would only hide a real failure.
export const COLD_START_TIMEOUT_MS = 130_000;

export interface ApiFetchOptions {
    // Overrides API_TIMEOUT_MS for this call.
    timeoutMs?: number;
    // Opts a GET into Next.js's server-side fetch cache for this many seconds. Without it nothing
    // is cached (cache: "no-store"). Only 200 responses are stored; errors never are. Meant for
    // public reads that are the same for everyone; refused for any other method.
    revalidateSeconds?: number;
}

// Longer waits are treated as unknown rather than shown to the user.
const MAX_RETRY_AFTER_SECONDS = 3600;

export class ApiError extends Error {
    // retryAfterSeconds: the Retry-After wait, only when it is a usable positive delta-seconds value.
    constructor(
        message: string,
        public readonly status: number,
        public readonly retryAfterSeconds?: number,
    ) {
        super(message);
        this.name = "ApiError";
    }
}

// Deliberately has no status: a timeout is not an HTTP response.
export class ApiTimeoutError extends Error {
    constructor(public readonly timeoutMs: number) {
        super(`API request timed out after ${timeoutMs} ms`);
        this.name = "ApiTimeoutError";
    }
}

// Deliberately has no status and no payload: a 2xx response whose body breaks
// the contract is not an HTTP failure, and its content must not reach the logs.
export class ApiResponseError extends Error {
    constructor(public readonly endpoint: string) {
        super(`API response from ${endpoint} is invalid`);
        this.name = "ApiResponseError";
    }
}

// Retry-After may also be an HTTP date, which the backend never sends; anything
// but a positive whole number of seconds counts as "no known wait".
export function parseRetryAfter(value: string | null): number | undefined {
    const trimmed = value?.trim();

    if (!trimmed || !/^\d+$/.test(trimmed)) {
        return undefined;
    }

    const seconds = Number(trimmed);

    return seconds > 0 && seconds <= MAX_RETRY_AFTER_SECONDS ? seconds : undefined;
}

export type PayloadGuard<T> = (payload: unknown) => payload is T;

async function readJson(response: Response, endpoint: string): Promise<unknown> {
    try {
        return await response.json();
    } catch (error: unknown) {
        // Replaced, not wrapped: the SyntaxError message quotes the body.
        if (error instanceof SyntaxError) {
            throw new ApiResponseError(endpoint);
        }

        throw error;
    }
}

export async function apiFetch<T>(
    path: string,
    isValid: PayloadGuard<T>,
    init?: RequestInit,
    { timeoutMs = API_TIMEOUT_MS, revalidateSeconds }: ApiFetchOptions = {},
): Promise<T> {
    if (!API_URL) {
        throw new Error("API_URL is not configured");
    }

    const method = (init?.method ?? "GET").toUpperCase();

    if (revalidateSeconds !== undefined && (method !== "GET" || !(revalidateSeconds > 0))) {
        throw new Error("Only a GET can be cached, for a positive number of seconds");
    }

    // One controller aborts the request either when the timeout fires or when
    // the caller's own signal aborts, so neither cancels the other out.
    const controller = new AbortController();
    const callerSignal = init?.signal;
    const timeoutError = new ApiTimeoutError(timeoutMs);
    const abortFromCaller = () => controller.abort(callerSignal?.reason);

    if (callerSignal?.aborted) {
        abortFromCaller();
    } else {
        callerSignal?.addEventListener("abort", abortFromCaller, { once: true });
    }

    const timeoutId = setTimeout(
        () => controller.abort(timeoutError),
        timeoutMs,
    );

    try {
        const response = await fetch(
            `${API_URL.replace(/\/$/, "")}${path}`,
            {
                ...init,
                headers: {
                    Accept: "application/json",
                    ...init?.headers,
                },
                ...(revalidateSeconds === undefined
                    ? { cache: "no-store" as const }
                    : { cache: "force-cache" as const, next: { revalidate: revalidateSeconds } }),
                signal: controller.signal,
            },
        );

        if (!response.ok) {
            throw new ApiError(
                `API request failed with status ${response.status}`,
                response.status,
                parseRetryAfter(response.headers.get("Retry-After")),
            );
        }

        // Without the query string, which identifies the request, not the endpoint.
        const endpoint = path.split("?", 1)[0];

        // Awaited here so the timeout also covers a body that stalls.
        const payload = await readJson(response, endpoint);

        if (!isValid(payload)) {
            throw new ApiResponseError(endpoint);
        }

        return payload;
    } catch (error: unknown) {
        if (controller.signal.reason === timeoutError) {
            throw timeoutError;
        }

        throw error;
    } finally {
        clearTimeout(timeoutId);
        callerSignal?.removeEventListener("abort", abortFromCaller);
    }
}
