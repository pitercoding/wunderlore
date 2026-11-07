import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WhoSaidItMatchQuestion, WhoSaidItMatchResult } from "@/lib/api/whoSaidItMatch";
import { answerWhoSaidItMatch, fetchWhoSaidItMatchState, startWhoSaidItMatch } from "./matchActions";
import WhoSaidItGame from "./WhoSaidItGame";

vi.mock("next/navigation", () => ({ useRouter: () => ({ refresh: vi.fn() }) }));
vi.mock("./actions", () => ({ createWhoSaidItRound: vi.fn(), submitWhoSaidItAnswer: vi.fn() }));
vi.mock("./matchActions", () => ({
    startWhoSaidItMatch: vi.fn(),
    answerWhoSaidItMatch: vi.fn(),
    fetchWhoSaidItMatchState: vi.fn(),
}));
vi.mock("@/app/leaderboard/actions", () => ({ publishToLeaderboard: vi.fn() }));

const startMock = vi.mocked(startWhoSaidItMatch);
const answerMock = vi.mocked(answerWhoSaidItMatch);
const stateMock = vi.mocked(fetchWhoSaidItMatchState);

const MATCH_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

function question(n: number): WhoSaidItMatchQuestion {
    return {
        matchId: MATCH_ID,
        questionNumber: n,
        totalQuestions: 10,
        quote: `Quote number ${n}`,
        options: [
            { id: `a${n}`, name: `Alpha ${n}` },
            { id: `b${n}`, name: `Beta ${n}` },
            { id: `c${n}`, name: `Gamma ${n}` },
            { id: `d${n}`, name: `Delta ${n}` },
        ],
    };
}

const result: WhoSaidItMatchResult = {
    matchId: MATCH_ID,
    totalQuestions: 10,
    correctAnswers: 7,
    incorrectAnswers: 3,
    score: 70,
    maxScore: 100,
    percentage: 70,
    questions: [
        { questionNumber: 1, quote: "Quote number 1", submittedCharacterName: "Alpha 1", correctCharacterName: "Alpha 1", correct: true },
        { questionNumber: 2, quote: "Quote number 2", submittedCharacterName: "Beta 2", correctCharacterName: "Gamma 2", correct: false },
    ],
};

function nextAnswer(n: number) {
    return {
        ok: true as const,
        answer: {
            matchId: MATCH_ID,
            questionNumber: n,
            submittedCharacterId: `a${n}`,
            matchFinished: false,
            next: question(n + 1),
            result: null,
        },
    };
}

const finalAnswer = {
    ok: true as const,
    answer: {
        matchId: MATCH_ID,
        questionNumber: 10,
        submittedCharacterId: "a10",
        matchFinished: true,
        next: null,
        result,
    },
};

afterEach(() => {
    vi.resetAllMocks();
});

async function startMatch(user: ReturnType<typeof userEvent.setup>) {
    startMock.mockResolvedValue({ ok: true, question: question(1) });
    render(<WhoSaidItGame />);
    await user.click(screen.getByRole("button", { name: "Start ranked match" }));
    await screen.findByText("Quote number 1", { exact: false });
}

