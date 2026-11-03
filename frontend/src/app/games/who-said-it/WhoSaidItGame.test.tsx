import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { WhoSaidItRound } from "@/lib/api/whoSaidIt";
import { createWhoSaidItRound, submitWhoSaidItAnswer } from "./actions";
import WhoSaidItGame from "./WhoSaidItGame";

const refresh = vi.fn();

vi.mock("next/navigation", () => ({
    useRouter: () => ({ refresh }),
}));

vi.mock("./actions", () => ({
    createWhoSaidItRound: vi.fn(),
    submitWhoSaidItAnswer: vi.fn(),
}));

const createMock = vi.mocked(createWhoSaidItRound);
const answerMock = vi.mocked(submitWhoSaidItAnswer);

const firstRound: WhoSaidItRound = {
    roundId: "round-1",
    quote: "One does not simply walk into Mordor.",
    options: [
        { id: "c1", name: "Boromir" },
        { id: "c2", name: "Aragorn" },
    ],
};

const secondRound: WhoSaidItRound = {
    roundId: "round-2",
    quote: "Fly, you fools!",
    options: [
        { id: "c3", name: "Gandalf" },
        { id: "c4", name: "Gimli" },
    ],
};

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";

type CreateResponse = Awaited<ReturnType<typeof createWhoSaidItRound>>;

function deferred<T>() {
    let resolve!: (value: T) => void;
    const promise = new Promise<T>((res) => {
        resolve = res;
    });
    return { promise, resolve };
}

function play() {
    return screen.getByRole("button", { name: "Play" });
}

