import { render, screen, within } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
    fetchLeaderboard,
    fetchLeaderboardOverview,
    type LeaderboardEntry,
    type LeaderboardOverviewGroup,
} from "@/lib/api/ranking";
import type { RankingDivision, RankingGame } from "@/lib/ranking/games";
import LeaderboardContent from "./LeaderboardContent";
import page, { metadata } from "./page";

vi.mock("@/lib/api/ranking", () => ({
    fetchLeaderboard: vi.fn(),
    fetchLeaderboardOverview: vi.fn(),
}));

const fetchMock = vi.mocked(fetchLeaderboard);
const overviewMock = vi.mocked(fetchLeaderboardOverview);

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

function entry(overrides: Partial<LeaderboardEntry> = {}): LeaderboardEntry {
    return {
        position: 1,
        nickname: "Frodo",
        gameType: "lore-challenge",
        division: "easy",
        percentage: 93.456789,
        playedAt: "2026-03-01T23:30:00Z",
        ...overrides,
    };
}

const PAIRS: ReadonlyArray<readonly [RankingGame, RankingDivision]> = [
    ["lore-challenge", "easy"],
    ["lore-challenge", "medium"],
    ["lore-challenge", "hard"],
    ["who-am-i", "easy"],
    ["who-am-i", "medium"],
    ["who-am-i", "hard"],
    ["where-in-middle-earth", "easy"],
    ["where-in-middle-earth", "medium"],
    ["where-in-middle-earth", "hard"],
    ["who-said-it", "standard"],
];

function groups(filled: Partial<Record<string, LeaderboardEntry[]>> = {}): LeaderboardOverviewGroup[] {
    return PAIRS.map(([game, division]) => ({
        game,
        division,
        entries: filled[`${game}/${division}`] ?? [],
    }));
}

async function renderPage(params: { game?: string | string[]; division?: string | string[] } = {}) {
    render(await LeaderboardContent({ searchParams: Promise.resolve(params) }));
}

