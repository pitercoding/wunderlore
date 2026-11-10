import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WhichCharacterQuestion } from "@/lib/api/whichCharacter";
import { submitWhichCharacterAnswers } from "./actions";
import WhichCharacterContent from "./WhichCharacterContent";

vi.mock("./actions", () => ({
    submitWhichCharacterAnswers: vi.fn(),
}));

const submitMock = vi.mocked(submitWhichCharacterAnswers);

const questions: WhichCharacterQuestion[] = Array.from({ length: 10 }, (_, q) => ({
    id: `q${q + 1}`,
    text: `Question text ${q + 1}`,
    options: Array.from({ length: 4 }, (_, a) => ({
        id: `q${q + 1}-a${a + 1}`,
        text: `Option ${q + 1}.${a + 1}`,
    })),
}));

const character = {
    id: "gandalf",
    name: "Gandalf",
    description: "A wise wanderer who guides others.",
};

// Picks the nth (1-based) alternative of the visible question.
async function choose(user: ReturnType<typeof userEvent.setup>, n: number) {
    await user.click(screen.getAllByRole("radio")[n - 1]);
}

async function answerAll(user: ReturnType<typeof userEvent.setup>) {
    for (let i = 0; i < 9; i++) {
        await choose(user, 2);
        await user.click(screen.getByRole("button", { name: "Next" }));
    }
    await choose(user, 2);
}

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((res) => {
        resolve = res;
    });
    return { promise, resolve };
}

