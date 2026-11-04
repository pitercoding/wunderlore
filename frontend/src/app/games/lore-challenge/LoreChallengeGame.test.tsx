import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type {
    LoreChallengeAnswer,
    LoreChallengeQuestion,
    LoreChallengeResult,
} from "@/lib/api/loreChallenge";
import {
    fetchLoreChallengeQuestion,
    fetchLoreChallengeResult,
    startLoreChallengeMatch,
    submitLoreChallengeAnswer,
} from "./actions";
import LoreChallengeGame from "./LoreChallengeGame";

vi.mock("./actions", () => ({
    startLoreChallengeMatch: vi.fn(),
    fetchLoreChallengeQuestion: vi.fn(),
    submitLoreChallengeAnswer: vi.fn(),
    fetchLoreChallengeResult: vi.fn(),
}));

const startMock = vi.mocked(startLoreChallengeMatch);
const questionMock = vi.mocked(fetchLoreChallengeQuestion);
const answerMock = vi.mocked(submitLoreChallengeAnswer);
const resultMock = vi.mocked(fetchLoreChallengeResult);

const MATCH_ID = "123e4567-e89b-42d3-a456-426614174000";
const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";

function question(number: number, total = 10): LoreChallengeQuestion {
    return {
        matchId: MATCH_ID,
        category: "ALL",
        difficulty: "MEDIUM",
        number,
        total,
        questionId: `places-medium-${String(number).padStart(2, "0")}`,
        questionCategory: "PLACES",
        prompt: `Question number ${number}?`,
        options: [
            { id: "a", text: `Alpha ${number}` },
            { id: "b", text: `Bravo ${number}` },
            { id: "c", text: `Charlie ${number}` },
            { id: "d", text: `Delta ${number}` },
        ],
    };
}

function answerFor(
    number: number,
    optionId: string,
    correct: boolean,
    score: number,
    total = 10,
): LoreChallengeAnswer {
    return {
        matchId: MATCH_ID,
        questionId: question(number).questionId,
        submittedOptionId: optionId,
        correct,
        correctOptionId: correct ? optionId : "c",
        explanation: `Explanation ${number}.`,
        pointsAwarded: correct ? 20 : 0,
        score,
        answered: number,
        total,
        finished: number === total,
    };
}

const RESULT: LoreChallengeResult = {
    matchId: MATCH_ID,
    category: "ALL",
    difficulty: "MEDIUM",
    totalQuestions: 10,
    correctAnswers: 7,
    incorrectAnswers: 3,
    score: 140,
    maxScore: 200,
    percentage: 70,
    durationSeconds: 95,
    rank: "Scholar of Rivendell",
};

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((res) => {
        resolve = res;
    });

    return { promise, resolve };
}

function begin() {
    return screen.getByRole("button", { name: "Begin the challenge" });
}

// Plays the match down to its last answered question, all answers right.
async function playToTheEnd(user: ReturnType<typeof userEvent.setup>, total: number) {
    startMock.mockResolvedValue({ ok: true, question: question(1, total) });
    await user.click(begin());

    for (let number = 1; number <= total; number++) {
        answerMock.mockResolvedValueOnce({
            ok: true,
            answer: answerFor(number, "a", true, number * 20, total),
        });
        await user.click(await screen.findByRole("button", { name: new RegExp(`Alpha ${number}`) }));

        if (number < total) {
            questionMock.mockResolvedValueOnce({ ok: true, question: question(number + 1, total) });
            await user.click(await screen.findByRole("button", { name: "Next question" }));
        }
    }
}

afterEach(() => {
    vi.resetAllMocks();
});

