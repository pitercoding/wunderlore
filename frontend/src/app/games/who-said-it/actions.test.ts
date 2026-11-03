import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import { createRound, submitAnswer } from "@/lib/api/whoSaidIt";
import { createWhoSaidItRound, submitWhoSaidItAnswer } from "./actions";

vi.mock("@/lib/api/whoSaidIt", () => ({
    createRound: vi.fn(),
    submitAnswer: vi.fn(),
}));

const createRoundMock = vi.mocked(createRound);
const submitAnswerMock = vi.mocked(submitAnswer);

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";
const COOLDOWN =
    "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.";
const UNEXPECTED = "Unable to load a game round. Please try again.";

// The format WhoSaidItRoundRegistry issues (UUID.randomUUID()).
const ROUND_ID = "123e4567-e89b-42d3-a456-426614174000";
const INVALID_OPTION = {
    ok: false,
    kind: "invalid",
    message: "That isn't a valid option for this round.",
};

beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("createWhoSaidItRound", () => {
    it("returns the round the backend created", async () => {
        const round = {
            roundId: "round-1",
            quote: "Fly, you fools!",
            options: [{ id: "c1", name: "Gandalf" }],
        };
        createRoundMock.mockResolvedValue(round);

        await expect(createWhoSaidItRound()).resolves.toEqual({ ok: true, round });
        expect(createRoundMock).toHaveBeenCalledTimes(1);
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        ["a 503 without a retry time", new ApiError("API request failed with status 503", 503), UNAVAILABLE],
        ["a 503 with an invalid retry time", new ApiError("API request failed with status 503", 503, Number.NaN), UNAVAILABLE],
        ["a 503 with a retry time", new ApiError("API request failed with status 503", 503, 30), COOLDOWN],
    ])("reports %s as temporarily unavailable without logging it", async (_, error, message) => {
        createRoundMock.mockRejectedValue(error);

        await expect(createWhoSaidItRound()).resolves.toEqual({ ok: false, message });
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        ["a timeout", new ApiTimeoutError(15_000), UNAVAILABLE],
        ["a network error", new TypeError("fetch failed"), UNAVAILABLE],
        ["an unmapped API error", new ApiError("API request failed with status 502", 502), UNEXPECTED],
        ["an invalid payload", new ApiResponseError("/api/games/who-said-it/rounds"), UNEXPECTED],
        ["an unexpected error", new Error("boom: internal detail"), UNEXPECTED],
    ])("maps %s to a message and logs it", async (_, error, message) => {
        createRoundMock.mockRejectedValue(error);

        await expect(createWhoSaidItRound()).resolves.toEqual({ ok: false, message });
        expect(console.error).toHaveBeenCalledWith(
            "Failed to create Who Said It round",
            error,
        );
    });

    it("never puts error details in the message", async () => {
        createRoundMock.mockRejectedValue(new Error("secret internal detail"));

        const response = await createWhoSaidItRound();

        expect(JSON.stringify(response)).not.toContain("secret internal detail");
    });
});

