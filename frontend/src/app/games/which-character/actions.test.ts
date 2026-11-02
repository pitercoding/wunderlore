import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import { submitAnswers } from "@/lib/api/whichCharacter";
import { submitWhichCharacterAnswers } from "./actions";

vi.mock("@/lib/api/whichCharacter", () => ({
    submitAnswers: vi.fn(),
}));

const submitAnswersMock = vi.mocked(submitAnswers);

const answers = Array.from({ length: 10 }, (_, i) => ({
    questionId: `q${i + 1}`,
    answerId: `q${i + 1}-a1`,
}));

const character = { id: "gandalf", name: "Gandalf", description: "A wise wanderer." };

beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("submitWhichCharacterAnswers", () => {
    it("forwards the answers and returns the backend character", async () => {
        submitAnswersMock.mockResolvedValue({ character });

        await expect(submitWhichCharacterAnswers(answers)).resolves.toEqual({
            ok: true,
            character,
        });
        expect(submitAnswersMock).toHaveBeenCalledWith(answers);
        expect(console.error).not.toHaveBeenCalled();
    });

    it("forwards only questionId and answerId", async () => {
        submitAnswersMock.mockResolvedValue({ character });
        const tampered = answers.map((a) => ({ ...a, score: 99, character: "sauron" }));

        await submitWhichCharacterAnswers(tampered);

        expect(submitAnswersMock).toHaveBeenCalledWith(answers);
    });

    it.each([
        ["not an array", "nope"],
        ["too few answers", answers.slice(0, 9)],
        ["too many answers", [...answers, answers[0]]],
        ["a blank answerId", answers.map((a, i) => (i === 3 ? { ...a, answerId: " " } : a))],
        ["a missing questionId", answers.map((a, i) => (i === 3 ? { answerId: a.answerId } : a))],
        ["a null entry", answers.map((a, i) => (i === 3 ? null : a))],
        ["a repeated question", answers.map((a, i) => (i === 3 ? answers[0] : a))],
    ])("rejects %s without calling the API", async (_, input) => {
        await expect(
            submitWhichCharacterAnswers(input as never),
        ).resolves.toMatchObject({ ok: false, kind: "invalid" });
        expect(submitAnswersMock).not.toHaveBeenCalled();
    });

    it("maps a 400 to an invalid failure", async () => {
        submitAnswersMock.mockRejectedValue(new ApiError("API request failed with status 400", 400));

        await expect(submitWhichCharacterAnswers(answers)).resolves.toMatchObject({
            ok: false,
            kind: "invalid",
        });
    });

    it.each([
        ["a 503", new ApiError("API request failed with status 503", 503), "Wunderlore is temporarily unavailable. Please try again.", false],
        ["a 503 with a retry time", new ApiError("API request failed with status 503", 503, 30), "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.", false],
        ["a timeout", new ApiTimeoutError(15_000), "Wunderlore is temporarily unavailable. Please try again.", true],
        ["a network error", new TypeError("fetch failed"), "Wunderlore is temporarily unavailable. Please try again.", true],
        ["a 500", new ApiError("API request failed with status 500", 500), "Something went wrong. Please try again.", true],
        ["an invalid payload", new ApiResponseError("/api/games/which-character/result"), "Something went wrong. Please try again.", true],
        ["a configuration error", new Error("API_URL is not configured"), "Something went wrong. Please try again.", true],
    ])("maps %s to a temporary failure with a safe message", async (_, error, message, logged) => {
        submitAnswersMock.mockRejectedValue(error);

        const response = await submitWhichCharacterAnswers(answers);

        expect(response).toEqual({ ok: false, kind: "temporary", message });
        expect(JSON.stringify(response)).not.toContain("API_URL");
        if (logged) {
            expect(console.error).toHaveBeenCalledWith(
                "Failed to submit Which Character Are You answers",
                error,
            );
        } else {
            expect(console.error).not.toHaveBeenCalled();
        }
    });
});
