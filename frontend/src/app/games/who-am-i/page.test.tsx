import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { startWhoAmIGame } from "./actions";
import WhoAmIPage, { metadata } from "./page";

vi.mock("./actions", () => ({
    startWhoAmIGame: vi.fn(),
    fetchWhoAmIRound: vi.fn(),
    submitWhoAmIAnswer: vi.fn(),
    fetchWhoAmIResult: vi.fn(),
}));

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhoAmIPage", () => {
    it("renders the menu without creating a game", () => {
        render(<WhoAmIPage />);

        expect(screen.getByRole("heading", { level: 1, name: "Who Am I?" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Start Game" })).toBeEnabled();
        expect(startWhoAmIGame).not.toHaveBeenCalled();
    });

    it("has a main landmark and links to the Home and the other games", () => {
        render(<WhoAmIPage />);

        expect(screen.getByRole("main")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute("href", "/");

        for (const [name, href] of [
            ["Who Said It?", "/games/who-said-it"],
            ["Which Character?", "/games/which-character"],
            ["Lore Challenge", "/games/lore-challenge"],
            ["Who Am I?", "/games/who-am-i"],
        ]) {
            expect(screen.getAllByRole("link", { name })[0]).toHaveAttribute("href", href);
        }
    });

    it("keeps one h1 and a sensible heading order on the menu", () => {
        render(<WhoAmIPage />);

        expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
        expect(screen.getByRole("heading", { level: 2, name: "How it works" })).toBeInTheDocument();
    });

    it("publishes its own metadata", () => {
        expect(metadata.title).toBe("Who Am I?");
        expect(metadata.alternates?.canonical).toBe("/games/who-am-i");
        expect(metadata.description).toMatch(/five clues/i);
    });
});
