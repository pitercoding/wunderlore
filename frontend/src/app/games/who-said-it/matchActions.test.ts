import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import {
    createMatch,
    getMatchQuestion,
    getMatchResult,
    submitMatchAnswer,
} from "@/lib/api/whoSaidItMatch";
import { answerWhoSaidItMatch, fetchWhoSaidItMatchState, startWhoSaidItMatch } from "./matchActions";

vi.mock("@/lib/api/whoSaidItMatch", () => ({
    createMatch: vi.fn(),
    getMatchQuestion: vi.fn(),
    getMatchResult: vi.fn(),
    submitMatchAnswer: vi.fn(),
}));

const MATCH_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

const question = {
    matchId: MATCH_ID,
    questionNumber: 1,
    totalQuestions: 10,
    quote: "Q",
    options: [],
};

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("startWhoSaidItMatch", () => {
    it("returns the first question", async () => {
        vi.mocked(createMatch).mockResolvedValue(question);

        await expect(startWhoSaidItMatch()).resolves.toEqual({ ok: true, question });
    });

    it("names the wait on a 429", async () => {
        vi.mocked(createMatch).mockRejectedValue(new ApiError("x", 429, 45));

        expect(await startWhoSaidItMatch()).toMatchObject({
            ok: false,
            message: expect.stringContaining("45 seconds"),
        });
    });

    it("reports an outage without logging an expected 503", async () => {
        const log = vi.spyOn(console, "error").mockImplementation(() => {});

        vi.mocked(createMatch).mockRejectedValue(new ApiError("x", 503));

        expect(await startWhoSaidItMatch()).toMatchObject({ ok: false, message: expect.stringContaining("unavailable") });
        expect(log).not.toHaveBeenCalled();
    });

    it("logs and reports other failures", async () => {
        const log = vi.spyOn(console, "error").mockImplementation(() => {});

        vi.mocked(createMatch).mockRejectedValue(new Error("boom"));

        expect(await startWhoSaidItMatch()).toMatchObject({ ok: false, message: "Unable to start a match. Please try again." });
        expect(log).toHaveBeenCalled();
    });
});

describe("answerWhoSaidItMatch", () => {
    it("passes a valid answer to the backend", async () => {
        const answer = {
            matchId: MATCH_ID,
            questionNumber: 1,
            submittedCharacterId: "a",
            matchFinished: false,
            next: question,
            result: null,
        };

        vi.mocked(submitMatchAnswer).mockResolvedValue(answer);

        await expect(answerWhoSaidItMatch(MATCH_ID, 1, "a")).resolves.toEqual({ ok: true, answer });
        expect(submitMatchAnswer).toHaveBeenCalledWith(MATCH_ID, 1, "a");
    });

    it.each([
        ["a malformed match id", "nope", 1, "a"],
        ["question 0", MATCH_ID, 0, "a"],
        ["question 11", MATCH_ID, 11, "a"],
        ["a fractional question", MATCH_ID, 1.5, "a"],
        ["a string question", MATCH_ID, "1", "a"],
        ["a blank character", MATCH_ID, 1, " "],
        ["an over-long character", MATCH_ID, 1, "x".repeat(65)],
        ["a non-string character", MATCH_ID, 1, 7],
    ])("refuses %s without calling the backend", async (_name, id, number, character) => {
        expect(await answerWhoSaidItMatch(id, number, character)).toMatchObject({ ok: false, kind: "invalid" });
        expect(submitMatchAnswer).not.toHaveBeenCalled();
    });

    it.each([
        [400, "invalid"],
        [404, "terminal"],
        [409, "stale"],
        [503, "temporary"],
    ])("maps a %i to %s", async (status, kind) => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        vi.mocked(submitMatchAnswer).mockRejectedValue(new ApiError("x", status));

        expect(await answerWhoSaidItMatch(MATCH_ID, 1, "a")).toMatchObject({ ok: false, kind });
    });

    it("treats a network failure as temporary", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        vi.mocked(submitMatchAnswer).mockRejectedValue(new TypeError("fetch failed"));

        expect(await answerWhoSaidItMatch(MATCH_ID, 1, "a")).toMatchObject({ ok: false, kind: "temporary" });
    });
});

describe("fetchWhoSaidItMatchState", () => {
    it("returns the current question", async () => {
        vi.mocked(getMatchQuestion).mockResolvedValue(question);

        await expect(fetchWhoSaidItMatchState(MATCH_ID)).resolves.toEqual({ ok: true, question });
    });

    it("falls back to the result when the match is over (409)", async () => {
        const result = {
            matchId: MATCH_ID,
            totalQuestions: 10,
            correctAnswers: 10,
            incorrectAnswers: 0,
            score: 100,
            maxScore: 100,
            percentage: 100,
            questions: [],
        };

        vi.mocked(getMatchQuestion).mockRejectedValue(new ApiError("x", 409));
        vi.mocked(getMatchResult).mockResolvedValue(result);

        await expect(fetchWhoSaidItMatchState(MATCH_ID)).resolves.toEqual({ ok: true, result });
    });

    it("is terminal when the match expired", async () => {
        vi.mocked(getMatchQuestion).mockRejectedValue(new ApiError("x", 404));

        expect(await fetchWhoSaidItMatchState(MATCH_ID)).toMatchObject({ ok: false, terminal: true });
    });

    it("is not terminal on an outage", async () => {
        vi.mocked(getMatchQuestion).mockRejectedValue(new ApiError("x", 503));

        expect(await fetchWhoSaidItMatchState(MATCH_ID)).toMatchObject({ ok: false, terminal: false });
    });

    it("refuses a malformed id", async () => {
        expect(await fetchWhoSaidItMatchState("nope")).toMatchObject({ ok: false, terminal: true });
        expect(getMatchQuestion).not.toHaveBeenCalled();
    });
});
