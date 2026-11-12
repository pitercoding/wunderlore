import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WhoAmIAnswer, WhoAmIResult, WhoAmIRound } from "@/lib/api/whoAmI";
import {
    fetchWhoAmIResult,
    fetchWhoAmIRound,
    startWhoAmIGame,
    submitWhoAmIAnswer,
} from "./actions";
import WhoAmIGame from "./WhoAmIGame";

vi.mock("./actions", () => ({
    startWhoAmIGame: vi.fn(),
    fetchWhoAmIRound: vi.fn(),
    submitWhoAmIAnswer: vi.fn(),
    fetchWhoAmIResult: vi.fn(),
}));

const startMock = vi.mocked(startWhoAmIGame);
const roundMock = vi.mocked(fetchWhoAmIRound);
const answerMock = vi.mocked(submitWhoAmIAnswer);
const resultMock = vi.mocked(fetchWhoAmIResult);

const GAME_ID = "123e4567-e89b-42d3-a456-426614174000";
const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";

// Names carry the round and the clue, so a test can tell one set of options from the next.
function optionsFor(round: number, clue: number) {
    return ["a", "b", "c", "d"].map((id, index) => ({
        id,
        text: `${["Alpha", "Bravo", "Charlie", "Delta"][index]} ${round}.${clue}`,
    }));
}

function cluesFor(round: number, upTo: number): string[] {
    return Array.from({ length: upTo }, (_, index) => `Clue text ${round}-${index + 1}.`);
}

function round(roundNumber: number, clueNumber = 1, score = 0): WhoAmIRound {
    return {
        gameId: GAME_ID,
        difficulty: "MEDIUM",
        roundNumber,
        totalRounds: 10,
        clueNumber,
        totalClues: 5,
        clues: cluesFor(roundNumber, clueNumber),
        options: optionsFor(roundNumber, clueNumber),
        score,
        maxScore: 1000,
    };
}

function incorrect(roundNumber: number, clueNumber: number, optionId: string, score = 0): WhoAmIAnswer {
    return {
        gameId: GAME_ID,
        roundNumber,
        clueNumber,
        submittedOptionId: optionId,
        correct: false,
        roundFinished: false,
        pointsAwarded: 0,
        score,
        gameFinished: false,
        next: {
            clueNumber: clueNumber + 1,
            clues: cluesFor(roundNumber, clueNumber + 1),
            options: optionsFor(roundNumber, clueNumber + 1),
        },
        reveal: null,
        result: null,
    };
}

function ended(
    roundNumber: number,
    clueNumber: number,
    optionId: string,
    correct: boolean,
    points: number,
    score: number,
    result: WhoAmIResult | null = null,
): WhoAmIAnswer {
    return {
        gameId: GAME_ID,
        roundNumber,
        clueNumber,
        submittedOptionId: optionId,
        correct,
        roundFinished: true,
        pointsAwarded: points,
        score,
        gameFinished: result !== null,
        next: null,
        reveal: { characterName: `Character ${roundNumber}`, clues: cluesFor(roundNumber, 5) },
        result,
    };
}