describe("LoreChallengeGame menu", () => {
    it("starts on the menu with no backend request", () => {
        render(<LoreChallengeGame />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Lore Challenge" }),
        ).toBeInTheDocument();
        expect(begin()).toBeEnabled();
        expect(screen.getAllByRole("radio")).toHaveLength(9);
        expect(startMock).not.toHaveBeenCalled();
    });

    it("offers all categories and three difficulties with their points", () => {
        render(<LoreChallengeGame />);

        const categories = within(screen.getByRole("group", { name: "Category" }));
        const difficulties = within(screen.getByRole("group", { name: "Difficulty" }));

        for (const label of ["All categories", "Characters", "Places", "Peoples", "History", "Events"]) {
            expect(categories.getByRole("radio", { name: new RegExp(label) })).toBeInTheDocument();
        }

        expect(difficulties.getByRole("radio", { name: /Easy.*10 points each/ })).toBeInTheDocument();
        expect(difficulties.getByRole("radio", { name: /Medium.*20 points each/ })).toBeInTheDocument();
        expect(difficulties.getByRole("radio", { name: /Hard.*30 points each/ })).toBeInTheDocument();
    });

    it("selects every category and every difficulty, one at a time", async () => {
        const user = userEvent.setup();
        render(<LoreChallengeGame />);

        expect(screen.getByRole("radio", { name: /All categories/ })).toBeChecked();
        expect(screen.getByRole("radio", { name: /Medium/ })).toBeChecked();

        await user.click(screen.getByRole("radio", { name: /Places/ }));
        expect(screen.getByRole("radio", { name: /Places/ })).toBeChecked();
        expect(screen.getByRole("radio", { name: /All categories/ })).not.toBeChecked();

        await user.click(screen.getByRole("radio", { name: /Hard/ }));
        expect(screen.getByRole("radio", { name: /Hard/ })).toBeChecked();
        expect(screen.getByRole("radio", { name: /Medium/ })).not.toBeChecked();
    });

    it("lets the keyboard choose with the arrow keys", async () => {
        const user = userEvent.setup();
        render(<LoreChallengeGame />);

        screen.getByRole("radio", { name: /Medium/ }).focus();
        await user.keyboard("{ArrowRight}");

        expect(screen.getByRole("radio", { name: /Hard/ })).toBeChecked();
        expect(screen.getByRole("radio", { name: /Hard/ })).toHaveFocus();
    });

    it("starts the match with the chosen category and difficulty", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        render(<LoreChallengeGame />);

        await user.click(screen.getByRole("radio", { name: /History/ }));
        await user.click(screen.getByRole("radio", { name: /Easy/ }));
        await user.click(begin());

        expect(startMock).toHaveBeenCalledTimes(1);
        expect(startMock).toHaveBeenCalledWith("HISTORY", "EASY");
        expect(await screen.findByText("Question number 1?")).toBeInTheDocument();
    });

    it("shows a loading state and starts only one match on a double click", async () => {
        const user = userEvent.setup();
        const pending = deferred<Awaited<ReturnType<typeof startLoreChallengeMatch>>>();
        startMock.mockReturnValue(pending.promise);
        render(<LoreChallengeGame />);

        await user.dblClick(begin());

        expect(screen.getByRole("status")).toHaveTextContent("Preparing your match...");
        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();
        expect(screen.getAllByRole("radio")[0]).toBeDisabled();
        expect(startMock).toHaveBeenCalledTimes(1);

        await act(async () => pending.resolve({ ok: true, question: question(1) }));

        expect(await screen.findByText("Question number 1?")).toBeInTheDocument();
    });

    it("shows an unavailable message and lets the player try again", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });
        render(<LoreChallengeGame />);

        await user.click(begin());

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.queryByText("Question number 1?")).not.toBeInTheDocument();

        startMock.mockResolvedValueOnce({ ok: true, question: question(1) });
        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByText("Question number 1?")).toBeInTheDocument();
        expect(startMock).toHaveBeenCalledTimes(2);
    });

    it("recovers when the action itself throws", async () => {
        const user = userEvent.setup();
        startMock.mockRejectedValue(new Error("boom"));
        render(<LoreChallengeGame />);

        await user.click(begin());

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Something went wrong. Please try again.",
        );
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    });
});

