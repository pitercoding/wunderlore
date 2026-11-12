import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
    WhereInMiddleEarthAccuracy,
    WhereInMiddleEarthAnswer,
    WhereInMiddleEarthResult,
    WhereInMiddleEarthRound,
} from "@/lib/api/whereInMiddleEarth";
import {
    fetchWhereInMiddleEarthResult,
    fetchWhereInMiddleEarthRound,
    startWhereInMiddleEarthGame,
    submitWhereInMiddleEarthAnswer,
} from "./actions";
import WhereInMiddleEarthGame from "./WhereInMiddleEarthGame";

vi.mock("./actions", () => ({
    startWhereInMiddleEarthGame: vi.fn(),
    fetchWhereInMiddleEarthRound: vi.fn(),
    submitWhereInMiddleEarthAnswer: vi.fn(),
    fetchWhereInMiddleEarthResult: vi.fn(),
}));

const startMock = vi.mocked(startWhereInMiddleEarthGame);
const roundMock = vi.mocked(fetchWhereInMiddleEarthRound);
const answerMock = vi.mocked(submitWhereInMiddleEarthAnswer);
const resultMock = vi.mocked(fetchWhereInMiddleEarthResult);

const GAME_ID = "123e4567-e89b-42d3-a456-426614174000";
const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";

function round(roundNumber: number, score = 0): WhereInMiddleEarthRound {
    return {
        gameId: GAME_ID,
        difficulty: "MEDIUM",
        roundNumber,
        totalRounds: 10,
        locationName: `Place ${roundNumber}`,
        description: `What place ${roundNumber} is.`,
        score,
        maxScore: 1000,
    };
}

function answered(
    roundNumber: number,
    accuracy: WhereInMiddleEarthAccuracy,
    points: number,
    score: number,
    result: WhereInMiddleEarthResult | null = null,
): WhereInMiddleEarthAnswer {
    return {
        gameId: GAME_ID,
        roundNumber,
        submitted: { x: 500, y: 350 },
        distance: points === 100 ? 4.2 : 120,
        accuracy,
        pointsAwarded: points,
        score,
        reveal: {
            locationName: `Place ${roundNumber}`,
            regionName: "Eriador",
            period: "Third Age",
            explanation: `Place ${roundNumber} lay by the old road.`,
            x: 504,
            y: 350,
        },
        gameFinished: result !== null,
        result,
    };
}

const RESULT: WhereInMiddleEarthResult = {
    gameId: GAME_ID,
    difficulty: "MEDIUM",
    totalRounds: 10,
    score: 640,
    maxScore: 1000,
    percentage: 64,
    closeHits: 8,
    averageDistance: 48.3,
    rounds: [
        { roundNumber: 1, locationName: "Place 1", accuracy: "EXCELLENT", distance: 4.2, points: 100 },
        { roundNumber: 2, locationName: "Place 2", accuracy: "GOOD", distance: 40, points: 60 },
        { roundNumber: 3, locationName: "Place 3", accuracy: "FAR", distance: 310.5, points: 0 },
    ],
};

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((res) => {
        resolve = res;
    });

    return { promise, resolve };
}

function startButton() {
    return screen.getByRole("button", { name: "Start game" });
}

function mapSvg(container: HTMLElement) {
    const svg = container.querySelector("svg[role='img']") as SVGSVGElement;

    svg.getBoundingClientRect = () =>
        ({ left: 0, top: 0, width: 1000, height: 700, right: 1000, bottom: 700, x: 0, y: 0 }) as DOMRect;

    return svg;
}

// Clicks the map at a point of its own coordinates (the test lays it out at 1:1).
function clickMap(container: HTMLElement, x: number, y: number) {
    fireEvent.click(mapSvg(container), { clientX: x, clientY: y });
}

async function begin(
    user: ReturnType<typeof userEvent.setup>,
    first = round(1),
): Promise<HTMLElement> {
    const view = render(<WhereInMiddleEarthGame />);

    startMock.mockResolvedValue({ ok: true, round: first });
    await user.click(startButton());
    await screen.findByRole("heading", { level: 2, name: /Round \d+ of 10/ });

    return view.container;
}