function quote(round: WhoSaidItRound) {
    return `“${round.quote}”`;
}

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhoSaidItGame", () => {
    it("starts idle: an introduction and Play, with no round and no backend request", () => {
        render(<WhoSaidItGame />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Who Said It?" }),
        ).toBeInTheDocument();
        expect(play()).toBeEnabled();
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute(
            "href",
            "/",
        );
        expect(screen.queryByText(quote(firstRound))).not.toBeInTheDocument();
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(createMock).not.toHaveBeenCalled();
    });

    it("creates exactly one round when Play is clicked and shows it", async () => {
        const user = userEvent.setup();
        createMock.mockResolvedValue({ ok: true, round: firstRound });
        render(<WhoSaidItGame />);

        await user.click(play());

        expect(createMock).toHaveBeenCalledTimes(1);
        expect(await screen.findByText(quote(firstRound))).toBeInTheDocument();
        expect(
            screen.getByRole("heading", { level: 1, name: "Who said this quote?" }),
        ).toBeInTheDocument();
        for (const option of firstRound.options) {
            expect(screen.getByRole("button", { name: option.name })).toBeEnabled();
        }
        expect(screen.queryByRole("button", { name: "Play" })).not.toBeInTheDocument();
        // The Play button is gone, so keyboard focus moves to the quote.
        expect(screen.getByText(quote(firstRound))).toHaveFocus();
    });

    it("announces the creating state and disables Play meanwhile", async () => {
        const user = userEvent.setup();
        const pending = deferred<CreateResponse>();
        createMock.mockReturnValue(pending.promise);
        render(<WhoSaidItGame />);

        await user.click(play());

        expect(screen.getByRole("status")).toHaveTextContent("Preparing your round...");
        expect(screen.getByRole("button", { name: "Loading..." })).toBeDisabled();
        expect(screen.getByRole("status").closest("section")).toHaveAttribute(
            "aria-busy",
            "true",
        );

        await act(async () => pending.resolve({ ok: true, round: firstRound }));

        expect(await screen.findByText(quote(firstRound))).toBeInTheDocument();
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
    });

    it("does not create a second round on duplicate clicks", async () => {
        const pending = deferred<CreateResponse>();
        createMock.mockReturnValue(pending.promise);
        render(<WhoSaidItGame />);
        const button = play();

        // Both clicks land before React re-renders the button as disabled.
        act(() => {
            button.click();
            button.click();
        });

        expect(createMock).toHaveBeenCalledTimes(1);

        await act(async () => pending.resolve({ ok: true, round: firstRound }));

        expect(createMock).toHaveBeenCalledTimes(1);
    });

    it("shows an alert and a Try again button when creation fails, then recovers", async () => {
        const user = userEvent.setup();
        createMock
            .mockResolvedValueOnce({ ok: false, message: UNAVAILABLE })
            .mockResolvedValueOnce({ ok: true, round: firstRound });
        render(<WhoSaidItGame />);

        await user.click(play());

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
        expect(screen.queryByRole("status")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByText(quote(firstRound))).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(createMock).toHaveBeenCalledTimes(2);
    });

    it("shows a generic alert when the action call itself fails", async () => {
        const user = userEvent.setup();
        createMock.mockRejectedValue(new Error("network down"));
        render(<WhoSaidItGame />);

        await user.click(play());

        expect(await screen.findByRole("alert")).toHaveTextContent(
            "Something went wrong. Please try again.",
        );
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
    });

    it("plays another round without refreshing the route and resets the answer", async () => {
        const user = userEvent.setup();
        createMock
            .mockResolvedValueOnce({ ok: true, round: firstRound })
            .mockResolvedValueOnce({ ok: true, round: secondRound });
        answerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c2",
                correct: false,
                correctCharacterId: "c1",
            },
        });
        render(<WhoSaidItGame />);

        await user.click(play());
        await user.click(await screen.findByRole("button", { name: "Aragorn" }));
        expect(
            await screen.findByRole("heading", { name: "Not quite!" }),
        ).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Play another round" }));

        expect(await screen.findByText(quote(secondRound))).toBeInTheDocument();
        expect(createMock).toHaveBeenCalledTimes(2);
        expect(refresh).not.toHaveBeenCalled();
        expect(
            screen.queryByRole("heading", { name: "Not quite!" }),
        ).not.toBeInTheDocument();
        expect(screen.queryByText(quote(firstRound))).not.toBeInTheDocument();
        for (const option of secondRound.options) {
            expect(screen.getByRole("button", { name: option.name })).toBeEnabled();
        }
        expect(screen.getByText(quote(secondRound))).toHaveFocus();
    });

    it("keeps the previous result visible when the next round cannot be created", async () => {
        const user = userEvent.setup();
        createMock
            .mockResolvedValueOnce({ ok: true, round: firstRound })
            .mockResolvedValueOnce({ ok: false, message: UNAVAILABLE })
            .mockResolvedValueOnce({ ok: true, round: secondRound });
        answerMock.mockResolvedValue({
            ok: true,
            result: {
                roundId: "round-1",
                submittedCharacterId: "c1",
                correct: true,
                correctCharacterId: "c1",
            },
        });
        render(<WhoSaidItGame />);

        await user.click(play());
        await user.click(await screen.findByRole("button", { name: "Boromir" }));
        await screen.findByRole("heading", { name: "Correct!" });

        await user.click(screen.getByRole("button", { name: "Play another round" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(UNAVAILABLE);
        expect(screen.getByRole("heading", { name: "Correct!" })).toBeInTheDocument();
        expect(screen.getByText(quote(firstRound))).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByText(quote(secondRound))).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(refresh).not.toHaveBeenCalled();
    });

    it("keeps the same scene mounted from idle to playing", async () => {
        const user = userEvent.setup();
        createMock.mockResolvedValue({ ok: true, round: firstRound });
        const { container } = render(<WhoSaidItGame />);
        const sceneImage = container.querySelector("img");

        expect(sceneImage).not.toBeNull();

        await user.click(play());
        await screen.findByText(quote(firstRound));

        expect(container.querySelector("img")).toBe(sceneImage);
    });

    it("returns to idle without creating a round when the page is reloaded", async () => {
        const user = userEvent.setup();
        createMock.mockResolvedValue({ ok: true, round: firstRound });
        const first = render(<WhoSaidItGame />);

        await user.click(play());
        await screen.findByText(quote(firstRound));
        first.unmount();

        // A reload mounts the page again from scratch.
        render(<WhoSaidItGame />);

        expect(play()).toBeEnabled();
        expect(screen.queryByText(quote(firstRound))).not.toBeInTheDocument();
        expect(createMock).toHaveBeenCalledTimes(1);
    });
});