describe("submitWhoSaidItAnswer", () => {
    it("forwards the round and character and returns the result", async () => {
        const result = {
            roundId: ROUND_ID,
            submittedCharacterId: "c1",
            correct: true,
            correctCharacterId: "c1",
        };
        submitAnswerMock.mockResolvedValue(result);

        await expect(submitWhoSaidItAnswer(ROUND_ID, "c1")).resolves.toEqual({
            ok: true,
            result,
        });
        expect(submitAnswerMock).toHaveBeenCalledWith(ROUND_ID, "c1");
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        [400, "invalid", "That isn't a valid option for this round."],
        [404, "terminal", "This round has expired or is no longer available."],
        [409, "terminal", "This round has already been answered."],
        [503, "temporary", "Wunderlore is temporarily unavailable. Please try again."],
    ])("maps HTTP status %i to a failure kind and a user message", async (status, kind, message) => {
        submitAnswerMock.mockRejectedValue(
            new ApiError(`API request failed with status ${status}`, status),
        );

        await expect(submitWhoSaidItAnswer(ROUND_ID, "c1")).resolves.toEqual({
            ok: false,
            kind,
            message,
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it("uses the status of any error-like object, not only ApiError", async () => {
        submitAnswerMock.mockRejectedValue({ status: 409 });

        await expect(submitWhoSaidItAnswer(ROUND_ID, "c1")).resolves.toEqual({
            ok: false,
            kind: "terminal",
            message: "This round has already been answered.",
        });
    });

    it("tells the player how long to wait when the 503 carries a retry time", async () => {
        submitAnswerMock.mockRejectedValue(
            new ApiError("API request failed with status 503", 503, 30),
        );

        await expect(submitWhoSaidItAnswer(ROUND_ID, "c1")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message:
                "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.",
        });
        expect(console.error).not.toHaveBeenCalled();
    });

    it.each([
        ["a network error", new TypeError("fetch failed")],
        ["a timeout", new ApiTimeoutError(15_000)],
    ])("treats %s as temporarily unavailable, with a log", async (_, error) => {
        submitAnswerMock.mockRejectedValue(error);

        await expect(submitWhoSaidItAnswer(ROUND_ID, "c1")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: "Wunderlore is temporarily unavailable. Please try again.",
        });
        expect(console.error).toHaveBeenCalledWith(
            "Failed to submit Who Said It answer",
            error,
        );
    });

    it.each([
        ["an unmapped HTTP status", new ApiError("boom", 500)],
        ["a validation error", new Error("roundId must be a non-empty string")],
        ["an invalid payload", new ApiResponseError(`/api/games/who-said-it/rounds/${ROUND_ID}/answer`)],
        ["a non-numeric status", { status: "404" }],
        ["a thrown string", "unexpected"],
        ["null", null],
    ])("treats %s as temporary, with a generic message and a log", async (_, error) => {
        submitAnswerMock.mockRejectedValue(error);

        await expect(submitWhoSaidItAnswer(ROUND_ID, "c1")).resolves.toEqual({
            ok: false,
            kind: "temporary",
            message: "Something went wrong. Please try again.",
        });
        expect(console.error).toHaveBeenCalledWith(
            "Failed to submit Who Said It answer",
            error,
        );
    });

    describe("argument validation", () => {
        it("accepts a UUID in any letter case and a character id of up to 64 characters", async () => {
            const result = {
                roundId: ROUND_ID,
                submittedCharacterId: "x".repeat(64),
                correct: false,
                correctCharacterId: "c1",
            };
            submitAnswerMock.mockResolvedValue(result);

            await expect(
                submitWhoSaidItAnswer(ROUND_ID.toUpperCase(), "x".repeat(64)),
            ).resolves.toEqual({ ok: true, result });
            expect(submitAnswerMock).toHaveBeenCalledWith(
                ROUND_ID.toUpperCase(),
                "x".repeat(64),
            );
        });

        it("accepts the 24-character ids The One API issues", async () => {
            submitAnswerMock.mockResolvedValue({
                roundId: ROUND_ID,
                submittedCharacterId: "5cd99d4bde30eff6ebccfbbe",
                correct: true,
                correctCharacterId: "5cd99d4bde30eff6ebccfbbe",
            });

            await expect(
                submitWhoSaidItAnswer(ROUND_ID, "5cd99d4bde30eff6ebccfbbe"),
            ).resolves.toMatchObject({ ok: true });
        });

        it.each([
            ["a short placeholder", "round-1"],
            ["an empty string", ""],
            ["only spaces", "   "],
            ["text that is not a UUID", "not-a-uuid"],
            ["a UUID with trailing text", `${ROUND_ID}x`],
            ["a UUID with a trailing newline", `${ROUND_ID}
`],
            ["a UUID with a leading space", ` ${ROUND_ID}`],
            ["a UUID without hyphens", ROUND_ID.replaceAll("-", "")],
            ["a path traversal", "../../admin"],
            ["a URL-encoded id", "%2e%2e%2f"],
            ["an oversized string", "a".repeat(100_000)],
            ["null", null],
            ["undefined", undefined],
            ["a number", 123],
            ["a boolean", true],
            ["an array", [ROUND_ID]],
            ["an object", { toString: () => ROUND_ID }],
        ])("rejects a round id that is %s without calling the backend", async (_, roundId) => {
            await expect(submitWhoSaidItAnswer(roundId, "c1")).resolves.toEqual(INVALID_OPTION);

            expect(submitAnswerMock).not.toHaveBeenCalled();
            expect(console.error).not.toHaveBeenCalled();
        });

        it.each([
            ["an empty string", ""],
            ["only spaces", "   "],
            ["one character over the limit", "x".repeat(65)],
            ["an oversized string", "x".repeat(100_000)],
            ["null", null],
            ["undefined", undefined],
            ["a number", 42],
            ["a boolean", false],
            ["an array", ["c1"]],
            ["an object", { id: "c1" }],
        ])("rejects a character id that is %s without calling the backend", async (_, characterId) => {
            await expect(submitWhoSaidItAnswer(ROUND_ID, characterId)).resolves.toEqual(
                INVALID_OPTION,
            );

            expect(submitAnswerMock).not.toHaveBeenCalled();
            expect(console.error).not.toHaveBeenCalled();
        });

        it("rejects missing arguments without calling the backend", async () => {
            const withoutArguments = submitWhoSaidItAnswer as (
                ...args: unknown[]
            ) => ReturnType<typeof submitWhoSaidItAnswer>;

            await expect(withoutArguments()).resolves.toEqual(INVALID_OPTION);
            await expect(withoutArguments(ROUND_ID)).resolves.toEqual(INVALID_OPTION);

            expect(submitAnswerMock).not.toHaveBeenCalled();
            expect(console.error).not.toHaveBeenCalled();
        });

        it("does not echo the rejected input back", async () => {
            const response = await submitWhoSaidItAnswer("secret-looking-value", "c1");

            expect(JSON.stringify(response)).not.toContain("secret-looking-value");
        });
    });
});
