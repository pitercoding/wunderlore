import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import HomePage from "./page";

describe("HomePage", () => {
    it("embeds WebSite structured data with only verifiable facts", () => {
        const { container } = render(<HomePage />);
        const script = container.querySelector('script[type="application/ld+json"]');

        expect(JSON.parse(script?.textContent ?? "")).toEqual({
            "@context": "https://schema.org",
            "@type": "WebSite",
            name: "Wunderlore",
            url: "https://wunderlore.vercel.app",
            description: expect.stringContaining("Wunderlore"),
            inLanguage: "en",
        });
    });

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
        ).toHaveAttribute("href", "/games/who-said-it");
    });

    it("links to the Which Character Are You? game", () => {
        render(<HomePage />);

        expect(
            screen.getByRole("link", { name: "Which Character Are You? →" }),
        ).toHaveAttribute("href", "/games/which-character");
        expect(
            screen.getByText(/personality questions.*Tolkien character/),
        ).toBeInTheDocument();
    });

    it("links to the Lore Challenge game", () => {
        render(<HomePage />);

        expect(
            screen.getByRole("link", { name: "Take the Lore Challenge →" }),
        ).toHaveAttribute("href", "/games/lore-challenge");
        expect(
            screen.getAllByRole("link", { name: "Lore Challenge" })[0],
        ).toHaveAttribute("href", "/games/lore-challenge");
    });

    it("links to the Who Am I? game as the fourth game", () => {
        render(<HomePage />);

        expect(screen.getByRole("link", { name: "Play Who Am I? →" })).toHaveAttribute(
            "href",
            "/games/who-am-i",
        );
        expect(screen.getAllByRole("link", { name: "Who Am I?" })[0]).toHaveAttribute(
            "href",
            "/games/who-am-i",
        );
    });
});
