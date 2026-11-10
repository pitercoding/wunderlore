import { describe, expect, it } from "vitest";
import {
    defaultDivisionOf,
    divisionFromDifficulty,
    divisionLabel,
    divisionsOf,
    gameLabel,
    isDivisionOf,
    isRankingDivision,
    isRankingGame,
    RANKING_DIVISION_COUNT,
    RANKING_GAMES,
    rankingHref,
} from "./games";

describe("ranking games and divisions", () => {
    it("lists the four ranked games, never Which Character", () => {
        expect(RANKING_GAMES.map((game) => game.slug)).toEqual([
            "lore-challenge",
            "who-am-i",
            "where-in-middle-earth",
            "who-said-it",
        ]);
        expect(isRankingGame("which-character")).toBe(false);
    });

    it.each(["lore-challenge", "who-am-i", "where-in-middle-earth"] as const)(
        "gives %s easy, medium and hard",
        (game) => {
            expect(divisionsOf(game)).toEqual(["easy", "medium", "hard"]);
            expect(defaultDivisionOf(game)).toBe("easy");
            expect(isDivisionOf(game, "standard")).toBe(false);
        },
    );

    it("gives Who Said It? the standard division only", () => {
        expect(divisionsOf("who-said-it")).toEqual(["standard"]);
        expect(defaultDivisionOf("who-said-it")).toBe("standard");
        expect(isDivisionOf("who-said-it", "easy")).toBe(false);
        expect(isDivisionOf("who-said-it", "standard")).toBe(true);
    });

    it("counts ten divisions in all", () => {
        expect(RANKING_DIVISION_COUNT).toBe(10);
    });

    it("recognizes only the four division slugs", () => {
        expect(["easy", "medium", "hard", "standard"].every(isRankingDivision)).toBe(true);
        expect(isRankingDivision("EASY")).toBe(false);
        expect(isRankingDivision("expert")).toBe(false);
        expect(isRankingDivision(undefined)).toBe(false);
    });

    it("labels games and divisions", () => {
        expect(gameLabel("who-said-it")).toBe("Who Said It?");
        expect(divisionLabel("standard")).toBe("Standard");
        expect(divisionLabel("hard")).toBe("Hard");
    });

    it("maps each difficulty of a game to its division", () => {
        expect(divisionFromDifficulty("EASY")).toBe("easy");
        expect(divisionFromDifficulty("MEDIUM")).toBe("medium");
        expect(divisionFromDifficulty("HARD")).toBe("hard");
    });

    it("builds the shareable link of a division", () => {
        expect(rankingHref("lore-challenge", "hard")).toBe("/leaderboard?game=lore-challenge&division=hard");
        expect(rankingHref("who-said-it", "standard")).toBe("/leaderboard?game=who-said-it&division=standard");
    });
});