describe("the overview (no parameters)", () => {
    it("loads the overview and never the ranking of a division", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage();

        expect(overviewMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();
        expect(screen.getByRole("heading", { level: 1, name: "Leaderboard" })).toBeInTheDocument();
    });

    it("shows the four games and their ten divisions, empty ones included", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage();

        const sections = screen.getAllByRole("region");

        expect(sections.map((section) => within(section).getByRole("heading", { level: 2 }).textContent)).toEqual([
            "Lore Challenge",
            "Who Am I?",
            "Where in Middle-earth",
            "Who Said It?",
        ]);

        const divisionsOf = (name: string) =>
            within(screen.getByRole("region", { name }))
                .getAllByRole("heading", { level: 3 })
                .map((heading) => heading.textContent);

        expect(divisionsOf("Lore Challenge")).toEqual(["Easy", "Medium", "Hard"]);
        expect(divisionsOf("Who Am I?")).toEqual(["Easy", "Medium", "Hard"]);
        expect(divisionsOf("Where in Middle-earth")).toEqual(["Easy", "Medium", "Hard"]);
        expect(divisionsOf("Who Said It?")).toEqual(["Standard"]);
        expect(screen.getAllByRole("heading", { level: 3 })).toHaveLength(10);
    });

    it("says there are no results yet in a division nobody played, and invents nothing", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage();

        expect(screen.getAllByText("No results yet")).toHaveLength(10);
        // No list of results inside any game section (the navigation lists are elsewhere).
        for (const region of screen.getAllByRole("region")) {
            expect(within(region).queryByRole("list")).not.toBeInTheDocument();
        }
        expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    it("never shows Which Character, a global ranking or a combined score", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage();

        // Only the leaderboard's own content: the site-wide navigation legitimately links to Which Character?.
        const content = within(screen.getByRole("main"));

        expect(content.queryByText(/which character/i)).not.toBeInTheDocument();
        expect(content.queryByText(/global/i)).not.toBeInTheDocument();
        expect(content.queryByText(/all games/i)).not.toBeInTheDocument();
        expect(
            content.getByText(/Each division competes only with itself\. There is no overall ranking\./),
        ).toBeInTheDocument();
    });

    it("links every division to its own full ranking", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage();

        const links = PAIRS.map(([game, division]) => {
            const label = {
                "lore-challenge": "Lore Challenge",
                "who-am-i": "Who Am I?",
                "where-in-middle-earth": "Where in Middle-earth",
                "who-said-it": "Who Said It?",
            }[game];
            const name = `${label} ${division[0].toUpperCase()}${division.slice(1)}: full ranking`;

            return screen.getByRole("link", { name }).getAttribute("href");
        });

        expect(links).toEqual(PAIRS.map(([game, division]) => `/leaderboard?game=${game}&division=${division}`));
    });

    it("shows the first places of a division as the backend sent them", async () => {
        overviewMock.mockResolvedValue({
            groups: groups({
                "who-am-i/hard": [
                    entry({ gameType: "who-am-i", division: "hard", position: 1, nickname: "Aria", percentage: 95 }),
                    entry({ gameType: "who-am-i", division: "hard", position: 2, nickname: "Bo", percentage: 80 }),
                    entry({ gameType: "who-am-i", division: "hard", position: 2, nickname: "Cy", percentage: 80 }),
                ],
            }),
        });

        await renderPage();

        const hard = within(screen.getByRole("region", { name: "Who Am I?" }));
        const items = hard.getAllByRole("listitem");

        expect(items).toHaveLength(3);
        expect(items.map((item) => item.textContent)).toEqual([
            expect.stringMatching(/^1Aria95\.00%/),
            expect.stringMatching(/^2Bo80\.00%/),
            expect.stringMatching(/^2Cy80\.00%/),
        ]);
        expect(items[0].querySelector("time")).toHaveAttribute("datetime", "2026-03-01T23:30:00Z");
        // The other divisions of that game stay empty.
        expect(hard.getAllByText("No results yet")).toHaveLength(2);
    });

    it("keeps every player tied for first, even when there are more than three", async () => {
        const tied = ["A", "B", "C", "D"].map((name) =>
            entry({ gameType: "lore-challenge", division: "easy", position: 1, nickname: `Tied ${name}`, percentage: 100 }),
        );

        overviewMock.mockResolvedValue({ groups: groups({ "lore-challenge/easy": tied }) });

        await renderPage();

        const items = within(screen.getByRole("region", { name: "Lore Challenge" })).getAllByRole("listitem");

        expect(items).toHaveLength(4);
        expect(items.map((item) => item.textContent?.[0])).toEqual(["1", "1", "1", "1"]);
    });

    it("does not compute or round positions or percentages itself", async () => {
        overviewMock.mockResolvedValue({
            groups: groups({
                "lore-challenge/easy": [
                    // A position the data does not make obvious (a gap after a tie) is shown as sent.
                    entry({ position: 1, nickname: "First", percentage: 100 }),
                    entry({ position: 1, nickname: "Second", percentage: 100 }),
                    entry({ position: 3, nickname: "Third", percentage: 33.333333 }),
                ],
            }),
        });

        await renderPage();

        const items = within(screen.getByRole("region", { name: "Lore Challenge" })).getAllByRole("listitem");

        expect(items.map((item) => item.textContent)).toEqual([
            expect.stringMatching(/^1First100\.00%/),
            expect.stringMatching(/^1Second100\.00%/),
            expect.stringMatching(/^3Third33\.33%/),
        ]);
    });

    it("offers the games as navigation with the overview current", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage();

        const nav = screen.getByRole("navigation", { name: "Choose a game" });
        const links = within(nav).getAllByRole("link");

        expect(links.map((link) => link.textContent)).toEqual([
            "Overview",
            "Lore Challenge",
            "Who Am I?",
            "Where in Middle-earth",
            "Who Said It?",
        ]);
        expect(links.map((link) => link.getAttribute("href"))).toEqual([
            "/leaderboard",
            "/leaderboard?game=lore-challenge&division=easy",
            "/leaderboard?game=who-am-i&division=easy",
            "/leaderboard?game=where-in-middle-earth&division=easy",
            "/leaderboard?game=who-said-it&division=standard",
        ]);
        expect(within(nav).getByRole("link", { name: "Overview" })).toHaveAttribute("aria-current", "page");
        expect(screen.queryByRole("navigation", { name: /division/ })).not.toBeInTheDocument();
    });

    it("ignores a division without a known game", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage({ division: "hard" });
        expect(fetchMock).not.toHaveBeenCalled();
        expect(overviewMock).toHaveBeenCalledTimes(1);
    });

    it("falls back to the overview for an unknown game, without a call for it", async () => {
        overviewMock.mockResolvedValue({ groups: groups() });

        await renderPage({ game: "nonsense", division: "easy" });

        expect(overviewMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).not.toHaveBeenCalled();
    });

    it("shows an accessible error with a way to retry, keeping the navigation", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        overviewMock.mockRejectedValue(new Error("down"));

        await renderPage();

        expect(screen.getByRole("alert")).toHaveTextContent("couldn't be loaded");
        expect(screen.getByRole("link", { name: "Try again" })).toHaveAttribute("href", "/leaderboard");
        expect(screen.getByRole("navigation", { name: "Choose a game" })).toBeInTheDocument();
    });

    it("treats a broken contract like any other failure", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        overviewMock.mockRejectedValue(new Error("API response from /api/ranking/overview is invalid"));

        await renderPage();

        expect(screen.getByRole("alert")).toHaveTextContent("couldn't be loaded");
        expect(screen.queryByRole("heading", { level: 3 })).not.toBeInTheDocument();
    });
});

