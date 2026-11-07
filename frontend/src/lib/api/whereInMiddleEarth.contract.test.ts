import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type {
    WhereInMiddleEarthAnswer,
    WhereInMiddleEarthResult,
    WhereInMiddleEarthRound,
} from "./whereInMiddleEarth";

// Same files the backend contract test reads; see contracts/where-in-middle-earth/README.md.
// Vitest runs from the frontend directory.
const CONTRACTS_DIR = resolve(process.cwd(), "..", "contracts", "where-in-middle-earth");

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
    return import("./whereInMiddleEarth");
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

describe("Where in Middle-earth contract fixtures", () => {
    it("round.json is accepted by createGame and getCurrentRound unchanged", async () => {
        const { createGame, getCurrentRound } = await loadModules();
        const expected = fixture<WhereInMiddleEarthRound>("round.json");

        serve("round.json");
        await expect(createGame("EASY")).resolves.toEqual(expected);

        serve("round.json");
        await expect(getCurrentRound(expected.gameId)).resolves.toEqual(expected);
    });

    it.each(["answer.json", "answer-final.json"])(
        "%s is accepted by submitAnswer unchanged",
        async (name) => {
            const { submitAnswer } = await loadModules();
            const expected = fixture<WhereInMiddleEarthAnswer>(name);

            serve(name);

            await expect(
                submitAnswer(expected.gameId, expected.roundNumber, expected.submitted),
            ).resolves.toEqual(expected);
        },
    );

    it("result.json is accepted by getResult unchanged", async () => {
        const { getResult } = await loadModules();
        const expected = fixture<WhereInMiddleEarthResult>("result.json");

        serve("result.json");

        await expect(getResult(expected.gameId)).resolves.toEqual(expected);
    });

    it("keeps the fields of each fixture the validators and types rely on", () => {
        // Explicit, so a failure names the property that changed.
        expect(Object.keys(fixture("round.json")).sort()).toEqual([
            "description", "difficulty", "gameId", "locationName", "maxScore", "roundNumber",
            "score", "totalRounds",
        ]);

        for (const name of ["answer.json", "answer-final.json"]) {
            expect(Object.keys(fixture(name)).sort()).toEqual([
                "accuracy", "distance", "gameFinished", "gameId", "pointsAwarded", "result",
                "reveal", "roundNumber", "score", "submitted",
            ]);
            expect(Object.keys(fixture<WhereInMiddleEarthAnswer>(name).reveal).sort()).toEqual([
                "explanation", "locationName", "period", "regionName", "x", "y",
            ]);
        }

        expect(Object.keys(fixture("result.json")).sort()).toEqual([
            "averageDistance", "closeHits", "difficulty", "gameId", "maxScore", "percentage",
            "rounds", "score", "totalRounds",
        ]);
    });

    it("never shows where the place is in a round that waits for an answer", () => {
        const round = fixture<Record<string, unknown>>("round.json");

        expect(round).not.toHaveProperty("x");
        expect(round).not.toHaveProperty("y");
        expect(round).not.toHaveProperty("reveal");
        expect(round).not.toHaveProperty("explanation");
        expect(round).not.toHaveProperty("regionName");
    });

    it("carries the result only with the answer that ended the game", () => {
        expect(fixture<WhereInMiddleEarthAnswer>("answer.json").result).toBeNull();
        expect(fixture<WhereInMiddleEarthAnswer>("answer.json").gameFinished).toBe(false);
        expect(fixture<WhereInMiddleEarthAnswer>("answer-final.json").result).not.toBeNull();
        expect(fixture<WhereInMiddleEarthAnswer>("answer-final.json").gameFinished).toBe(true);
    });
});
