import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type {
    LoreChallengeAnswer,
    LoreChallengeQuestion,
    LoreChallengeResult,
} from "./loreChallenge";

// Same files the backend contract test reads; see contracts/lore-challenge/README.md.
// Vitest runs from the frontend directory.
const CONTRACTS_DIR = resolve(process.cwd(), "..", "contracts", "lore-challenge");

function fixtureText(name: string): string {
    return readFileSync(resolve(CONTRACTS_DIR, name), "utf-8");
}

function fixture<T>(name: string): T {
    return JSON.parse(fixtureText(name)) as T;
}

// The real apiFetch and production validators run; only fetch is stubbed.
async function loadModules() {
    vi.stubEnv("API_URL", "http://api.test");
    vi.resetModules();
    return import("./loreChallenge");
}

const fetchMock = vi.fn<typeof fetch>();

function serve(name: string) {
    fetchMock.mockResolvedValue(
        new Response(fixtureText(name), {
            headers: { "Content-Type": "application/json" },
        }),
    );
}

beforeEach(() => {
    vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
    fetchMock.mockReset();
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
});

describe("Lore Challenge contract fixtures", () => {
    it("question.json is accepted by createMatch and getCurrentQuestion unchanged", async () => {
        const { createMatch, getCurrentQuestion } = await loadModules();
        const expected = fixture<LoreChallengeQuestion>("question.json");

        serve("question.json");
        await expect(createMatch("ALL", "EASY")).resolves.toEqual(expected);

        serve("question.json");
        await expect(getCurrentQuestion(expected.matchId)).resolves.toEqual(expected);
    });

    it("answer.json is accepted by submitAnswer unchanged", async () => {
        const { submitAnswer } = await loadModules();
        const expected = fixture<LoreChallengeAnswer>("answer.json");

        serve("answer.json");

        await expect(
            submitAnswer(expected.matchId, expected.questionId, expected.submittedOptionId),
        ).resolves.toEqual(expected);
    });

    it("result.json is accepted by getResult unchanged", async () => {
        const { getResult } = await loadModules();
        const expected = fixture<LoreChallengeResult>("result.json");

        serve("result.json");

        await expect(getResult(expected.matchId)).resolves.toEqual(expected);
    });

    it("keeps the fields of each fixture the validators and types rely on", () => {
        // Explicit, so a failure names the property that changed.
        expect(Object.keys(fixture("question.json")).sort()).toEqual([
            "category", "difficulty", "matchId", "number", "options", "prompt",
            "questionCategory", "questionId", "total",
        ]);
        expect(Object.keys(fixture("answer.json")).sort()).toEqual([
            "answered", "correct", "correctOptionId", "explanation", "finished", "matchId",
            "pointsAwarded", "questionId", "score", "submittedOptionId", "total",
        ]);
        expect(Object.keys(fixture("result.json")).sort()).toEqual([
            "category", "correctAnswers", "difficulty", "durationSeconds", "incorrectAnswers",
            "matchId", "maxScore", "percentage", "rank", "score", "totalQuestions",
        ]);
    });
});