const expectedAnswers = questions.map((q) => ({
    questionId: q.id,
    answerId: `${q.id}-a2`,
}));

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhichCharacterContent", () => {
    it("renders the first question with four alternatives", () => {
        render(<WhichCharacterContent questions={questions} />);

        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();
        expect(screen.getByRole("progressbar")).toHaveAttribute("value", "1");
        expect(screen.getByText("Question text 1")).toBeInTheDocument();
        expect(screen.getAllByRole("radio")).toHaveLength(4);
        expect(screen.getByRole("radio", { name: "Option 1.3" })).not.toBeChecked();
        expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
    });

    it("marks the selected alternative and lets the user change it", async () => {
        const user = userEvent.setup();
        render(<WhichCharacterContent questions={questions} />);

        await user.click(screen.getByRole("radio", { name: "Option 1.2" }));
        expect(screen.getByRole("radio", { name: "Option 1.2" })).toBeChecked();

        await user.click(screen.getByRole("radio", { name: "Option 1.4" }));
        expect(screen.getByRole("radio", { name: "Option 1.4" })).toBeChecked();
        expect(screen.getByRole("radio", { name: "Option 1.2" })).not.toBeChecked();
    });

    it("does not allow moving on without an answer", async () => {
        const user = userEvent.setup();
        render(<WhichCharacterContent questions={questions} />);

        const next = screen.getByRole("button", { name: "Next" });
        expect(next).toBeDisabled();

        await user.click(next);
        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();

        await choose(user, 1);
        expect(next).toBeEnabled();
    });

    it("advances to the next question", async () => {
        const user = userEvent.setup();
        render(<WhichCharacterContent questions={questions} />);

        await choose(user, 1);
        await user.click(screen.getByRole("button", { name: "Next" }));

        expect(screen.getByText("Question 2 of 10")).toBeInTheDocument();
        expect(screen.getByText("Question text 2")).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Previous" })).toBeEnabled();
    });

    it("keeps the answer when going back to a previous question", async () => {
        const user = userEvent.setup();
        render(<WhichCharacterContent questions={questions} />);

        await choose(user, 3);
        await user.click(screen.getByRole("button", { name: "Next" }));
        await user.click(screen.getByRole("button", { name: "Previous" }));

        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();
        expect(screen.getByRole("radio", { name: "Option 1.3" })).toBeChecked();
    });

    it("offers See result instead of Next on the last question, enabled only once answered", async () => {
        const user = userEvent.setup();
        render(<WhichCharacterContent questions={questions} />);

        for (let i = 0; i < 9; i++) {
            await choose(user, 2);
            await user.click(screen.getByRole("button", { name: "Next" }));
        }

        expect(screen.getByText("Question 10 of 10")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Next" })).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "See result" })).toBeDisabled();

        await choose(user, 2);
        expect(screen.getByRole("button", { name: "See result" })).toBeEnabled();
    });

    it("submits exactly the ten questionId/answerId pairs and shows the backend character", async () => {
        const user = userEvent.setup();
        submitMock.mockResolvedValue({ ok: true, character });
        render(<WhichCharacterContent questions={questions} />);

        await answerAll(user);
        await user.click(screen.getByRole("button", { name: "See result" }));

        expect(submitMock).toHaveBeenCalledTimes(1);
        expect(submitMock).toHaveBeenCalledWith(expectedAnswers);
        for (const answer of submitMock.mock.calls[0][0]) {
            expect(Object.keys(answer).sort()).toEqual(["answerId", "questionId"]);
        }

        expect(
            await screen.findByRole("heading", { level: 1, name: "Gandalf" }),
        ).toBeInTheDocument();
        expect(screen.getByText(character.description)).toBeInTheDocument();
        expect(screen.getByText("Quiz complete")).toBeInTheDocument();
        expect(screen.queryByRole("radio")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Play again" })).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Back to games" })).toHaveAttribute(
            "href",
            "/#experiences",
        );
    });

    it("restarts the quiz from the first question with no answers", async () => {
        const user = userEvent.setup();
        submitMock.mockResolvedValue({ ok: true, character });
        render(<WhichCharacterContent questions={questions} />);

        await answerAll(user);
        await user.click(screen.getByRole("button", { name: "See result" }));
        await user.click(await screen.findByRole("button", { name: "Play again" }));

        expect(screen.getByText("Question 1 of 10")).toBeInTheDocument();
        for (const radio of screen.getAllByRole("radio")) {
            expect(radio).not.toBeChecked();
        }
        expect(screen.getByRole("button", { name: "Next" })).toBeDisabled();
        expect(screen.queryByText("Quiz complete")).not.toBeInTheDocument();
    });

    it("keeps every answer after a failed submission and allows retrying", async () => {
        const user = userEvent.setup();
        submitMock.mockResolvedValueOnce({
            ok: false,
            kind: "temporary",
            message: "Wunderlore is temporarily unavailable. Please try again.",
        });
        submitMock.mockResolvedValueOnce({ ok: true, character });
        render(<WhichCharacterContent questions={questions} />);

        await answerAll(user);
        await user.click(screen.getByRole("button", { name: "See result" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Wunderlore is temporarily unavailable. Please try again.",
        );
        expect(screen.getByRole("radio", { name: "Option 10.2" })).toBeChecked();

        // Earlier answers survived too.
        await user.click(screen.getByRole("button", { name: "Previous" }));
        expect(screen.getByRole("radio", { name: "Option 9.2" })).toBeChecked();
        await user.click(screen.getByRole("button", { name: "Next" }));

        await user.click(screen.getByRole("button", { name: "See result" }));

        expect(
            await screen.findByRole("heading", { level: 1, name: "Gandalf" }),
        ).toBeInTheDocument();
        expect(submitMock).toHaveBeenCalledTimes(2);
        expect(submitMock).toHaveBeenLastCalledWith(expectedAnswers);
    });

    it("shows a safe message when the action itself throws", async () => {
        const user = userEvent.setup();
        submitMock.mockRejectedValue(new Error("secret internal detail"));
        render(<WhichCharacterContent questions={questions} />);

        await answerAll(user);
        await user.click(screen.getByRole("button", { name: "See result" }));

        const alert = await screen.findByRole("alert");
        expect(alert).toHaveTextContent("Something went wrong. Please try again.");
        expect(alert).not.toHaveTextContent("secret");
        expect(screen.getByRole("radio", { name: "Option 10.2" })).toBeChecked();
    });

    it("blocks duplicate submissions and edits while a request is in progress", async () => {
        const user = userEvent.setup();
        const pending = deferred<Awaited<ReturnType<typeof submitWhichCharacterAnswers>>>();
        submitMock.mockReturnValue(pending.promise);
        render(<WhichCharacterContent questions={questions} />);

        await answerAll(user);
        const finish = screen.getByRole("button", { name: "See result" });
        await user.dblClick(finish);

        expect(submitMock).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("status")).toHaveTextContent("Consulting the chronicles...");
        expect(screen.getByRole("button", { name: "Submitting..." })).toBeDisabled();
        expect(screen.getByRole("button", { name: "Previous" })).toBeDisabled();
        for (const radio of screen.getAllByRole("radio")) {
            expect(radio).toBeDisabled();
        }

        pending.resolve({ ok: true, character });

        await waitFor(() =>
            expect(screen.getByRole("heading", { level: 1, name: "Gandalf" })).toBeInTheDocument(),
        );
        expect(submitMock).toHaveBeenCalledTimes(1);
    });
});
