import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import SceneNav from "./SceneNav";

const pathnameMock = vi.hoisted(() => vi.fn<() => string | null>());

vi.mock("next/navigation", () => ({
    usePathname: pathnameMock,
}));

function current() {
    return screen
        .getAllByRole("link", { current: "page" })
        .map((link) => link.textContent);
}

describe("SceneNav current page", () => {
    beforeEach(() => {
        pathnameMock.mockReset();
    });

    it("marks the current page in both the wide navigation and the menu", () => {
        pathnameMock.mockReturnValue("/leaderboard");
        render(<SceneNav />);

        expect(current()).toEqual(["Leaderboard", "Leaderboard"]);
    });

    it("keeps the link of a game marked on the pages beneath it, and only that one", () => {
        pathnameMock.mockReturnValue("/games/who-said-it/anything");
        render(<SceneNav />);

        expect(current()).toEqual(["Who Said It?", "Who Said It?"]);
    });

    it("marks nothing on the home page", () => {
        pathnameMock.mockReturnValue("/");
        render(<SceneNav />);

        expect(screen.queryByRole("link", { current: "page" })).not.toBeInTheDocument();
    });
});
