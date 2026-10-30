import { describe, expect, it } from "vitest";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import { unavailableMessage } from "./unavailable";

const GENERIC = "Wunderlore is temporarily unavailable. Please try again.";

describe("unavailableMessage", () => {
    it.each([
        [1, "1 second"],
        [30, "30 seconds"],
        [59, "59 seconds"],
        [60, "1 minute"],
        [61, "2 minutes"],
        [600, "10 minutes"],
    ])("names the wait for a 503 with Retry-After %i", (seconds, wait) => {
        expect(unavailableMessage(new ApiError("x", 503, seconds))).toBe(
            `Wunderlore is temporarily unavailable. Please try again in about ${wait}.`,
        );
    });

    it.each([
        ["no Retry-After", new ApiError("x", 503)],
        ["a zero wait", new ApiError("x", 503, 0)],
        ["a negative wait", new ApiError("x", 503, -3)],
        ["a NaN wait", new ApiError("x", 503, Number.NaN)],
        ["a fractional wait", new ApiError("x", 503, 1.5)],
        ["a timeout", new ApiTimeoutError(15_000)],
        ["a network error", new TypeError("fetch failed")],
    ])("is generic, without a wait, for %s", (_, error) => {
        expect(unavailableMessage(error)).toBe(GENERIC);
    });

    it.each([
        ["another status with Retry-After", new ApiError("x", 502, 30)],
        ["an invalid payload", new ApiResponseError("/api/x")],
        ["a plain error", new Error("API_URL is not configured")],
        ["a non-error", "boom"],
    ])("does not claim an outage for %s", (_, error) => {
        expect(unavailableMessage(error)).toBeUndefined();
    });

    it("never exposes technical details", () => {
        const message = unavailableMessage(new ApiError("API request failed with status 503", 503, 30));

        expect(message).not.toMatch(/503|status|API|The One/);
    });
});