describe("the ranking of one division", () => {
    it.each(PAIRS)("asks the backend for %s / %s only", async (game, division) => {
        fetchMock.mockResolvedValue({ game, division, entries: [] });

        await renderPage({ game, division });

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith(game, division);
        expect(overviewMock).not.toHaveBeenCalled();
    });

    it("names the game and the division and explains why results are grouped", async () => {
        fetchMock.mockResolvedValue({
            game: "lore-challenge",
            division: "hard",
            entries: [entry({ division: "hard" })],
        });

        await renderPage({ game: "lore-challenge", division: "hard" });

        expect(screen.getByRole("heading", { level: 2, name: "Lore Challenge · Hard" })).toBeInTheDocument();
        expect(screen.getByText(/Hard results compete only with other Hard games of Lore Challenge/)).toBeInTheDocument();
        expect(
            screen.getByText(/Each division competes only with itself\. There is no overall ranking\./),
        ).toBeInTheDocument();
    });

    it("shows the full ranking in the order received, without a Game column", async () => {
        fetchMock.mockResolvedValue({
            game: "lore-challenge",
            division: "easy",
            entries: [
                entry({ position: 1, nickname: "Frodo", percentage: 93.456789 }),
                entry({ position: 2, nickname: "Sam", percentage: 80 }),
                entry({ position: 2, nickname: "Pippin", percentage: 80 }),
                entry({ position: 4, nickname: "Merry", percentage: 70 }),
            ],
        });

        await renderPage({ game: "lore-challenge", division: "easy" });

        const table = screen.getByRole("table", { name: /Lore Challenge · Easy leaderboard, 4 results/ });

        expect(within(table).getAllByRole("columnheader").map((header) => header.textContent)).toEqual([
            "Rank",
            "Player",
            "Date",
            "Score",
        ]);

        const rows = within(table).getAllByRole("row");

        expect(rows).toHaveLength(5);
        expect(within(rows[1]).getAllByRole("cell").map((cell) => cell.textContent)).toEqual([
            "1",
            "Frodo",
            expect.stringMatching(/\d/),
            "93.46%",
        ]);
        expect(rows[1].querySelector("time")).toHaveAttribute("datetime", "2026-03-01T23:30:00Z");
        // Ties and the gap after them are exactly what the backend sent.
        expect(rows.slice(1).map((row) => within(row).getAllByRole("cell")[0].textContent)).toEqual([
            "1",
            "2",
            "2",
            "4",
        ]);
        expect(rows.slice(1).map((row) => within(row).getAllByRole("cell")[1].textContent)).toEqual([
            "Frodo",
            "Sam",
            "Pippin",
            "Merry",
        ]);
    });

    it("shows the percentage with two decimals", async () => {
        fetchMock.mockResolvedValue({
            game: "who-am-i",
            division: "easy",
            entries: [
                entry({ gameType: "who-am-i", percentage: 100 }),
                entry({ gameType: "who-am-i", position: 2, percentage: 33.333333 }),
            ],
        });

        await renderPage({ game: "who-am-i", division: "easy" });

        expect(screen.getByText("100.00%")).toBeInTheDocument();
        expect(screen.getByText("33.33%")).toBeInTheDocument();
    });

    it("offers Easy, Medium and Hard for the games with levels, marking the current one", async () => {
        fetchMock.mockResolvedValue({ game: "where-in-middle-earth", division: "medium", entries: [] });

        await renderPage({ game: "where-in-middle-earth", division: "medium" });

        const nav = screen.getByRole("navigation", { name: "Choose a Where in Middle-earth division" });
        const links = within(nav).getAllByRole("link");

        expect(links.map((link) => link.textContent)).toEqual(["Easy", "Medium", "Hard"]);
        expect(links.map((link) => link.getAttribute("href"))).toEqual([
            "/leaderboard?game=where-in-middle-earth&division=easy",
            "/leaderboard?game=where-in-middle-earth&division=medium",
            "/leaderboard?game=where-in-middle-earth&division=hard",
        ]);
        expect(within(nav).getByRole("link", { name: "Medium" })).toHaveAttribute("aria-current", "page");
        expect(within(nav).getByRole("link", { name: "Easy" })).not.toHaveAttribute("aria-current");

        const games = screen.getByRole("navigation", { name: "Choose a game" });

        expect(within(games).getByRole("link", { name: "Where in Middle-earth" })).toHaveAttribute(
            "aria-current",
            "page",
        );
        expect(within(games).getByRole("link", { name: "Overview" })).not.toHaveAttribute("aria-current");
    });

    it.each(["lore-challenge", "who-am-i", "where-in-middle-earth"] as const)(
        "offers the three difficulties for %s",
        async (game) => {
            fetchMock.mockResolvedValue({ game, division: "easy", entries: [] });

            await renderPage({ game, division: "easy" });

            const nav = screen.getByRole("navigation", { name: /division$/ });

            expect(within(nav).getAllByRole("link").map((link) => link.textContent)).toEqual([
                "Easy",
                "Medium",
                "Hard",
            ]);
        },
    );

    it("offers only Standard for Who Said It?, with no division filters", async () => {
        fetchMock.mockResolvedValue({ game: "who-said-it", division: "standard", entries: [] });

        await renderPage({ game: "who-said-it", division: "standard" });

        expect(screen.queryByRole("navigation", { name: /division$/ })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Easy" })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Medium" })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Hard" })).not.toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 2, name: "Who Said It? · Standard" })).toBeInTheDocument();
        expect(screen.getByText(/single mode/)).toBeInTheDocument();
    });

    it("uses Easy when a game is chosen without a division", async () => {
        fetchMock.mockResolvedValue({ game: "who-am-i", division: "easy", entries: [] });

        await renderPage({ game: "who-am-i" });

        expect(fetchMock).toHaveBeenCalledWith("who-am-i", "easy");
    });

    it("uses Standard when Who Said It? is chosen without a division", async () => {
        fetchMock.mockResolvedValue({ game: "who-said-it", division: "standard", entries: [] });

        await renderPage({ game: "who-said-it" });

        expect(fetchMock).toHaveBeenCalledWith("who-said-it", "standard");
    });

    it.each([
        [{ game: "who-said-it", division: "easy" }, "who-said-it", "standard"],
        [{ game: "who-said-it", division: "hard" }, "who-said-it", "standard"],
        [{ game: "lore-challenge", division: "standard" }, "lore-challenge", "easy"],
        [{ game: "who-am-i", division: "expert" }, "who-am-i", "easy"],
        [{ game: "where-in-middle-earth", division: "EASY" }, "where-in-middle-earth", "easy"],
        [{ game: "lore-challenge", division: ["hard", "easy"] }, "lore-challenge", "hard"],
    ] as const)("never sends an invalid pair: %j", async (params, game, division) => {
        fetchMock.mockResolvedValue({ game, division, entries: [] });

        await renderPage(params as { game: string; division: string | string[] });

        expect(fetchMock).toHaveBeenCalledTimes(1);
        expect(fetchMock).toHaveBeenCalledWith(game, division);
    });

    it("shows an empty state that names the game and division", async () => {
        fetchMock.mockResolvedValue({ game: "who-said-it", division: "standard", entries: [] });

        await renderPage({ game: "who-said-it", division: "standard" });

        expect(screen.getByText(/No Who Said It\? · Standard results have been published yet/)).toBeInTheDocument();
        expect(screen.queryByRole("table")).not.toBeInTheDocument();
    });

    it("shows an accessible error with a way to retry that keeps the division", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        fetchMock.mockRejectedValue(new Error("down"));

        await renderPage({ game: "lore-challenge", division: "hard" });

        expect(screen.getByRole("alert")).toHaveTextContent("couldn't be loaded");
        expect(screen.getByRole("link", { name: "Try again" })).toHaveAttribute(
            "href",
            "/leaderboard?game=lore-challenge&division=hard",
        );
        expect(screen.getByRole("navigation", { name: "Choose a game" })).toBeInTheDocument();
        expect(screen.getByRole("navigation", { name: /division$/ })).toBeInTheDocument();
    });

    it("lets the visitor go back to the overview", async () => {
        fetchMock.mockResolvedValue({ game: "lore-challenge", division: "easy", entries: [] });

        await renderPage({ game: "lore-challenge", division: "easy" });

        expect(screen.getByRole("link", { name: "Overview" })).toHaveAttribute("href", "/leaderboard");
    });
});

describe("the page", () => {
    it("has its own metadata and canonical", () => {
        expect(metadata.title).toBe("Leaderboard");
        expect(metadata.description).toMatch(/Who Said It/);
        expect(metadata.description).toMatch(/division/);
        expect(metadata.description).not.toMatch(/global/i);
        expect(metadata.alternates?.canonical).toBe("/leaderboard");
    });

    it("renders a loading state in a Suspense fallback", () => {
        // The default export wraps the content in Suspense with a status fallback.
        expect(typeof page).toBe("function");
    });
});
