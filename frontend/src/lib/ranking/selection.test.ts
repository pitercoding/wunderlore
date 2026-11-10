import { describe, expect, it } from "vitest";
import { resolveSelection } from "./selection";

describe("resolveSelection", () => {
    it("is the overview without parameters", () => {
        expect(resolveSelection({})).toEqual({ kind: "overview" });
    });

    it("is the overview for an unknown game, or a division without a game", () => {
        expect(resolveSelection({ game: "nonsense", division: "easy" })).toEqual({ kind: "overview" });
        expect(resolveSelection({ game: "which-character" })).toEqual({ kind: "overview" });
        expect(resolveSelection({ division: "hard" })).toEqual({ kind: "overview" });
        expect(resolveSelection({ game: "" })).toEqual({ kind: "overview" });
    });

    it.each([
        ["lore-challenge", "easy"],
        ["lore-challenge", "medium"],
        ["lore-challenge", "hard"],
        ["who-am-i", "easy"],
        ["where-in-middle-earth", "hard"],
        ["who-said-it", "standard"],
    ] as const)("keeps the valid pair %s / %s", (game, division) => {
        expect(resolveSelection({ game, division })).toEqual({ kind: "division", game, division });
    });

    it("chooses the first division of a game chosen without one", () => {
        expect(resolveSelection({ game: "who-am-i" })).toEqual({
            kind: "division",
            game: "who-am-i",
            division: "easy",
        });
        expect(resolveSelection({ game: "who-said-it" })).toEqual({
            kind: "division",
            game: "who-said-it",
            division: "standard",
        });
    });

    it.each([
        ["who-said-it", "easy", "standard"],
        ["who-said-it", "hard", "standard"],
        ["lore-challenge", "standard", "easy"],
        ["where-in-middle-earth", "expert", "easy"],
        ["who-am-i", "HARD", "easy"],
    ] as const)("replaces the invalid pair %s / %s by %s", (game, division, expected) => {
        expect(resolveSelection({ game, division })).toEqual({ kind: "division", game, division: expected });
    });

    it("uses the first value of a repeated parameter", () => {
        expect(resolveSelection({ game: ["lore-challenge", "who-am-i"], division: ["hard", "easy"] })).toEqual({
            kind: "division",
            game: "lore-challenge",
            division: "hard",
        });
    });
});
