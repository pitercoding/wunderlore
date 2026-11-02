import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { WhichCharacterQuestions, WhichCharacterResult } from "./whichCharacter";

// Same files the backend contract test reads; see contracts/which-character/README.md.
// Vitest runs from the frontend directory.
const CONTRACTS_DIR = resolve(process.cwd(), "..", "contracts", "which-character");

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
    return import("./whichCharacter");
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

describe("Which Character Are You contract fixtures", () => {
    it("questions.json is accepted by getQuestions unchanged", async () => {
        const { getQuestions } = await loadModules();
        serve("questions.json");

        const result = await getQuestions();

        expect(result).toEqual(fixture("questions.json"));
        // The declared type must cover every property of the fixture.
        const expected: WhichCharacterQuestions = result;
        expect(Object.keys(expected)).toEqual(["questions"]);
        expect(expected.questions).toHaveLength(10);
        for (const question of expected.questions) {
            expect(Object.keys(question).sort()).toEqual(["id", "options", "text"]);
            expect(question.options).toHaveLength(4);
            for (const option of question.options) {
                expect(Object.keys(option).sort()).toEqual(["id", "text"]);
            }
        }
    });

    it("result.json is accepted by submitAnswers unchanged", async () => {
        const { submitAnswers } = await loadModules();
        serve("result.json");
        // One answer per question of the questions fixture.
        const { questions } = fixture<WhichCharacterQuestions>("questions.json");
        const answers = questions.map((question) => ({
            questionId: question.id,
            answerId: question.options[0].id,
        }));

        const result = await submitAnswers(answers);

        expect(result).toEqual(fixture("result.json"));
        const expected: WhichCharacterResult = result;
        expect(Object.keys(expected)).toEqual(["character"]);
        expect(Object.keys(expected.character).sort()).toEqual(["description", "id", "name"]);
    });
});
