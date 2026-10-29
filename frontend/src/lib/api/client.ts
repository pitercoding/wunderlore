const API_URL = process.env.API_URL;

export const API_TIMEOUT_MS = 15_000;

export class ApiError extends Error {
    constructor(
        message: string,
        public readonly status: number,
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

export async function apiFetch<T>(
    path: string,
    init?: RequestInit,
): Promise<T> {
    if (!API_URL) {
        throw new Error("API_URL is not configured");
    }

    // One controller aborts the request either when the timeout fires or when
    // the caller's own signal aborts, so neither cancels the other out.
    const controller = new AbortController();
    const callerSignal = init?.signal;
    const timeoutError = new ApiTimeoutError(API_TIMEOUT_MS);
    const abortFromCaller = () => controller.abort(callerSignal?.reason);

    if (callerSignal?.aborted) {
        abortFromCaller();
    } else {
        callerSignal?.addEventListener("abort", abortFromCaller, { once: true });
    }

    const timeoutId = setTimeout(
        () => controller.abort(timeoutError),
        API_TIMEOUT_MS,
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
                cache: "no-store",
                signal: controller.signal,
            },
        );

        if (!response.ok) {
            throw new ApiError(
                `API request failed with status ${response.status}`,
                response.status,
            );
        }

        // Awaited here so the timeout also covers a body that stalls.
        return (await response.json()) as T;
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