describe("LoreChallengeGame question", () => {
    async function openFirstQuestion(user: ReturnType<typeof userEvent.setup>) {
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        render(<LoreChallengeGame />);
        await user.click(begin());
        await screen.findByText("Question number 1?");
    }

    it("shows the number, the progress, the prompt and four options", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);

        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();
        expect(screen.getByRole("progressbar", { name: "Match progress" })).toHaveAttribute("max", "10");
        expect(screen.getByRole("progressbar", { name: "Match progress" })).toHaveAttribute("value", "0");
        expect(screen.getByRole("heading", { level: 2, name: "Question number 1?" })).toHaveFocus();

        const options = within(screen.getByRole("group", { name: "Answer choices" })).getAllByRole("button");

        expect(options).toHaveLength(4);
        expect(options.map((option) => option.textContent)).toEqual([
            "Alpha 1", "Bravo 1", "Charlie 1", "Delta 1",
        ]);
        expect(screen.queryByText(/Explanation/)).not.toBeInTheDocument();
    });

    it("shows positive feedback, the explanation and the points after a correct answer", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        answerMock.mockResolvedValue({ ok: true, answer: answerFor(1, "a", true, 20) });

        await user.click(screen.getByRole("button", { name: /Alpha 1/ }));

        expect(answerMock).toHaveBeenCalledWith(MATCH_ID, "places-medium-01", "a");
        expect(await screen.findByText("Correct! +20 points")).toBeInTheDocument();
        expect(screen.getByText("Explanation 1.")).toBeInTheDocument();
        // The marks are not color only: a visually hidden note names the answer too.
        expect(screen.getByRole("button", { name: /Alpha 1.*Your answer, correct/ })).toBeDisabled();
        expect(screen.getByText("20", { selector: "span" })).toBeInTheDocument();
        expect(screen.getByRole("progressbar")).toHaveAttribute("value", "1");
    });

    it("shows negative feedback and reveals the right option after a wrong answer", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        answerMock.mockResolvedValue({ ok: true, answer: answerFor(1, "b", false, 0) });

        await user.click(screen.getByRole("button", { name: /Bravo 1/ }));

        expect(await screen.findByText("Not quite!")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /Bravo 1.*Your answer, incorrect/ })).toBeDisabled();
        expect(screen.getByRole("button", { name: /Charlie 1.*Correct answer/ })).toBeDisabled();
        expect(screen.getByText("Explanation 1.")).toBeInTheDocument();
    });

    it("moves focus to the next-question button once answered", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        answerMock.mockResolvedValue({ ok: true, answer: answerFor(1, "a", true, 20) });

        await user.click(screen.getByRole("button", { name: /Alpha 1/ }));

        expect(await screen.findByRole("button", { name: "Next question" })).toHaveFocus();
    });

    it("submits a double click once and locks the options while checking", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        const pending = deferred<Awaited<ReturnType<typeof submitLoreChallengeAnswer>>>();
        answerMock.mockReturnValue(pending.promise);

        await user.dblClick(screen.getByRole("button", { name: /Alpha 1/ }));

        expect(screen.getByRole("status")).toHaveTextContent("Checking your answer...");
        expect(screen.getByRole("button", { name: /Bravo 1/ })).toBeDisabled();
        expect(answerMock).toHaveBeenCalledTimes(1);

        await act(async () => pending.resolve({ ok: true, answer: answerFor(1, "a", true, 20) }));

        expect(await screen.findByText("Correct! +20 points")).toBeInTheDocument();
        expect(answerMock).toHaveBeenCalledTimes(1);
    });

    it("keeps the question open after a temporary failure so the player can answer again", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        answerMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });

        await user.click(screen.getByRole("button", { name: /Alpha 1/ }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("button", { name: /Bravo 1/ })).toBeEnabled();

        answerMock.mockResolvedValueOnce({ ok: true, answer: answerFor(1, "b", false, 0) });
        await user.click(screen.getByRole("button", { name: /Bravo 1/ }));

        expect(await screen.findByText("Not quite!")).toBeInTheDocument();
    });

    it("offers a new match when the match expired", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        answerMock.mockResolvedValue({
            ok: false,
            kind: "terminal",
            message: "This match has expired or is no longer available.",
        });

        await user.click(screen.getByRole("button", { name: /Alpha 1/ }));

        expect(await screen.findByRole("alert")).toHaveTextContent("expired");
        expect(screen.getByRole("button", { name: /Bravo 1/ })).toBeDisabled();

        await user.click(screen.getByRole("button", { name: "Start a new match" }));

        expect(begin()).toBeEnabled();
    });

    it("resynchronizes with the server when an answer is refused as stale", async () => {
        const user = userEvent.setup();
        await openFirstQuestion(user);
        answerMock.mockResolvedValue({
            ok: false,
            kind: "stale",
            message: "This match has moved on without you. Let's pick up where it stands.",
        });
        questionMock.mockResolvedValue({ ok: true, question: question(2) });

        await user.click(screen.getByRole("button", { name: /Alpha 1/ }));
        await user.click(await screen.findByRole("button", { name: "Continue" }));

        expect(questionMock).toHaveBeenCalledWith(MATCH_ID);
        expect(await screen.findByText("Question number 2?")).toBeInTheDocument();
    });
});

