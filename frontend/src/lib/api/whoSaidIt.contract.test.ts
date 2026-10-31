import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { WhoSaidItAnswerResult, WhoSaidItRound } from "./whoSaidIt";

// Same files the backend contract test reads; see contracts/who-said-it/README.md.
// Vitest runs from the frontend directory.
const CONTRACTS_DIR = resolve(process.cwd(), "..", "contracts", "who-said-it");

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
    return import("./whoSaidIt");
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

describe("Who Said It contract fixtures", () => {
    it("round-created.json is accepted by createRound unchanged", async () => {
        const { createRound } = await loadModules();
        serve("round-created.json");

        const round = await createRound();

        expect(round).toEqual(fixture("round-created.json"));
        // The declared type must cover every property of the fixture.
        const expected: WhoSaidItRound = round;
        expect(Object.keys(expected).sort()).toEqual(["options", "quote", "roundId"]);
        expect(typeof expected.roundId).toBe("string");
        expect(typeof expected.quote).toBe("string");
        expect(expected.options.length).toBeGreaterThan(0);
        for (const option of expected.options) {
            expect(Object.keys(option).sort()).toEqual(["id", "name"]);
            expect(typeof option.id).toBe("string");
            expect(typeof option.name).toBe("string");
        }
    });

    it.each([
        ["answer-correct.json", true],
        ["answer-incorrect.json", false],
    ])("%s is accepted by submitAnswer unchanged", async (name, correct) => {
        const { submitAnswer } = await loadModules();
        const expected = fixture<WhoSaidItAnswerResult>(name);
        serve(name);

        const result = await submitAnswer(expected.roundId, expected.submittedCharacterId);

        expect(result).toEqual(expected);
        expect(Object.keys(result).sort()).toEqual([
            "correct",
            "correctCharacterId",
            "roundId",
            "submittedCharacterId",
        ]);
        expect(typeof result.roundId).toBe("string");
        expect(typeof result.submittedCharacterId).toBe("string");
        expect(typeof result.correct).toBe("boolean");
        expect(typeof result.correctCharacterId).toBe("string");
        expect(result.correct).toBe(correct);
    });

    it("answer fixtures agree on whether the submitted character is the correct one", () => {
        for (const name of ["answer-correct.json", "answer-incorrect.json"]) {
            const answer = fixture<WhoSaidItAnswerResult>(name);
            expect(answer.correct).toBe(
                answer.submittedCharacterId === answer.correctCharacterId,
            );
        }
    });

    it("the round fixture lists the character every answer fixture refers to", () => {
        const round = fixture<WhoSaidItRound>("round-created.json");
        const ids = round.options.map((option) => option.id);

        for (const name of ["answer-correct.json", "answer-incorrect.json"]) {
            const answer = fixture<WhoSaidItAnswerResult>(name);
            expect(answer.roundId).toBe(round.roundId);
            expect(ids).toContain(answer.submittedCharacterId);
            expect(ids).toContain(answer.correctCharacterId);
        }
    });
});
