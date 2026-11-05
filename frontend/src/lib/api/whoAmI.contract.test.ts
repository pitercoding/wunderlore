import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { WhoAmIAnswer, WhoAmIResult, WhoAmIRound } from "./whoAmI";

// Same files the backend contract test reads; see contracts/who-am-i/README.md.
// Vitest runs from the frontend directory.
const CONTRACTS_DIR = resolve(process.cwd(), "..", "contracts", "who-am-i");

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
    return import("./whoAmI");
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

describe("Who Am I? contract fixtures", () => {
    it("round.json is accepted by createGame and getCurrentRound unchanged", async () => {
        const { createGame, getCurrentRound } = await loadModules();
        const expected = fixture<WhoAmIRound>("round.json");

        serve("round.json");
        await expect(createGame("EASY")).resolves.toEqual(expected);

        serve("round.json");
        await expect(getCurrentRound(expected.gameId)).resolves.toEqual(expected);
    });

    it.each(["answer-incorrect.json", "answer-correct.json", "answer-final.json"])(
        "%s is accepted by submitAnswer unchanged",
        async (name) => {
            const { submitAnswer } = await loadModules();
            const expected = fixture<WhoAmIAnswer>(name);

            serve(name);

            await expect(
                submitAnswer(
                    expected.gameId,
                    expected.roundNumber,
                    expected.clueNumber,
                    expected.submittedOptionId,
                ),
            ).resolves.toEqual(expected);
        },
    );

    it("result.json is accepted by getResult unchanged", async () => {
        const { getResult } = await loadModules();
        const expected = fixture<WhoAmIResult>("result.json");

        serve("result.json");

        await expect(getResult(expected.gameId)).resolves.toEqual(expected);
    });

    it("keeps the fields of each fixture the validators and types rely on", () => {
        // Explicit, so a failure names the property that changed.
        expect(Object.keys(fixture("round.json")).sort()).toEqual([
            "clueNumber", "clues", "difficulty", "gameId", "maxScore", "options", "roundNumber",
            "score", "totalClues", "totalRounds",
        ]);

        const answerFields = [
            "clueNumber", "correct", "gameFinished", "gameId", "next", "pointsAwarded", "result",
            "reveal", "roundFinished", "roundNumber", "score", "submittedOptionId",
        ];

        for (const name of ["answer-incorrect.json", "answer-correct.json", "answer-final.json"]) {
            expect(Object.keys(fixture(name)).sort()).toEqual(answerFields);
        }

        expect(Object.keys(fixture("result.json")).sort()).toEqual([
            "difficulty", "gameId", "identified", "incorrectAnswers", "maxScore", "percentage",
            "rounds", "score", "totalRounds", "unidentified",
        ]);
    });

    it("never shows the character or a later clue in an incorrect answer or a round", () => {
        const round = fixture<WhoAmIRound>("round.json");
        const incorrect = fixture<WhoAmIAnswer>("answer-incorrect.json");

        expect(round.clues).toHaveLength(round.clueNumber);
        expect(incorrect.next?.clues).toHaveLength(incorrect.next?.clueNumber ?? 0);
        expect(incorrect.reveal).toBeNull();
        expect(incorrect.result).toBeNull();
    });
});