// The running score, "N points": the only paragraph that is just that.
function scoreLabel() {
    return screen.getByText(
        (_, element) => element?.tagName === "P" && /^\d+ points$/.test(element.textContent ?? ""),
    );
}

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhereInMiddleEarthGame: menu", () => {
    it("starts on the menu with the name, the subtitle and no backend request", () => {
        render(<WhereInMiddleEarthGame />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Where in Middle-earth" }),
        ).toBeInTheDocument();
        expect(screen.getByText("Click the map. Find the place.")).toBeInTheDocument();
        expect(screen.getByText(/Ten places, one map\. How well do you know/)).toBeInTheDocument();
        expect(startButton()).toBeEnabled();
        expect(startMock).not.toHaveBeenCalled();
    });

    it("explains the mechanic, the ten rounds and what each band is worth", () => {
        render(<WhereInMiddleEarthGame />);

        expect(screen.getByText(/Ten places of Middle-earth, one map/)).toBeInTheDocument();
        expect(screen.getByText(/A perfect game is worth 1,000 points/)).toBeInTheDocument();

        const bands = within(screen.getByRole("list", { name: "Points by distance" })).getAllByRole(
            "listitem",
        );

        expect(bands).toHaveLength(5);
        expect(bands[0]).toHaveTextContent("Excellent");
        expect(bands[0]).toHaveTextContent("within 15 units");
        expect(bands[0]).toHaveTextContent("100 points");
        expect(bands[4]).toHaveTextContent("Far away");
        expect(bands[4]).toHaveTextContent("0 points");
    });

    it("offers three difficulties, Medium selected, and a way back to the games", () => {
        render(<WhereInMiddleEarthGame />);

        const group = within(screen.getByRole("group", { name: "Difficulty" }));

        expect(group.getAllByRole("radio")).toHaveLength(3);
        expect(group.getByRole("radio", { name: /Medium/ })).toBeChecked();
        expect(screen.getByRole("link", { name: "Back to the games" })).toHaveAttribute(
            "href",
            "/#experiences",
        );
    });

    it("creates the game with the chosen difficulty only when the player starts", async () => {
        const user = userEvent.setup();

        render(<WhereInMiddleEarthGame />);
        startMock.mockResolvedValue({ ok: true, round: round(1) });

        await user.click(screen.getByRole("radio", { name: /Hard/ }));
        expect(startMock).not.toHaveBeenCalled();

        await user.click(startButton());

        expect(startMock).toHaveBeenCalledTimes(1);
        expect(startMock).toHaveBeenCalledWith("HARD");
    });

    it("shows a loading state and ignores a second click while the game is created", async () => {
        const user = userEvent.setup();
        const pending = deferred<Awaited<ReturnType<typeof startWhereInMiddleEarthGame>>>();

        render(<WhereInMiddleEarthGame />);
        startMock.mockReturnValue(pending.promise);

        await user.click(startButton());

        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();
        expect(screen.getByText("Unrolling the map...")).toBeInTheDocument();
        expect(screen.getByRole("group", { name: "Difficulty" })).toBeDisabled();
        expect(startMock).toHaveBeenCalledTimes(1);

        pending.resolve({ ok: true, round: round(1) });
        await screen.findByRole("heading", { level: 2, name: "Round 1 of 10" });
    });

    it("shows why the game could not start and offers another try", async () => {
        const user = userEvent.setup();

        render(<WhereInMiddleEarthGame />);
        startMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });

        await user.click(startButton());

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("heading", { level: 1, name: "Where in Middle-earth" })).toBeInTheDocument();

        startMock.mockResolvedValueOnce({ ok: true, round: round(1) });
        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("recovers from a request that throws", async () => {
        const user = userEvent.setup();

        render(<WhereInMiddleEarthGame />);
        startMock.mockRejectedValueOnce(new Error("network down"));

        await user.click(startButton());

        expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong. Please try again.");
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    });
});