describe("LoreChallengeGame progress through the match", () => {
    it("advances to the next question and keeps the running score", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        render(<LoreChallengeGame />);
        await user.click(begin());

        answerMock.mockResolvedValue({ ok: true, answer: answerFor(1, "a", true, 20) });
        await user.click(await screen.findByRole("button", { name: /Alpha 1/ }));
        questionMock.mockResolvedValue({ ok: true, question: question(2) });
        await user.click(await screen.findByRole("button", { name: "Next question" }));

        expect(await screen.findByText("Question number 2?")).toBeInTheDocument();
        expect(screen.getByText("Question 2 of 10")).toBeInTheDocument();
        expect(screen.getByText("20", { selector: "span" })).toBeInTheDocument();
        expect(screen.getByRole("progressbar")).toHaveAttribute("value", "1");
        expect(screen.queryByText("Explanation 1.")).not.toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 2 })).toHaveFocus();
    });

    it("does not advance twice on a double click of the next button", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        render(<LoreChallengeGame />);
        await user.click(begin());
        answerMock.mockResolvedValue({ ok: true, answer: answerFor(1, "a", true, 20) });
        await user.click(await screen.findByRole("button", { name: /Alpha 1/ }));

        const pending = deferred<Awaited<ReturnType<typeof fetchLoreChallengeQuestion>>>();
        questionMock.mockReturnValue(pending.promise);

        await user.dblClick(await screen.findByRole("button", { name: "Next question" }));

        expect(screen.getByRole("status")).toHaveTextContent("Preparing the next question...");
        expect(questionMock).toHaveBeenCalledTimes(1);

        await act(async () => pending.resolve({ ok: true, question: question(2) }));

        expect(await screen.findByText("Question number 2?")).toBeInTheDocument();
    });

    it("lets the player retry loading the next question after a failure", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        render(<LoreChallengeGame />);
        await user.click(begin());
        answerMock.mockResolvedValue({ ok: true, answer: answerFor(1, "a", true, 20) });
        await user.click(await screen.findByRole("button", { name: /Alpha 1/ }));

        questionMock.mockResolvedValueOnce({ ok: false, kind: "temporary", message: UNAVAILABLE });
        await user.click(await screen.findByRole("button", { name: "Next question" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        // The answered question stays on screen, so nothing is lost.
        expect(screen.getByText("Explanation 1.")).toBeInTheDocument();

        questionMock.mockResolvedValueOnce({ ok: true, question: question(2) });
        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByText("Question number 2?")).toBeInTheDocument();
    });

    it("offers the results, not another question, after the last answer", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValue({ ok: true, question: question(10, 10) });
        render(<LoreChallengeGame />);
        await user.click(begin());
        answerMock.mockResolvedValue({ ok: true, answer: answerFor(10, "a", true, 200) });

        await user.click(await screen.findByRole("button", { name: /Alpha 10/ }));

        expect(await screen.findByRole("button", { name: "See your results" })).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Next question" })).not.toBeInTheDocument();
    });
});

