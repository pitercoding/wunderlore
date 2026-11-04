import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { startLoreChallengeMatch } from "./actions";
import LoreChallengePage from "./page";

vi.mock("./actions", () => ({
    startLoreChallengeMatch: vi.fn(),
    fetchLoreChallengeQuestion: vi.fn(),
    submitLoreChallengeAnswer: vi.fn(),
    fetchLoreChallengeResult: vi.fn(),
}));

afterEach(() => {
    vi.resetAllMocks();
});

describe("LoreChallengePage", () => {
    it("renders the menu without creating a match", () => {
        render(<LoreChallengePage />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Lore Challenge" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Begin the challenge" })).toBeEnabled();
        expect(startLoreChallengeMatch).not.toHaveBeenCalled();
    });

    it("has a main landmark and links to the Home and the other games", () => {
        render(<LoreChallengePage />);

        expect(screen.getByRole("main")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute("href", "/");
        expect(screen.getAllByRole("link", { name: "Lore Challenge" })[0]).toHaveAttribute(
            "href",
            "/games/lore-challenge",
        );
    });
});
