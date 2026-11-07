import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import SceneNav from "./SceneNav";

function menu() {
    return screen.getByText("Menu").closest("details") as HTMLDetailsElement;
}

describe("SceneNav compact menu", () => {
    it("starts closed and opens and closes with the menu button", async () => {
        const user = userEvent.setup();
        render(<SceneNav />);

        expect(menu().open).toBe(false);

        await user.click(screen.getByText("Menu"));
        expect(menu().open).toBe(true);

        await user.click(screen.getByText("Menu"));
        expect(menu().open).toBe(false);
    });

    it("closes with Escape and returns focus to the menu button", async () => {
        const user = userEvent.setup();
        render(<SceneNav />);

        await user.click(screen.getByText("Menu"));
        const links = screen
            .getAllByRole("navigation", { name: "Menu" })[0]
            .querySelectorAll("a");
        links[1].focus();
        expect(menu().open).toBe(true);

        await user.keyboard("{Escape}");

        expect(menu().open).toBe(false);
        expect(screen.getByText("Menu").closest("summary")).toHaveFocus();
    });

    it("ignores Escape while the menu is closed", async () => {
        const user = userEvent.setup();
        render(<SceneNav />);
        screen.getByText("Menu").focus();

        await user.keyboard("{Escape}");

        expect(menu().open).toBe(false);
    });

    it("keeps the menu links reachable in order with Tab", async () => {
        const user = userEvent.setup();
        render(<SceneNav />);

        await user.click(screen.getByText("Menu"));
        screen.getByText("Menu").focus();

        await user.tab();
        expect(screen.getAllByRole("link", { name: "Who Said It?" })[1]).toHaveFocus();
        await user.tab();
        expect(screen.getAllByRole("link", { name: "Which Character?" })[1]).toHaveFocus();
        await user.tab();
        expect(screen.getAllByRole("link", { name: "Lore Challenge" })[1]).toHaveFocus();
        await user.tab();
        expect(screen.getAllByRole("link", { name: "Who Am I?" })[1]).toHaveFocus();
        await user.tab();
        expect(screen.getAllByRole("link", { name: "Where in Middle-earth" })[1]).toHaveFocus();
        await user.tab();
        expect(screen.getAllByRole("link", { name: "Quotes" })[1]).toHaveFocus();
    });

    it("links every destination", () => {
        render(<SceneNav />);

        const hrefs = screen
            .getAllByRole("link")
            .map((link) => link.getAttribute("href"));

        expect(hrefs).toEqual([
            "/",
            "/games/who-said-it",
            "/games/which-character",
            "/games/lore-challenge",
            "/games/who-am-i",
            "/games/where-in-middle-earth",
            "/quotes",
            "/games/who-said-it",
            "/games/which-character",
            "/games/lore-challenge",
            "/games/who-am-i",
            "/games/where-in-middle-earth",
            "/quotes",
        ]);
    });

    it("shows only the brand in compact mode", () => {
        render(<SceneNav compact />);

        expect(screen.getAllByRole("link")).toHaveLength(1);
        expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
    });
});