const RESULT: WhoAmIResult = {
    gameId: GAME_ID,
    difficulty: "MEDIUM",
    totalRounds: 10,
    score: 640,
    maxScore: 1000,
    percentage: 64,
    identified: 8,
    incorrectAnswers: 9,
    unidentified: 2,
    rounds: [
        { roundNumber: 1, characterName: "Character 1", cluesUsed: 1, identified: true, points: 100, wrongAttempts: 0 },
        { roundNumber: 2, characterName: "Character 2", cluesUsed: 3, identified: true, points: 60, wrongAttempts: 2 },
        { roundNumber: 3, characterName: "Character 3", cluesUsed: 5, identified: false, points: 0, wrongAttempts: 5 },
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

async function begin(user: ReturnType<typeof userEvent.setup>, first = round(1)) {
    startMock.mockResolvedValue({ ok: true, round: first });
    await user.click(startButton());
    await screen.findByRole("heading", { level: 2, name: /Round \d+ of 10/ });
}

// The running score, "N points": the only paragraph that is just that.
function scoreLabel() {
    return screen.getByText(
        (_, element) => element?.tagName === "P" && /^\d+ points$/.test(element.textContent ?? ""),
    );
}

function optionButton(name: RegExp | string) {
    return screen.getByRole("button", { name });
}

// Plays rounds 1 to total-1 identifying each at the first clue, then leaves the last one to the caller.
async function playTo(user: ReturnType<typeof userEvent.setup>, lastRound: number) {
    await begin(user);

    for (let number = 1; number < lastRound; number++) {
        answerMock.mockResolvedValueOnce({
            ok: true,
            answer: ended(number, 1, "a", true, 100, number * 100),
        });
        await user.click(optionButton(`Alpha ${number}.1`));
        roundMock.mockResolvedValueOnce({ ok: true, round: round(number + 1, 1, number * 100) });
        await user.click(await screen.findByRole("button", { name: "Next character" }));
        await screen.findByRole("heading", { level: 2, name: `Round ${number + 1} of 10` });
    }
}

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhoAmIGame: menu", () => {
    it("starts on the menu with the name, the subtitle and no backend request", () => {
        render(<WhoAmIGame />);

        expect(screen.getByRole("heading", { level: 1, name: "Who Am I?" })).toBeInTheDocument();
        expect(screen.getByText("Guess the Character")).toBeInTheDocument();
        expect(
            screen.getByText("Five clues. One character. How well do you know Middle-earth?"),
        ).toBeInTheDocument();
        expect(startButton()).toBeEnabled();
        expect(startMock).not.toHaveBeenCalled();
    });

    it("explains the mechanic and what each clue is worth", () => {
        render(<WhoAmIGame />);

        expect(screen.getByText(/Ten characters of Middle-earth, five clues each/)).toBeInTheDocument();

        const ladder = within(screen.getByRole("list", { name: "Points by clue" }));

        for (const [index, points] of [100, 80, 60, 40, 20].entries()) {
            const item = ladder.getAllByRole("listitem")[index];

            expect(item).toHaveTextContent(`Clue ${index + 1}`);
            expect(item).toHaveTextContent(`${points} points`);
        }
    });

    it("offers three difficulties, Medium selected, and a way back to the games", () => {
        render(<WhoAmIGame />);

        const group = within(screen.getByRole("group", { name: "Difficulty" }));

        expect(group.getAllByRole("radio").map((radio) => (radio as HTMLInputElement).value)).toEqual([
            "EASY",
            "MEDIUM",
            "HARD",
        ]);
        expect(group.getByRole("radio", { name: /Medium/ })).toBeChecked();
        expect(screen.getByRole("link", { name: "Back to the games" })).toHaveAttribute(
            "href",
            "/#experiences",
        );
    });

    it("selects every difficulty, one at a time", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);

        for (const name of [/Easy/, /Hard/, /Medium/]) {
            await user.click(screen.getByRole("radio", { name }));
            expect(screen.getByRole("radio", { name })).toBeChecked();
            expect(screen.getAllByRole("radio").filter((radio) => (radio as HTMLInputElement).checked)).toHaveLength(1);
        }
    });

    it("lets the keyboard choose with the arrow keys", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);

        screen.getByRole("radio", { name: /Medium/ }).focus();
        await user.keyboard("{ArrowRight}");

        expect(screen.getByRole("radio", { name: /Hard/ })).toBeChecked();
    });

    it("starts the game with the chosen difficulty", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);

        await user.click(screen.getByRole("radio", { name: /Hard/ }));
        await begin(user);

        expect(startMock).toHaveBeenCalledTimes(1);
        expect(startMock).toHaveBeenCalledWith("HARD");
    });

    it("shows a loading state and starts only one game on a double click", async () => {
        const user = userEvent.setup();
        const pending = deferred<Awaited<ReturnType<typeof startWhoAmIGame>>>();
        startMock.mockReturnValue(pending.promise);
        render(<WhoAmIGame />);

        await user.dblClick(startButton());

        expect(startMock).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("status")).toHaveTextContent("Opening the case file...");
        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();
        expect(screen.getByRole("radio", { name: /Easy/ })).toBeDisabled();

        pending.resolve({ ok: true, round: round(1) });

        expect(await screen.findByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();
    });

    it("shows an unavailable message and lets the player try again", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });
        render(<WhoAmIGame />);

        await user.click(startButton());

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);

        startMock.mockResolvedValueOnce({ ok: true, round: round(1) });
        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();
        expect(startMock).toHaveBeenCalledTimes(2);
    });

    it("recovers when the action itself throws", async () => {
        const user = userEvent.setup();
        startMock.mockRejectedValueOnce(new Error("boom"));
        render(<WhoAmIGame />);

        await user.click(startButton());

        expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong. Please try again.");
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    });
});