describe("LoreChallengeGame results", () => {
    async function finishMatch(user: ReturnType<typeof userEvent.setup>) {
        render(<LoreChallengeGame />);
        await playToTheEnd(user, 3);
        resultMock.mockResolvedValue({ ok: true, result: RESULT });
        await user.click(await screen.findByRole("button", { name: "See your results" }));
        await screen.findByText("Scholar of Rivendell");
    }

    it("shows the score, the counts, the accuracy, the time and the rank from the backend", async () => {
        const user = userEvent.setup();
        await finishMatch(user);

        expect(resultMock).toHaveBeenCalledWith(MATCH_ID);
        expect(screen.getByRole("heading", { level: 1, name: "Your results" })).toBeInTheDocument();
        expect(screen.getByRole("heading", { level: 2, name: "Scholar of Rivendell" })).toHaveFocus();
        expect(screen.getByText("140")).toBeInTheDocument();
        expect(screen.getByText(/\/ 200 points/)).toBeInTheDocument();
        expect(screen.getByText("All categories · Medium")).toBeInTheDocument();

        const stats = within(screen.getByText("Correct").closest("dl") as HTMLElement);

        expect(stats.getByText("Correct").nextSibling).toHaveTextContent("7");
        expect(stats.getByText("Incorrect").nextSibling).toHaveTextContent("3");
        expect(stats.getByText("Accuracy").nextSibling).toHaveTextContent("70%");
        expect(stats.getByText("Time").nextSibling).toHaveTextContent("1 min 35 s");
    });

    it("links back to the games", async () => {
        const user = userEvent.setup();
        await finishMatch(user);

        expect(screen.getByRole("link", { name: "Back to the games" })).toHaveAttribute(
            "href",
            "/#experiences",
        );
    });

    it("plays again with the same category and difficulty", async () => {
        const user = userEvent.setup();

        render(<LoreChallengeGame />);
        await user.click(screen.getByRole("radio", { name: /Events/ }));
        await user.click(screen.getByRole("radio", { name: /Hard/ }));
        await playToTheEnd(user, 3);
        resultMock.mockResolvedValue({ ok: true, result: RESULT });
        await user.click(await screen.findByRole("button", { name: "See your results" }));
        await screen.findByText("Scholar of Rivendell");

        startMock.mockClear();
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        await user.click(screen.getByRole("button", { name: "Play again" }));

        expect(startMock).toHaveBeenCalledWith("EVENTS", "HARD");
        expect(await screen.findByText("Question number 1?")).toBeInTheDocument();
        // A fresh match: no leftover score.
        expect(screen.getByText("0", { selector: "span" })).toBeInTheDocument();
    });

    it("returns to the menu with the previous choices kept", async () => {
        const user = userEvent.setup();

        render(<LoreChallengeGame />);
        await user.click(screen.getByRole("radio", { name: /Peoples/ }));
        await playToTheEnd(user, 3);
        resultMock.mockResolvedValue({ ok: true, result: RESULT });
        await user.click(await screen.findByRole("button", { name: "See your results" }));
        await user.click(await screen.findByRole("button", { name: "Change category or difficulty" }));

        expect(begin()).toBeEnabled();
        expect(screen.getByRole("radio", { name: /Peoples/ })).toBeChecked();
    });

    it("shows an error and a retry when playing again fails", async () => {
        const user = userEvent.setup();
        await finishMatch(user);
        startMock.mockResolvedValue({ ok: false, kind: "temporary", message: UNAVAILABLE });

        await user.click(screen.getByRole("button", { name: "Play again" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
        // The result stays on screen.
        expect(screen.getByText("Scholar of Rivendell")).toBeInTheDocument();
    });

    it("loads the result when the server says the match is already over", async () => {
        const user = userEvent.setup();
        startMock.mockResolvedValue({ ok: true, question: question(1) });
        render(<LoreChallengeGame />);
        await user.click(begin());
        answerMock.mockResolvedValue({
            ok: false,
            kind: "stale",
            message: "This match has moved on without you. Let's pick up where it stands.",
        });
        // Asking for the question gets a 409 (finished); the game falls back to the result.
        questionMock.mockResolvedValue({ ok: false, kind: "stale", message: "stale" });
        resultMock.mockResolvedValue({ ok: true, result: RESULT });

        await user.click(await screen.findByRole("button", { name: /Alpha 1/ }));
        await user.click(await screen.findByRole("button", { name: "Continue" }));

        expect(await screen.findByText("Scholar of Rivendell")).toBeInTheDocument();
    });
});
