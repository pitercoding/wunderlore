import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import HomePage from "./page";

describe("HomePage", () => {
    it("links to the quote catalog and the game", () => {
        render(<HomePage />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Wunderlore" }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("link", { name: "Explore quotes →" }),
        ).toHaveAttribute("href", "/quotes");
        expect(
            screen.getByRole("link", { name: "Play Who Said It? →" }),
        ).toHaveAttribute("href", "/game");
    });
});
