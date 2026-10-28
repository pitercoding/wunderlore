import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { GameRound } from "@/lib/api/game";
import { submitGameAnswer } from "./actions";
import GameContent from "./GameContent";

const refresh = vi.fn();

vi.mock("next/navigation", () => ({
    useRouter: () => ({ refresh }),
}));

vi.mock("./actions", () => ({
    submitGameAnswer: vi.fn(),
}));

const submitGameAnswerMock = vi.mocked(submitGameAnswer);

const round: GameRound = {
    roundId: "round-1",
    quote: "One does not simply walk into Mordor.",
    options: [
        { id: "c1", name: "Boromir" },
        { id: "c2", name: "Aragorn" },
        { id: "c3", name: "Gimli" },
    ],
};

function answerButtons() {
    return round.options.map((option) =>
        screen.getByRole("button", { name: option.name }),
    );
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}

afterEach(() => {
    vi.resetAllMocks();
});

describe("GameContent", () => {
    it("renders the quote, the options and a link back home", () => {
        render(<GameContent round={round} />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Who said this quote?" }),
        ).toBeInTheDocument();
        expect(screen.getByText(`“${round.quote}”`)).toBeInTheDocument();
        for (const button of answerButtons()) {
            expect(button).toBeEnabled();
        }
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute(
            "href",
            "/",
        );
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(
            screen.queryByRole("button", { name: "Play another round" }),
        ).not.toBeInTheDocument();
    });

    it("shows a pending state while the answer is being checked", async () => {
        const user = userEvent.setup();
        const pending = deferred<Awaited<ReturnType<typeof submitGameAnswer>>>();
        submitGameAnswerMock.mockReturnValue(pending.promise);
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(submitGameAnswerMock).toHaveBeenCalledWith("round-1", "c2");
        expect(screen.getByRole("status")).toHaveTextContent(
            "Checking your answer...",
        );
        // The game card is an unnamed <section>, so it has no "region" role.
        expect(
            screen.getByText(`“${round.quote}”`).closest("section"),
        ).toHaveAttribute("aria-busy", "true");
        for (const button of answerButtons()) {
            expect(button).toBeDisabled();
        }

        pending.resolve({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c2",
                correct: false,
                correctCharacterId: "c1",
            },
        });

        await waitFor(() =>
            expect(screen.queryByRole("status")).not.toBeInTheDocument(),
        );
    });

    it("announces a correct answer and locks the round", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Boromir" }));

        expect(
            await screen.findByRole("heading", { name: "Correct!" }),
        ).toBeInTheDocument();
        expect(screen.getByText(/The correct answer is/)).toHaveTextContent(
            "The correct answer is Boromir.",
        );
        for (const button of answerButtons()) {
            expect(button).toBeDisabled();
        }
        expect(
            screen.getByRole("button", { name: "Play another round" }),
        ).toBeEnabled();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("announces a wrong answer and reveals the correct character", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c3",
                correct: false,
                correctCharacterId: "c1",
            },
        });
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Gimli" }));

        expect(
            await screen.findByRole("heading", { name: "Not quite!" }),
        ).toBeInTheDocument();
        expect(screen.getByText(/The correct answer is/)).toHaveTextContent(
            "The correct answer is Boromir.",
        );
    });

    it("falls back to 'Unknown' when the correct character is not an option", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: false,
                correctCharacterId: "missing",
            },
        });
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Boromir" }));

        expect(await screen.findByText(/The correct answer is/)).toHaveTextContent(
            "The correct answer is Unknown.",
        );
    });

    it("submits only once even if options are clicked again", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Boromir" }));
        await screen.findByRole("heading", { name: "Correct!" });
        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(submitGameAnswerMock).toHaveBeenCalledTimes(1);
    });

    it("shows the action's error message and lets the player retry", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock
            .mockResolvedValueOnce({
                ok: false,
                message: "Invalid answer. Please try again.",
            })
            .mockResolvedValueOnce({
                ok: true,
                result: {
                    roundId: "round-1",
                    submittedCharacterId: "c2",
                    correct: false,
                    correctCharacterId: "c1",
                },
            });
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Invalid answer. Please try again.",
        );
        expect(
            screen.getByText("Start a new round to continue playing."),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Play another round" }),
        ).toBeEnabled();
        for (const button of answerButtons()) {
            expect(button).toBeEnabled();
        }

        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(
            await screen.findByRole("heading", { name: "Not quite!" }),
        ).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(submitGameAnswerMock).toHaveBeenCalledTimes(2);
    });

    it("shows a generic error when the action call itself fails", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock.mockRejectedValue(new Error("network down"));
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Gimli" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Something went wrong. Please try again.",
        );
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
        for (const button of answerButtons()) {
            expect(button).toBeEnabled();
        }
    });

    it("refreshes the route to load a new round", async () => {
        const user = userEvent.setup();
        submitGameAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<GameContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Boromir" }));
        await user.click(
            await screen.findByRole("button", { name: "Play another round" }),
        );

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();
        for (const button of answerButtons()) {
            expect(button).toBeDisabled();
        }
    });
});