describe("WhoAmIGame: a round", () => {
    it("shows the progress, the difficulty, the score, the first clue and four options", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);

        expect(screen.getByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();
        expect(screen.getByText("Medium")).toBeInTheDocument();
        expect(scoreLabel()).toHaveTextContent("0 points");
        expect(screen.getByRole("progressbar", { name: "Game progress" })).toHaveAttribute("value", "0");
        expect(screen.getByText("Clue text 1-1.")).toBeInTheDocument();

        const options = within(screen.getByRole("group", { name: "Choose a character" })).getAllByRole("button");

        expect(options.map((option) => option.textContent)).toEqual([
            "Alpha 1.1", "Bravo 1.1", "Charlie 1.1", "Delta 1.1",
        ]);
    });

    it("moves focus to the round heading when the round opens", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);

        expect(screen.getByRole("heading", { level: 2, name: "Round 1 of 10" })).toHaveFocus();
    });

    it("marks the five clues as current, used and locked without revealing the later ones", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);

        const tracker = within(screen.getByRole("list", { name: "Clues used" }));
        const items = tracker.getAllByRole("listitem");

        expect(items).toHaveLength(5);
        expect(items[0]).toHaveTextContent("Clue 1, current");
        expect(items[0]).toHaveAttribute("aria-current", "step");
        expect(items[1]).toHaveTextContent("Clue 2, not yet revealed");
        expect(items[4]).toHaveTextContent("Clue 5, not yet revealed");
        expect(screen.queryByText("Clue text 1-2.")).not.toBeInTheDocument();
    });

    it("submits the round, the clue and the option, and never a score or correctness", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValue({ ok: true, answer: incorrect(1, 1, "b") });

        await user.click(optionButton("Bravo 1.1"));

        expect(answerMock).toHaveBeenCalledTimes(1);
        expect(answerMock).toHaveBeenCalledWith(GAME_ID, 1, 1, "b");
    });

    it("tells a wrong answer apart and reveals the next clue and a new set of names", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValue({ ok: true, answer: incorrect(1, 1, "b") });

        await user.click(optionButton("Bravo 1.1"));

        expect(await screen.findByText("Clue text 1-2.")).toBeInTheDocument();
        expect(screen.getByText("Clue text 1-1.")).toBeInTheDocument();
        expect(screen.getByText("Bravo 1.1 is not the one. Clue 2 is now revealed.")).toBeInTheDocument();
        expect(screen.getByText("Ruled out so far: Bravo 1.1")).toBeInTheDocument();
        expect(optionButton("Alpha 1.2")).toBeEnabled();
        expect(screen.queryByRole("button", { name: "Alpha 1.1" })).not.toBeInTheDocument();
        expect(screen.queryByText("Clue text 1-3.")).not.toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();

        const items = within(screen.getByRole("list", { name: "Clues used" })).getAllByRole("listitem");

        expect(items[0]).toHaveTextContent("Clue 1, revealed");
        expect(items[1]).toHaveTextContent("Clue 2, current");
    });

    it("moves focus to the new clue after a wrong answer", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValue({ ok: true, answer: incorrect(1, 1, "b") });

        await user.click(optionButton("Bravo 1.1"));
        await screen.findByText("Clue text 1-2.");

        expect(screen.getByText("Clue text 1-2.").closest("li")).toHaveFocus();
    });

    it("keeps the score after a wrong answer and answers the next clue with the next clue number", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: true, answer: incorrect(1, 1, "b") });
        await user.click(optionButton("Bravo 1.1"));
        await screen.findByText("Clue text 1-2.");

        answerMock.mockResolvedValueOnce({ ok: true, answer: incorrect(1, 2, "c") });
        await user.click(optionButton("Charlie 1.2"));
        await screen.findByText("Clue text 1-3.");

        expect(answerMock).toHaveBeenLastCalledWith(GAME_ID, 1, 2, "c");
        expect(screen.getByText("Ruled out so far: Bravo 1.1, Charlie 1.2")).toBeInTheDocument();
        expect(scoreLabel()).toHaveTextContent("0 points");
    });

    it("shows the points, the character and the next button after a correct answer", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: true, answer: incorrect(1, 1, "b") });
        await user.click(optionButton("Bravo 1.1"));
        await screen.findByText("Clue text 1-2.");

        answerMock.mockResolvedValueOnce({ ok: true, answer: ended(1, 2, "a", true, 80, 80) });
        await user.click(optionButton("Alpha 1.2"));

        expect(await screen.findByRole("heading", { level: 3, name: "Correct! +80 points" })).toBeInTheDocument();
        expect(screen.getByText("Character 1")).toBeInTheDocument();
        expect(scoreLabel()).toHaveTextContent("80 points");
        expect(screen.queryByRole("group", { name: "Choose a character" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Next character" })).toBeEnabled();
    });

    it("moves focus to the verdict when the round ends, so it is read out", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: true, answer: ended(1, 1, "a", true, 100, 100) });

        await user.click(optionButton("Alpha 1.1"));

        expect(await screen.findByRole("heading", { level: 3, name: "Correct! +100 points" })).toHaveFocus();
    });

    it("shows every clue once the round ends, the unused ones marked", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: true, answer: ended(1, 1, "a", true, 100, 100) });

        await user.click(optionButton("Alpha 1.1"));

        await screen.findByRole("heading", { level: 3, name: "Correct! +100 points" });

        expect(screen.getByText("Clue text 1-5.")).toBeInTheDocument();
        expect(screen.getAllByText(/\(not needed\)/, { selector: "span" })).toHaveLength(4);

        const items = within(screen.getByRole("list", { name: "Clues used" })).getAllByRole("listitem");

        expect(items[0]).toHaveTextContent("Clue 1, revealed");
        expect(items[1]).toHaveTextContent("Clue 2, not yet revealed");
    });

    it("reveals the character with no points after the fifth wrong answer", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);

        for (let clue = 1; clue <= 4; clue++) {
            answerMock.mockResolvedValueOnce({ ok: true, answer: incorrect(1, clue, "b") });
            await user.click(optionButton(`Bravo 1.${clue}`));
            await screen.findByText(`Clue text 1-${clue + 1}.`);
        }

        expect(screen.getByText("Clue text 1-5.")).toBeInTheDocument();

        answerMock.mockResolvedValueOnce({ ok: true, answer: ended(1, 5, "b", false, 0, 0) });
        await user.click(optionButton("Bravo 1.5"));

        expect(
            await screen.findByRole("heading", { level: 3, name: "Not identified. This round is worth 0 points." }),
        ).toBeInTheDocument();
        expect(screen.getByText("Character 1")).toBeInTheDocument();
        expect(screen.getByText(/It was/)).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Next character" })).toBeEnabled();
        expect(answerMock).toHaveBeenCalledTimes(5);
        expect(answerMock).toHaveBeenLastCalledWith(GAME_ID, 1, 5, "b");
    });

    it("submits a double click once and locks the options while checking", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        const pending = deferred<Awaited<ReturnType<typeof submitWhoAmIAnswer>>>();
        answerMock.mockReturnValue(pending.promise);

        await user.dblClick(optionButton("Bravo 1.1"));

        expect(answerMock).toHaveBeenCalledTimes(1);
        expect(screen.getByText("Checking your answer...")).toBeInTheDocument();

        for (const button of within(screen.getByRole("group", { name: "Choose a character" })).getAllByRole("button")) {
            expect(button).toBeDisabled();
        }

        pending.resolve({ ok: true, answer: incorrect(1, 1, "b") });

        expect(await screen.findByText("Clue text 1-2.")).toBeInTheDocument();
        expect(answerMock).toHaveBeenCalledTimes(1);
    });

    it("keeps the clue open after a temporary failure so the player can answer again", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });

        await user.click(optionButton("Bravo 1.1"));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(optionButton("Bravo 1.1")).toBeEnabled();
        expect(screen.queryByText("Clue text 1-2.")).not.toBeInTheDocument();

        answerMock.mockResolvedValueOnce({ ok: true, answer: incorrect(1, 1, "b") });
        await user.click(optionButton("Bravo 1.1"));

        expect(await screen.findByText("Clue text 1-2.")).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("recovers when the answer action itself throws", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockRejectedValueOnce(new Error("boom"));

        await user.click(optionButton("Bravo 1.1"));

        expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong. Please try again.");
        expect(optionButton("Bravo 1.1")).toBeEnabled();
    });

    it("offers a new game when the game expired", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({
            ok: false,
            kind: "terminal",
            message: "This game has expired or is no longer available.",
        });

        await user.click(optionButton("Bravo 1.1"));

        expect(await screen.findByRole("alert")).toHaveTextContent("This game has expired");

        for (const button of within(screen.getByRole("group", { name: "Choose a character" })).getAllByRole("button")) {
            expect(button).toBeDisabled();
        }

        await user.click(screen.getByRole("button", { name: "Start a new game" }));

        expect(screen.getByRole("heading", { level: 2, name: "How it works" })).toBeInTheDocument();
    });

    it("resynchronizes with the server when an answer is refused as stale", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: false, kind: "stale", message: "Moved on." });

        await user.click(optionButton("Bravo 1.1"));
        await screen.findByRole("alert");

        roundMock.mockResolvedValueOnce({ ok: true, round: round(1, 3) });
        await user.click(screen.getByRole("button", { name: "Continue" }));

        expect(await screen.findByText("Clue text 1-3.")).toBeInTheDocument();
        expect(roundMock).toHaveBeenCalledWith(GAME_ID);
    });

    it("shows the result when the server says the game is already over", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: false, kind: "stale", message: "Moved on." });
        await user.click(optionButton("Bravo 1.1"));
        await screen.findByRole("alert");

        roundMock.mockResolvedValueOnce({ ok: false, kind: "stale", message: "Game has finished." });
        resultMock.mockResolvedValueOnce({ ok: true, result: RESULT });
        await user.click(screen.getByRole("button", { name: "Continue" }));

        expect(await screen.findByText("Case closed", { selector: "h2" })).toBeInTheDocument();
        expect(resultMock).toHaveBeenCalledWith(GAME_ID);
    });
});