describe("Who Said It? competitive match", () => {
    it("keeps the original Play button next to the ranked one and starts nothing by itself", () => {
        render(<WhoSaidItGame />);

        expect(screen.getByRole("button", { name: "Play" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Start ranked match" })).toBeInTheDocument();
        expect(startMock).not.toHaveBeenCalled();
    });

    it("shows progress, the quote and exactly four options", async () => {
        await startMatch(userEvent.setup());

        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();
        expect(screen.getByText("“Quote number 1”")).toBeInTheDocument();

        const group = screen.getByRole("group", { name: "Answer choices" });

        expect(within(group).getAllByRole("button")).toHaveLength(4);
    });

    it("never shows whether an answer was right while the match goes on", async () => {
        answerMock.mockResolvedValue(nextAnswer(1));

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByText("Question 2 of 10")).toBeInTheDocument();
        expect(answerMock).toHaveBeenCalledWith(MATCH_ID, 1, "a1");
        expect(screen.queryByText(/Correct|Not quite|correct answer/i)).not.toBeInTheDocument();
    });

    it("marks the chosen option and disables the others while sending", async () => {
        let resolve!: (value: Awaited<ReturnType<typeof answerWhoSaidItMatch>>) => void;

        answerMock.mockReturnValue(new Promise((res) => (resolve = res)));

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Beta 1" }));

        expect(screen.getByRole("button", { name: "Beta 1" })).toHaveAttribute("aria-pressed", "true");
        expect(screen.getByRole("button", { name: "Alpha 1" })).toBeDisabled();
        expect(screen.getByRole("status")).toHaveTextContent("Sending your answer...");

        resolve(nextAnswer(1));
        expect(await screen.findByText("Question 2 of 10")).toBeInTheDocument();
    });

    it("sends one answer for a double click", async () => {
        let resolve!: (value: Awaited<ReturnType<typeof answerWhoSaidItMatch>>) => void;

        answerMock.mockReturnValue(new Promise((res) => (resolve = res)));

        const user = userEvent.setup();

        await startMatch(user);

        const option = screen.getByRole("button", { name: "Alpha 1" });

        await user.dblClick(option);
        expect(answerMock).toHaveBeenCalledTimes(1);

        resolve(nextAnswer(1));
        await screen.findByText("Question 2 of 10");
    });

    it("after a temporary failure lets the player send the answer again", async () => {
        answerMock
            .mockResolvedValueOnce({ ok: false, kind: "temporary", message: "Something went wrong. Please try again." })
            .mockResolvedValueOnce(nextAnswer(1));

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Something went wrong");
        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Alpha 1" })).toBeEnabled();

        await user.click(screen.getByRole("button", { name: "Alpha 1" }));
        expect(await screen.findByText("Question 2 of 10")).toBeInTheDocument();
        expect(answerMock).toHaveBeenCalledTimes(2);
    });

    it("resynchronizes with the server after a 409", async () => {
        answerMock.mockResolvedValue({ ok: false, kind: "stale", message: "Your match was out of step." });
        stateMock.mockResolvedValue({ ok: true, question: question(4) });

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByText("Question 4 of 10")).toBeInTheDocument();
        expect(screen.getByRole("status")).toHaveTextContent("out of step");
    });

    it("shows the result when the server says the match is already over", async () => {
        answerMock.mockResolvedValue({ ok: false, kind: "stale", message: "Out of step." });
        stateMock.mockResolvedValue({ ok: true, result });

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByRole("heading", { name: "Match complete" })).toBeInTheDocument();
    });

    it("ends with a clear message when the match expired", async () => {
        answerMock.mockResolvedValue({ ok: false, kind: "terminal", message: "This match has expired or is no longer available." });

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("expired");
        expect(screen.getByRole("button", { name: "Start a new match" })).toBeInTheDocument();
    });

    it("shows the final result with score, percentage and the quote-by-quote summary", async () => {
        answerMock.mockResolvedValue(finalAnswer);

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByRole("heading", { name: "Match complete" })).toBeInTheDocument();
        expect(screen.getByText("70")).toBeInTheDocument();
        expect(screen.getByText(/\/ 100 points/)).toBeInTheDocument();
        expect(screen.getByText("70.00%")).toBeInTheDocument();
        expect(screen.getByText("7 of 10")).toBeInTheDocument();
        expect(screen.getByText(/You chose Beta 2\. It was Gamma 2\./)).toBeInTheDocument();
        expect(screen.getByText("Missed")).toBeInTheDocument();
    });

    it("offers publishing on the result and another match", async () => {
        answerMock.mockResolvedValue(finalAnswer);

        const user = userEvent.setup();

        await startMatch(user);
        await user.click(screen.getByRole("button", { name: "Alpha 1" }));

        expect(await screen.findByRole("button", { name: "Publish to leaderboard" })).toBeInTheDocument();

        startMock.mockResolvedValue({ ok: true, question: { ...question(1), matchId: "other-match" } });
        await user.click(screen.getByRole("button", { name: "Play another match" }));

        expect(await screen.findByText("Question 1 of 10")).toBeInTheDocument();
    });

    it("shows a start failure on the first screen and keeps both modes available", async () => {
        startMock.mockResolvedValue({ ok: false, message: "Wunderlore is temporarily unavailable. Please try again." });

        const user = userEvent.setup();

        render(<WhoSaidItGame />);
        await user.click(screen.getByRole("button", { name: "Start ranked match" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("temporarily unavailable");
        expect(screen.getByRole("button", { name: "Start ranked match" })).toBeEnabled();
    });
});
