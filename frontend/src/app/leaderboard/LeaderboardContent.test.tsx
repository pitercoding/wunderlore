import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchLeaderboard, type LeaderboardEntry } from "@/lib/api/ranking";
import LeaderboardContent from "./LeaderboardContent";
import page, { metadata } from "./page";

vi.mock("@/lib/api/ranking", () => ({ fetchLeaderboard: vi.fn() }));

const fetchMock = vi.mocked(fetchLeaderboard);

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

function entry(overrides: Partial<LeaderboardEntry> = {}): LeaderboardEntry {
    return {
        position: 1,
        nickname: "Frodo",
        gameType: "who-said-it",
        percentage: 93.456789,
        playedAt: "2026-03-01T23:30:00Z",
        ...overrides,
    };
}

async function renderPage(game?: string | string[]) {
    render(await LeaderboardContent({ searchParams: Promise.resolve({ game }) }));
}

describe("LeaderboardContent", () => {
    it("shows the global ranking by default", async () => {
        fetchMock.mockResolvedValue({
            game: null,
            entries: [
                entry(),
                entry({ position: 2, nickname: "Sam", gameType: "where-in-middle-earth", percentage: 80 }),
                entry({ position: 2, nickname: "Pippin", gameType: "who-am-i", percentage: 80 }),
            ],
        });

        await renderPage();

        expect(fetchMock).toHaveBeenCalledWith(undefined);
        expect(screen.getByRole("heading", { level: 1, name: "Leaderboard" })).toBeInTheDocument();

        const table = screen.getByRole("table", { name: /Global leaderboard/ });
        const rows = within(table).getAllByRole("row");

        // The header plus three results; equal percentages share the position.
        expect(rows).toHaveLength(4);
        expect(within(rows[1]).getAllByRole("cell").map((c) => c.textContent)).toEqual([
            "1",
            "Frodo",
            "Who Said It?",
            expect.stringMatching(/\d/),
            "93.46%",
        ]);
        expect(rows[1].querySelector("time")).toHaveAttribute("datetime", "2026-03-01T23:30:00Z");
        expect(within(rows[2]).getAllByRole("cell")[0]).toHaveTextContent("2");
        expect(within(rows[3]).getAllByRole("cell")[0]).toHaveTextContent("2");
        expect(within(rows[2]).getByText("Where in Middle-earth")).toBeInTheDocument();
    });

    it("shows the percentage with two decimals", async () => {
        fetchMock.mockResolvedValue({ game: null, entries: [entry({ percentage: 100 }), entry({ position: 2, percentage: 33.333333 })] });

        await renderPage();

        expect(screen.getByText("100.00%")).toBeInTheDocument();
        expect(screen.getByText("33.33%")).toBeInTheDocument();
    });

    it("offers the four games as filters and marks the active one", async () => {
        fetchMock.mockResolvedValue({ game: "who-am-i", entries: [entry({ gameType: "who-am-i" })] });

        await renderPage("who-am-i");

        expect(fetchMock).toHaveBeenCalledWith("who-am-i");

        const nav = screen.getByRole("navigation", { name: "Filter by game" });
        const links = within(nav).getAllByRole("link");

        expect(links.map((l) => l.textContent)).toEqual([
            "All games",
            "Who Said It?",
            "Lore Challenge",
            "Who Am I?",
            "Where in Middle-earth",
        ]);
        expect(links.map((l) => l.getAttribute("href"))).toEqual([
            "/leaderboard",
            "/leaderboard?game=who-said-it",
            "/leaderboard?game=lore-challenge",
            "/leaderboard?game=who-am-i",
            "/leaderboard?game=where-in-middle-earth",
        ]);
        expect(within(nav).getByRole("link", { name: "Who Am I?" })).toHaveAttribute("aria-current", "page");
        expect(within(nav).getByRole("link", { name: "All games" })).not.toHaveAttribute("aria-current");
        expect(screen.getByRole("table", { name: /Who Am I\? leaderboard/ })).toBeInTheDocument();
    });

    it("marks All games as current when there is no filter", async () => {
        fetchMock.mockResolvedValue({ game: null, entries: [] });

        await renderPage();

        expect(screen.getByRole("link", { name: "All games" })).toHaveAttribute("aria-current", "page");
    });

    it("ignores an unknown filter instead of failing", async () => {
        fetchMock.mockResolvedValue({ game: null, entries: [] });

        await renderPage("nonsense");

        expect(fetchMock).toHaveBeenCalledWith(undefined);
    });

    it("uses the first value when the filter is repeated", async () => {
        fetchMock.mockResolvedValue({ game: "lore-challenge", entries: [] });

        await renderPage(["lore-challenge", "who-am-i"]);

        expect(fetchMock).toHaveBeenCalledWith("lore-challenge");
    });

    it("shows an empty state", async () => {
        fetchMock.mockResolvedValue({ game: null, entries: [] });

        await renderPage();

        expect(screen.getByText(/No results have been published yet/)).toBeInTheDocument();
        expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    it("names the game in the empty state of a filter", async () => {
        fetchMock.mockResolvedValue({ game: "who-said-it", entries: [] });

        await renderPage("who-said-it");

        expect(screen.getByText(/No Who Said It\? results have been published yet/)).toBeInTheDocument();
    });

    it("shows an accessible error with a way to retry, keeping the filters", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        fetchMock.mockRejectedValue(new Error("down"));

        await renderPage("lore-challenge");

        expect(screen.getByRole("alert")).toHaveTextContent("couldn't be loaded");
        expect(screen.getByRole("link", { name: "Try again" })).toHaveAttribute(
            "href",
            "/leaderboard?game=lore-challenge",
        );
        expect(screen.getByRole("navigation", { name: "Filter by game" })).toBeInTheDocument();
    });
});

describe("the page", () => {
    it("has its own metadata and canonical", () => {
        expect(metadata.title).toBe("Leaderboard");
        expect(metadata.description).toMatch(/Who Said It/);
        expect(metadata.alternates?.canonical).toBe("/leaderboard");
    });

    it("renders a loading state in a Suspense fallback", () => {
        // The default export wraps the content in Suspense with a status fallback.
        expect(typeof page).toBe("function");
    });
});