describe("WhoAmIGame: moving between rounds", () => {
    it("advances to the next character and keeps the running score", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await playTo(user, 3);

        expect(screen.getByRole("heading", { level: 2, name: "Round 3 of 10" })).toBeInTheDocument();
        expect(screen.getByText("Clue text 3-1.")).toBeInTheDocument();
        expect(scoreLabel()).toHaveTextContent("200 points");
        expect(roundMock).toHaveBeenCalledTimes(2);
        expect(screen.getByRole("progressbar", { name: "Game progress" })).toHaveAttribute("value", "2");
    });

    it("shows a loading state and asks for the next round once on a double click", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: true, answer: ended(1, 1, "a", true, 100, 100) });
        await user.click(optionButton("Alpha 1.1"));
        const pending = deferred<Awaited<ReturnType<typeof fetchWhoAmIRound>>>();
        roundMock.mockReturnValue(pending.promise);

        await user.dblClick(await screen.findByRole("button", { name: "Next character" }));

        expect(roundMock).toHaveBeenCalledTimes(1);
        expect(screen.getByText("Finding the next character...")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();

        pending.resolve({ ok: true, round: round(2, 1, 100) });

        expect(await screen.findByRole("heading", { level: 2, name: "Round 2 of 10" })).toBeInTheDocument();
    });

    it("lets the player retry loading the next round after a failure", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await begin(user);
        answerMock.mockResolvedValueOnce({ ok: true, answer: ended(1, 1, "a", true, 100, 100) });
        await user.click(optionButton("Alpha 1.1"));
        roundMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });

        await user.click(await screen.findByRole("button", { name: "Next character" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        // The reveal stays on screen while the player retries.
        expect(screen.getByText("Character 1")).toBeInTheDocument();

        roundMock.mockResolvedValueOnce({ ok: true, round: round(2, 1, 100) });
        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 2 of 10" })).toBeInTheDocument();
    });

    it("offers the results, not another round, after the tenth character", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await playTo(user, 10);
        answerMock.mockResolvedValueOnce({
            ok: true,
            answer: ended(10, 1, "a", true, 100, 1000, RESULT),
        });

        await user.click(optionButton("Alpha 10.1"));

        expect(await screen.findByRole("button", { name: "See your results" })).toBeEnabled();
        expect(screen.getByRole("heading", { level: 2, name: "Round 10 of 10" })).toBeInTheDocument();
    });
});