describe("WhereInMiddleEarthGame: a round", () => {
    it("shows the progress, the score, the question and the map", async () => {
        const user = userEvent.setup();
        const container = await begin(user, round(3, 180));

        expect(screen.getByRole("heading", { level: 2, name: "Round 3 of 10" })).toBeInTheDocument();
        expect(scoreLabel()).toHaveTextContent("180 points");
        expect(screen.getByRole("progressbar", { name: "Game progress" })).toHaveAttribute("value", "2");
        expect(screen.getByText("Medium")).toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 3, name: "Where is Place 3?" })).toBeInTheDocument();
        expect(screen.getByText("What place 3 is.")).toBeInTheDocument();
        expect(mapSvg(container)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
        expect(screen.getByText("No marker yet. Choose a spot on the map.")).toBeInTheDocument();
    });

    it("moves focus to the question when the round starts", async () => {
        const user = userEvent.setup();

        await begin(user);

        expect(screen.getByRole("heading", { level: 3, name: /Where is Place 1/ })).toHaveFocus();
    });

    it("does not send anything when the map is clicked, and enables Confirm", async () => {
        const user = userEvent.setup();
        const container = await begin(user);

        clickMap(container, 385, 226);

        expect(answerMock).not.toHaveBeenCalled();
        expect(screen.getByRole("button", { name: "Confirm" })).toBeEnabled();
        expect(screen.getByText(/Your marker is at 39% of the way from the west edge/)).toBeInTheDocument();
        expect(container.querySelector('[data-marker="selection"]')).toBeInTheDocument();
    });

    it("lets the player change the point before confirming, and sends only the last one", async () => {
        const user = userEvent.setup();
        const container = await begin(user);

        clickMap(container, 100, 100);
        clickMap(container, 385.04, 226.46);
        answerMock.mockResolvedValue({ ok: true, answer: answered(1, "EXCELLENT", 100, 100) });

        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(answerMock).toHaveBeenCalledTimes(1);
        expect(answerMock).toHaveBeenCalledWith(GAME_ID, 1, 385, 226.5);
    });

    it("clears the marker and disables Confirm again", async () => {
        const user = userEvent.setup();
        const container = await begin(user);

        clickMap(container, 100, 100);
        await user.click(screen.getByRole("button", { name: "Clear marker" }));

        expect(container.querySelector('[data-marker="selection"]')).toBeNull();
        expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
    });

    it("works with the keyboard alone", async () => {
        const user = userEvent.setup();

        await begin(user);
        answerMock.mockResolvedValue({ ok: true, answer: answered(1, "GOOD", 60, 60) });

        screen.getByRole("group", { name: /Map of Middle-earth/ }).focus();
        // The first arrow key puts the marker in the middle; the next two move it.
        await user.keyboard("{ArrowRight}{ArrowRight}{ArrowDown}");
        await user.tab();

        expect(screen.getByRole("button", { name: "Clear marker" })).toHaveFocus();

        await user.tab();
        expect(screen.getByRole("button", { name: "Confirm" })).toHaveFocus();
        await user.keyboard("{Enter}");

        expect(answerMock).toHaveBeenCalledWith(GAME_ID, 1, 510, 360);
    });

    it("confirms once, even if Confirm is pressed twice while the answer is checked", async () => {
        const user = userEvent.setup();
        const container = await begin(user);
        const pending = deferred<Awaited<ReturnType<typeof submitWhereInMiddleEarthAnswer>>>();

        clickMap(container, 385, 226);
        answerMock.mockReturnValue(pending.promise);

        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await user.click(screen.getByRole("button", { name: "Checking..." }));

        expect(answerMock).toHaveBeenCalledTimes(1);
        expect(screen.getByText("Checking your answer...")).toBeInTheDocument();

        // The map is locked while the answer is checked.
        clickMap(container, 10, 10);
        expect(container.querySelector('[data-marker="selection"]')).toHaveStyle({ left: "38.5%" });

        pending.resolve({ ok: true, answer: answered(1, "EXCELLENT", 100, 100) });
        await screen.findByRole("heading", { level: 3, name: /Excellent/ });
    });
});

