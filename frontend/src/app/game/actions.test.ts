import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { submitAnswer } from "@/lib/api/game";
import { submitGameAnswer } from "./actions";

vi.mock("@/lib/api/game", () => ({
    submitAnswer: vi.fn(),
}));

const submitAnswerMock = vi.mocked(submitAnswer);

beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("submitGameAnswer", () => {
    it("forwards the round and character and returns the result", async () => {
        const result = {
            roundId: "round-1",
            submittedCharacterId: "c1",
            correct: true,
            correctCharacterId: "c1",
        };
        submitAnswerMock.mockResolvedValue(result);

        await expect(submitGameAnswer("round-1", "c1")).resolves.toEqual({
            ok: true,
            result,
        });
        expect(submitAnswerMock).toHaveBeenCalledWith("round-1", "c1");
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        [400, "Invalid answer. Please try again."],
        [404, "This round has expired. Start a new round."],
        [409, "This round has already been answered."],
        [503, "The game is temporarily unavailable. Please try again."],
    ])("maps HTTP status %i to a user message", async (status, message) => {
        submitAnswerMock.mockRejectedValue(
            new ApiError(`API request failed with status ${status}`, status),
        );

        await expect(submitGameAnswer("round-1", "c1")).resolves.toEqual({
            ok: false,
            message,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("uses the status of any error-like object, not only ApiError", async () => {
        submitAnswerMock.mockRejectedValue({ status: 409 });

        await expect(submitGameAnswer("round-1", "c1")).resolves.toEqual({
            ok: false,
            message: "This round has already been answered.",
        });
    });

    it.each([
        ["an unmapped HTTP status", new ApiError("boom", 500)],
        ["a validation error", new Error("roundId must be a non-empty string")],
        ["a network error", new TypeError("fetch failed")],
        ["a non-numeric status", { status: "404" }],
        ["a thrown string", "unexpected"],
        ["null", null],
    ])("returns a generic message and logs %s", async (_, error) => {
        submitAnswerMock.mockRejectedValue(error);

        await expect(submitGameAnswer("", "c1")).resolves.toEqual({
            ok: false,
            message: "Something went wrong. Please try again.",
        });
        expect(console.error).toHaveBeenCalledWith(
            "Failed to submit game answer",
            error,
        );
    });
});
