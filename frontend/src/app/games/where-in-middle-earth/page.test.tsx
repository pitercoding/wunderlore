import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { startWhereInMiddleEarthGame } from "./actions";
import WhereInMiddleEarthPage, { metadata } from "./page";

vi.mock("./actions", () => ({
    startWhereInMiddleEarthGame: vi.fn(),
    fetchWhereInMiddleEarthRound: vi.fn(),
    submitWhereInMiddleEarthAnswer: vi.fn(),
    fetchWhereInMiddleEarthResult: vi.fn(),
}));

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhereInMiddleEarthPage", () => {
    it("renders the menu without creating a game", () => {
        render(<WhereInMiddleEarthPage />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Where in Middle-earth" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Start Game" })).toBeEnabled();
        expect(startWhereInMiddleEarthGame).not.toHaveBeenCalled();
    });

    it("has a main landmark and links to the Home and the other games", () => {
        render(<WhereInMiddleEarthPage />);

        expect(screen.getByRole("main")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute("href", "/");
        expect(screen.getAllByRole("link", { name: "Where in Middle-earth" })[0]).toHaveAttribute(
            "href",
            "/games/where-in-middle-earth",
        );
        expect(screen.getAllByRole("link", { name: "Who Am I?" })[0]).toHaveAttribute(
            "href",
            "/games/who-am-i",
        );
    });

    it("declares its title, description and canonical path", () => {
        expect(metadata.title).toBe("Where in Middle-earth");
        expect(metadata.description).toMatch(/original map/);
        expect(metadata.alternates?.canonical).toBe("/games/where-in-middle-earth");
    });
});