describe("WhereInMiddleEarthGame: feedback", () => {
    async function answerFirstRound(accuracy: WhereInMiddleEarthAccuracy, points: number) {
        const user = userEvent.setup();
        const container = await begin(user);

        clickMap(container, 500, 350);
        answerMock.mockResolvedValue({ ok: true, answer: answered(1, accuracy, points, points) });
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await screen.findByRole("heading", { level: 3, name: new RegExp(accuracy === "FAR" ? "Far away" : "Excellent") });

        return { user, container };
    }

    it("shows the band, the points, the distance, the place and its explanation", async () => {
        const { container } = await answerFirstRound("EXCELLENT", 100);

        expect(screen.getByRole("heading", { level: 3, name: /Excellent: \+100 points/ })).toHaveFocus();
        expect(screen.getByText("4.2 units", { exact: false })).toBeInTheDocument();
        expect(screen.getByText(/from Place 1\./)).toBeInTheDocument();

        const facts = screen.getAllByRole("definition").map((definition) => definition.textContent);

        expect(facts).toEqual(["Place 1", "Eriador", "Third Age", "100 points"]);
        expect(screen.getByText("Place 1 lay by the old road.")).toBeInTheDocument();
        expect(scoreLabel()).toHaveTextContent("100 points");
        expect(container.querySelector('[data-marker="guess"]')).toBeInTheDocument();
        expect(container.querySelector('[data-marker="answer"]')).toHaveTextContent("Place 1");
    });

    it("shows a miss with a word and a symbol, not by color alone", async () => {
        await answerFirstRound("FAR", 0);

        const title = screen.getByRole("heading", { level: 3, name: /Far away: \+0 points/ });

        expect(title).toHaveTextContent("✕");
    });

    it("locks the map and the confirm button once the round is answered", async () => {
        const { container } = await answerFirstRound("EXCELLENT", 100);

        expect(screen.queryByRole("button", { name: "Confirm" })).not.toBeInTheDocument();
        expect(screen.getByText("The round is over.")).toBeInTheDocument();

        clickMap(container, 10, 10);
        expect(container.querySelector('[data-marker="guess"]')).toHaveStyle({ left: "50%" });
    });

    it("goes to the next round, with the previous score", async () => {
        const { user } = await answerFirstRound("EXCELLENT", 100);

        roundMock.mockResolvedValue({ ok: true, round: round(2, 100) });
        await user.click(screen.getByRole("button", { name: "Next Round" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 2 of 10" })).toBeInTheDocument();
        expect(roundMock).toHaveBeenCalledWith(GAME_ID);
        expect(scoreLabel()).toHaveTextContent("100 points");
        // A fresh round: no marker, nothing answered.
        expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();
        expect(screen.queryByText("The round is over.")).not.toBeInTheDocument();
    });

    it("shows a loading state and then an error if the next round cannot be fetched", async () => {
        const { user } = await answerFirstRound("EXCELLENT", 100);
        const pending = deferred<Awaited<ReturnType<typeof fetchWhereInMiddleEarthRound>>>();

        roundMock.mockReturnValueOnce(pending.promise);
        await user.click(screen.getByRole("button", { name: "Next Round" }));

        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();
        expect(screen.getByText("Finding the next place...")).toBeInTheDocument();

        pending.resolve({ ok: false, kind: "temporary", message: UNAVAILABLE });

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);

        roundMock.mockResolvedValueOnce({ ok: true, round: round(2, 100) });
        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 2 of 10" })).toBeInTheDocument();
    });
});

describe("WhereInMiddleEarthGame: failures while answering", () => {
    async function readyToConfirm() {
        const user = userEvent.setup();
        const container = await begin(user);

        clickMap(container, 385, 226);

        return { user, container };
    }

    it("keeps the marker and offers another try after a temporary failure", async () => {
        const { user, container } = await readyToConfirm();

        answerMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(container.querySelector('[data-marker="selection"]')).toBeInTheDocument();

        answerMock.mockResolvedValueOnce({ ok: true, answer: answered(1, "EXCELLENT", 100, 100) });
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByRole("heading", { level: 3, name: /Excellent/ })).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("recovers from an answer that throws", async () => {
        const { user } = await readyToConfirm();

        answerMock.mockRejectedValueOnce(new Error("network down"));
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong. Please try again.");
        expect(screen.getByRole("button", { name: "Confirm" })).toBeEnabled();
    });

    it("sends the player back to the menu when the game is gone", async () => {
        const { user } = await readyToConfirm();

        answerMock.mockResolvedValueOnce({
            ok: false,
            kind: "terminal",
            message: "This game has expired or is no longer available.",
        });
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("expired");
        expect(screen.getByRole("button", { name: "Confirm" })).toBeDisabled();

        await user.click(screen.getByRole("button", { name: "Start a new game" }));

        expect(await screen.findByRole("button", { name: "Start game" })).toBeInTheDocument();
    });

    it("picks the game up where the server says it is after a 409", async () => {
        const { user } = await readyToConfirm();

        answerMock.mockResolvedValueOnce({
            ok: false,
            kind: "stale",
            message: "This game has moved on without you. Let's pick up where it stands.",
        });
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await screen.findByRole("alert");

        roundMock.mockResolvedValueOnce({ ok: true, round: round(2, 100) });
        await user.click(screen.getByRole("button", { name: "Continue" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 2 of 10" })).toBeInTheDocument();
    });

    it("shows the result when a 409 finds the game already over", async () => {
        const { user } = await readyToConfirm();

        answerMock.mockResolvedValueOnce({ ok: false, kind: "stale", message: "moved on" });
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await screen.findByRole("alert");

        roundMock.mockResolvedValueOnce({ ok: false, kind: "stale", message: "over" });
        resultMock.mockResolvedValueOnce({ ok: true, result: RESULT });
        await user.click(screen.getByRole("button", { name: "Continue" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Journey's end" })).toBeInTheDocument();
        expect(resultMock).toHaveBeenCalledWith(GAME_ID);
    });
});

describe("WhereInMiddleEarthGame: the end", () => {
    async function playToTheEnd() {
        const user = userEvent.setup();
        const container = await begin(user, round(10, 540));

        clickMap(container, 500, 350);
        answerMock.mockResolvedValue({ ok: true, answer: answered(10, "EXCELLENT", 100, 640, RESULT) });
        await user.click(screen.getByRole("button", { name: "Confirm" }));

        return user;
    }

    it("offers the results after the tenth round, without another request", async () => {
        const user = await playToTheEnd();

        await user.click(await screen.findByRole("button", { name: "See your results" }));

        expect(roundMock).not.toHaveBeenCalled();
        expect(resultMock).not.toHaveBeenCalled();
        expect(await screen.findByRole("heading", { level: 2, name: "Journey's end" })).toHaveFocus();
    });

    it("shows the total, the near hits, the average distance and the ten answers", async () => {
        const user = await playToTheEnd();

        await user.click(await screen.findByRole("button", { name: "See your results" }));

        expect(screen.getByText("640")).toBeInTheDocument();
        expect(screen.getByText(/\/ 1000 points/)).toBeInTheDocument();

        const stats = Object.fromEntries(
            screen.getAllByRole("term").map((term) => [term.textContent, term.nextElementSibling?.textContent]),
        );

        expect(stats).toEqual({
            Accuracy: "64%",
            "Near hits": "8 of 10",
            "Average distance": "48.3 units",
            Rounds: "10",
        });

        const rows = within(screen.getByRole("list", { name: "" })).getAllByRole("listitem");

        expect(rows).toHaveLength(3);
        expect(rows[0]).toHaveTextContent("Place 1");
        expect(rows[0]).toHaveTextContent("Excellent");
        expect(rows[0]).toHaveTextContent("100 pts");
        expect(rows[2]).toHaveTextContent("Far away");
        expect(rows[2]).toHaveTextContent("310.5 units away");
        expect(rows[2]).toHaveTextContent("0 pts");
    });

    it("plays again with the same difficulty", async () => {
        const user = await playToTheEnd();

        await user.click(await screen.findByRole("button", { name: "See your results" }));
        startMock.mockResolvedValueOnce({ ok: true, round: round(1) });
        await user.click(screen.getByRole("button", { name: "Play Again" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();
        expect(startMock).toHaveBeenLastCalledWith("MEDIUM");
        expect(scoreLabel()).toHaveTextContent("0 points");
    });

    it("lets the player change the difficulty from the results, and go back to the games", async () => {
        const user = await playToTheEnd();

        await user.click(await screen.findByRole("button", { name: "See your results" }));

        expect(screen.getByRole("link", { name: "Back to Games" })).toHaveAttribute("href", "/#experiences");

        await user.click(screen.getByRole("button", { name: "Change difficulty" }));

        expect(await screen.findByRole("button", { name: "Start game" })).toBeInTheDocument();
    });

    it("shows an error if a new game cannot be created from the results", async () => {
        const user = await playToTheEnd();

        await user.click(await screen.findByRole("button", { name: "See your results" }));
        startMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });
        await user.click(screen.getByRole("button", { name: "Play Again" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    });
});

describe("WhereInMiddleEarthGame: structure", () => {
    it("has one main landmark and one h1 on every screen", async () => {
        const user = userEvent.setup();
        const container = await begin(user);

        expect(screen.getAllByRole("main")).toHaveLength(1);
        expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);

        clickMap(container, 500, 350);
        answerMock.mockResolvedValue({ ok: true, answer: answered(1, "GOOD", 60, 60) });
        await user.click(screen.getByRole("button", { name: "Confirm" }));
        await screen.findByRole("button", { name: "Next Round" });

        expect(screen.getAllByRole("main")).toHaveLength(1);
        expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    });
});
