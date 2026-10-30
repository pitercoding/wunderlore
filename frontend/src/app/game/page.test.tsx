import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { connection } from "next/server";
import { ApiError, ApiResponseError } from "@/lib/api/client";
import { getRound, type GameRound } from "@/lib/api/game";
import { suspendingRefresh } from "@/test/suspendingRefresh";
import GamePage from "./page";

const refresh = vi.fn();

vi.mock("next/navigation", () => ({
    useRouter: () => ({ refresh }),
}));

vi.mock("next/server", () => ({
    connection: vi.fn(),
}));

vi.mock("@/lib/api/game", () => ({
    getRound: vi.fn(),
}));

// GameContent has its own tests; here it only needs to show what it received.
vi.mock("./GameContent", () => ({
    default: ({ round }: { round: GameRound }) => (
        <p data-testid="game-content">
            {round.roundId}: {round.quote}
        </p>
    ),
}));

const connectionMock = vi.mocked(connection);
const getRoundMock = vi.mocked(getRound);

type SuspenseElement = ReactElement<{
    children: ReactElement;
    fallback: ReactElement;
}>;

// GamePage wraps an async, non-exported loader in <Suspense>. jsdom cannot
// render async Server Components, so the loader is awaited directly and its
// output is rendered, the same way the server would resolve it.
async function renderLoadedPage(options?: Parameters<typeof render>[1]) {
    const page = GamePage() as SuspenseElement;
    const loader = page.props.children.type as () => Promise<ReactElement>;
    return render(await loader(), options);
}

beforeEach(() => {
    connectionMock.mockResolvedValue(undefined);
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("GamePage", () => {
    it("shows a loading status while the round is loading", () => {
        const page = GamePage() as SuspenseElement;

        render(page.props.fallback);

        expect(screen.getByRole("status")).toHaveTextContent(
            "Loading a new round...",
        );
    });

    it("waits for a request before fetching the round", async () => {
        const order: string[] = [];
        connectionMock.mockImplementation(async () => {
            order.push("connection");
        });
        getRoundMock.mockImplementation(async () => {
            order.push("getRound");
            return { roundId: "r1", quote: "q", options: [] };
        });

        await renderLoadedPage();

        expect(order).toEqual(["connection", "getRound"]);
    });

    it("renders the game with the loaded round", async () => {
        getRoundMock.mockResolvedValue({
            roundId: "round-7",
            quote: "Fly, you fools!",
            options: [{ id: "c1", name: "Gandalf" }],
        });

        await renderLoadedPage();

        expect(screen.getByTestId("game-content")).toHaveTextContent(
            "round-7: Fly, you fools!",
        );
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(
            screen.queryByRole("button", { name: "Try again" }),
        ).not.toBeInTheDocument();
    });

    it("retries only when the player asks to, keeping the error visible", async () => {
        const user = userEvent.setup();
        getRoundMock.mockRejectedValue(new TypeError("fetch failed"));
        await renderLoadedPage();

        expect(refresh).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("alert")).toHaveTextContent(
            "Unable to load a game round. Please try again.",
        );
    });

    it("disables the retry until the server answers", async () => {
        const user = userEvent.setup();
        const server = suspendingRefresh();
        refresh.mockImplementation(server.refresh);
        getRoundMock.mockRejectedValue(new TypeError("fetch failed"));
        await renderLoadedPage({ wrapper: server.Wrapper });

        // Awaited: the update suspends, and React only resumes it in an awaited act.
        await act(() =>
            user.click(screen.getByRole("button", { name: "Try again" })),
        );

        expect(
            screen.getByRole("button", { name: "Trying again..." }),
        ).toBeDisabled();
        expect(screen.getByRole("alert")).toBeInTheDocument();

        await act(async () => server.respond());

        expect(
            await screen.findByRole("button", { name: "Try again" }),
        ).toBeEnabled();
    });

    it.each([
        ["an API error", new ApiError("API request failed with status 503", 503)],
        ["a network error", new TypeError("fetch failed")],
        ["an invalid payload", new ApiResponseError("/api/game/round")],
    ])("shows an error message when loading fails with %s", async (_, error) => {
        getRoundMock.mockRejectedValue(error);

        await renderLoadedPage();

        expect(
            screen.getByRole("heading", { level: 1, name: "Who Said It?" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent(
            "Unable to load a game round. Please try again.",
        );
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute(
            "href",
            "/",
        );
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
        expect(screen.queryByTestId("game-content")).not.toBeInTheDocument();
        expect(console.error).toHaveBeenCalledWith(
            "Failed to load game round",
            error,
        );
    });
});
