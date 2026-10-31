import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WhoSaidItRound } from "@/lib/api/whoSaidIt";
import { submitWhoSaidItAnswer } from "./actions";
import WhoSaidItContent from "./WhoSaidItContent";

const refresh = vi.fn();

vi.mock("next/navigation", () => ({
    useRouter: () => ({ refresh }),
}));

vi.mock("./actions", () => ({
    submitWhoSaidItAnswer: vi.fn(),
}));

const submitWhoSaidItAnswerMock = vi.mocked(submitWhoSaidItAnswer);

const round: WhoSaidItRound = {
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

describe("WhoSaidItContent", () => {
    it("renders the quote, the options and a link back home", () => {
        render(<WhoSaidItContent round={round} />);

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
        const pending = deferred<Awaited<ReturnType<typeof submitWhoSaidItAnswer>>>();
        submitWhoSaidItAnswerMock.mockReturnValue(pending.promise);
        render(<WhoSaidItContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(submitWhoSaidItAnswerMock).toHaveBeenCalledWith("round-1", "c2");
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
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<WhoSaidItContent round={round} />);

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
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c3",
                correct: false,
                correctCharacterId: "c1",
            },
        });
        render(<WhoSaidItContent round={round} />);

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
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: false,
                correctCharacterId: "missing",
            },
        });
        render(<WhoSaidItContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Boromir" }));

        expect(await screen.findByText(/The correct answer is/)).toHaveTextContent(
            "The correct answer is Unknown.",
        );
    });

    it("submits only once even if options are clicked again", async () => {
        const user = userEvent.setup();
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<WhoSaidItContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Boromir" }));
        await screen.findByRole("heading", { name: "Correct!" });
        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(submitWhoSaidItAnswerMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["invalid", "That isn't a valid option for this round."],
        ["temporary", "Wunderlore is temporarily unavailable. Please try again."],
    ] as const)("keeps the round open after a %s failure", async (kind, message) => {
        const user = userEvent.setup();
        submitWhoSaidItAnswerMock
            .mockResolvedValueOnce({ ok: false, kind, message })
            .mockResolvedValueOnce({
                ok: true,
                result: {
                    roundId: "round-1",
                    submittedCharacterId: "c2",
                    correct: false,
                    correctCharacterId: "c1",
                },
            });
        render(<WhoSaidItContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(message);
        expect(
            screen.getByText("Choose an option again, or start a new round."),
        ).toBeInTheDocument();
        expect(
            screen.queryByText("Start a new round to continue playing."),
        ).not.toBeInTheDocument();
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
        expect(submitWhoSaidItAnswerMock).toHaveBeenCalledTimes(2);
    });

    it("shows a generic error when the action call itself fails", async () => {
        const user = userEvent.setup();
        submitWhoSaidItAnswerMock.mockRejectedValue(new Error("network down"));
        render(<WhoSaidItContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Gimli" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Something went wrong. Please try again.",
        );
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
        expect(
            screen.getByText("Choose an option again, or start a new round."),
        ).toBeInTheDocument();
        for (const button of answerButtons()) {
            expect(button).toBeEnabled();
        }
    });

    it.each([
        ["expired", "This round has expired or is no longer available."],
        ["already answered", "This round has already been answered."],
    ])("locks the options when the round is %s", async (_, message) => {
        const user = userEvent.setup();
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: false,
            kind: "terminal",
            message,
        });
        render(<WhoSaidItContent round={round} />);

        await user.click(screen.getByRole("button", { name: "Aragorn" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(message);
        expect(
            screen.getByText("Start a new round to continue playing."),
        ).toBeInTheDocument();
        expect(
            screen.queryByText("Choose an option again, or start a new round."),
        ).not.toBeInTheDocument();
        expect(screen.queryByText(/try again/i)).not.toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Play another round" }),
        ).toBeEnabled();
        for (const button of answerButtons()) {
            expect(button).toBeDisabled();
        }

        await user.click(screen.getByRole("button", { name: "Boromir" }));

        expect(submitWhoSaidItAnswerMock).toHaveBeenCalledTimes(1);
    });

    it("starts a playable new round from a terminal failure", async () => {
        const user = userEvent.setup();
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: false,
            kind: "terminal",
            message: "This round has expired or is no longer available.",
        });
        const { rerender } = render(<WhoSaidItContent key={round.roundId} round={round} />);

        await user.click(screen.getByRole("button", { name: "Aragorn" }));
        await user.click(
            await screen.findByRole("button", { name: "Play another round" }),
        );

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();

        // The page keys WhoSaidItContent by round, so the refreshed round remounts it.
        rerender(<WhoSaidItContent key="round-2" round={{ ...round, roundId: "round-2" }} />);

        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        for (const button of answerButtons()) {
            expect(button).toBeEnabled();
        }

        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-2",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        await user.click(screen.getByRole("button", { name: "Boromir" }));

        expect(submitWhoSaidItAnswerMock).toHaveBeenLastCalledWith("round-2", "c1");
        expect(
            await screen.findByRole("heading", { name: "Correct!" }),
        ).toBeInTheDocument();
    });

    it("refreshes the route to load a new round", async () => {
        const user = userEvent.setup();
        submitWhoSaidItAnswerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<WhoSaidItContent round={round} />);

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
