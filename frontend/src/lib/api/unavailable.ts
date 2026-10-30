import { ApiError, ApiTimeoutError } from "@/lib/api/client";

// Wording shared by /game and /quotes for a temporarily unavailable service.
function formatWait(seconds: number): string {
    if (seconds < 60) {
        return `${seconds} ${seconds === 1 ? "second" : "seconds"}`;
    }

    const minutes = Math.ceil(seconds / 60);

    return `${minutes} ${minutes === 1 ? "minute" : "minutes"}`;
}

// A temporary outage: a 503, a timeout or a network failure (fetch rejects with a TypeError).
// Invalid payloads and other statuses are different failures and return undefined.
// With a known Retry-After the message names the wait instead of inviting an immediate retry.
export function unavailableMessage(error: unknown): string | undefined {
    if (error instanceof ApiError && error.status === 503) {
        const wait = error.retryAfterSeconds;

        if (wait !== undefined && Number.isInteger(wait) && wait > 0) {
            return `Wunderlore is temporarily unavailable. Please try again in about ${formatWait(wait)}.`;
        }

        return "Wunderlore is temporarily unavailable. Please try again.";
    }

    if (error instanceof ApiTimeoutError || error instanceof TypeError) {
        return "Wunderlore is temporarily unavailable. Please try again.";
    }

    return undefined;
}