describe("WhoAmIGame: result", () => {
    async function finish(user: ReturnType<typeof userEvent.setup>) {
        await playTo(user, 10);
        answerMock.mockResolvedValueOnce({
            ok: true,
            answer: ended(10, 1, "a", true, 100, 1000, RESULT),
        });
        await user.click(optionButton("Alpha 10.1"));
        await user.click(await screen.findByRole("button", { name: "See your results" }));
        await screen.findByText("Case closed", { selector: "h2" });
    }

    it("shows the score, the percentage and the counts the backend sent, without asking again", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await finish(user);

        expect(resultMock).not.toHaveBeenCalled();
        expect(screen.getByText("640")).toBeInTheDocument();
        expect(screen.getByText("/ 1000 points")).toBeInTheDocument();

        const stat = (label: string) => screen.getByText(label, { selector: "dt" }).closest("div");

        expect(stat("Performance")).toHaveTextContent("64%");
        expect(stat("Identified")).toHaveTextContent("8 of 10");
        expect(stat("Wrong guesses")).toHaveTextContent("9");
        expect(stat("Not identified")).toHaveTextContent("2");
    });

    it("moves focus to the title", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await finish(user);

        expect(screen.getByRole("heading", { level: 2, name: "Case closed" })).toHaveFocus();
    });

    it("summarizes each round with the character, the clue and the points", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await finish(user);

        const rows = within(screen.getByRole("heading", { level: 3, name: "Round by round" }).nextElementSibling as HTMLElement)
            .getAllByRole("listitem");

        expect(rows).toHaveLength(3);
        expect(rows[0]).toHaveTextContent("Character 1");
        expect(rows[0]).toHaveTextContent("Identified at clue 1 of 5");
        expect(rows[0]).toHaveTextContent("100 pts");
        expect(rows[1]).toHaveTextContent("Identified at clue 3 of 5");
        expect(rows[1]).toHaveTextContent("60 pts");
        expect(rows[2]).toHaveTextContent("Not identified after 5 clues");
        expect(rows[2]).toHaveTextContent("0 pts");
    });

    it("links back to the games", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await finish(user);

        expect(screen.getByRole("link", { name: "Back to Games" })).toHaveAttribute("href", "/#experiences");
    });

    it("plays again with the same difficulty", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await user.click(screen.getByRole("radio", { name: /Easy/ }));
        await finish(user);
        startMock.mockClear();
        startMock.mockResolvedValue({ ok: true, round: round(1) });

        await user.click(screen.getByRole("button", { name: "Play Again" }));

        expect(await screen.findByRole("heading", { level: 2, name: "Round 1 of 10" })).toBeInTheDocument();
        expect(startMock).toHaveBeenCalledWith("EASY");
        expect(scoreLabel()).toHaveTextContent("0 points");
    });

    it("returns to the menu with the difficulty kept", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await user.click(screen.getByRole("radio", { name: /Hard/ }));
        await finish(user);

        await user.click(screen.getByRole("button", { name: "Change difficulty" }));

        expect(screen.getByRole("radio", { name: /Hard/ })).toBeChecked();
        expect(startButton()).toBeEnabled();
    });

    it("shows an error and a retry when playing again fails", async () => {
        const user = userEvent.setup();
        render(<WhoAmIGame />);
        await finish(user);
        startMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });

        await user.click(screen.getByRole("button", { name: "Play Again" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    });
});
