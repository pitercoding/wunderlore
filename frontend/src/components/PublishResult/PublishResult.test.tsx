import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { publishToLeaderboard } from "@/app/leaderboard/actions";
import PublishResult from "./PublishResult";

vi.mock("@/app/leaderboard/actions", () => ({ publishToLeaderboard: vi.fn() }));

const publishMock = vi.mocked(publishToLeaderboard);

const GAME_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

const entry = {
    gameType: "who-said-it" as const,
    nickname: "Frodo",
    percentage: 70,
    playedAt: "2026-03-01T10:15:30Z",
    alreadyPublished: false,
};

beforeEach(() => {
    window.localStorage.clear();
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

async function openForm(user: ReturnType<typeof userEvent.setup>) {
    await user.click(screen.getByRole("button", { name: "Publish to leaderboard" }));
}

describe("PublishResult", () => {
    it("is optional: it only offers, and asks for nothing until clicked", () => {
        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);

        expect(screen.getByRole("heading", { name: "Leaderboard" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Publish to leaderboard" })).toBeInTheDocument();
        expect(screen.queryByLabelText("Nickname")).not.toBeInTheDocument();
        expect(publishMock).not.toHaveBeenCalled();
    });

    it("opens a labelled form with the rules and can be cancelled", async () => {
        const user = userEvent.setup();

        render(<PublishResult gameType="who-am-i" gameId={GAME_ID} division="hard" />);
        await openForm(user);

        const input = screen.getByLabelText("Nickname");

        expect(input).toHaveAccessibleDescription(/3 to 16 characters/);

        await user.click(screen.getByRole("button", { name: "Cancel" }));
        expect(screen.queryByLabelText("Nickname")).not.toBeInTheDocument();
    });

    it("suggests the nickname saved in localStorage and lets the player edit it", async () => {
        window.localStorage.setItem("wunderlore:nickname", "Bilbo");
        publishMock.mockResolvedValue({ ok: true, entry: { ...entry, nickname: "Bilbo B" } });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);

        const input = screen.getByLabelText("Nickname");

        expect(input).toHaveValue("Bilbo");

        await user.type(input, " B");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(publishMock).toHaveBeenCalledWith("who-said-it", GAME_ID, "Bilbo B");
    });

    it("starts empty when nothing was saved", async () => {
        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);

        expect(screen.getByLabelText("Nickname")).toHaveValue("");
    });

    it("works when localStorage throws", async () => {
        vi.spyOn(Storage.prototype, "getItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        vi.spyOn(Storage.prototype, "setItem").mockImplementation(() => {
            throw new Error("blocked");
        });
        publishMock.mockResolvedValue({ ok: true, entry });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("status")).toHaveTextContent("Published in Who Said It? · Standard as Frodo");
    });

    it("validates before sending and does not call the backend", async () => {
        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "ab");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(screen.getByRole("alert")).toHaveTextContent(/3 to 16 characters/);
        expect(screen.getByLabelText("Nickname")).toHaveAttribute("aria-invalid", "true");
        expect(publishMock).not.toHaveBeenCalled();
    });

    it("rejects a reserved nickname locally", async () => {
        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Admin");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(screen.getByRole("alert")).toHaveTextContent(/not available/);
        expect(publishMock).not.toHaveBeenCalled();
    });

    it("confirms a successful publication, saves the nickname and links to the game's ranking", async () => {
        publishMock.mockResolvedValue({ ok: true, entry });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("status")).toHaveTextContent(
            "Published in Who Said It? · Standard as Frodo with 70.00%.",
        );
        expect(window.localStorage.getItem("wunderlore:nickname")).toBe("Frodo");
        expect(screen.getByRole("link", { name: /Who Said It\? · Standard leaderboard/ })).toHaveAttribute(
            "href",
            "/leaderboard?game=who-said-it&division=standard",
        );
        expect(screen.queryByLabelText("Nickname")).not.toBeInTheDocument();
    });

    it("says so when the game was already published", async () => {
        publishMock.mockResolvedValue({ ok: true, entry: { ...entry, alreadyPublished: true } });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Other");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("status")).toHaveTextContent(
            "This result was already published in Who Said It? · Standard as Frodo.",
        );
    });

    it("never claims publication on failure and lets the player try again", async () => {
        publishMock
            .mockResolvedValueOnce({
                ok: false,
                kind: "temporary",
                message: "Your result was not published: the leaderboard is unavailable right now.",
            })
            .mockResolvedValueOnce({ ok: true, entry });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("was not published");
        expect(screen.queryByText(/Published in/)).not.toBeInTheDocument();
        expect(window.localStorage.getItem("wunderlore:nickname")).toBeNull();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(await screen.findByRole("status")).toHaveTextContent("Published in Who Said It? · Standard as Frodo");
        expect(publishMock).toHaveBeenCalledTimes(2);
    });

    it("shows a failure when the action itself throws", async () => {
        publishMock.mockRejectedValue(new Error("network"));

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("alert")).toHaveTextContent(/not published/);
    });

    it("shows a backend nickname refusal on the field and keeps the form", async () => {
        publishMock.mockResolvedValue({
            ok: false,
            kind: "invalid",
            message: "That nickname isn't allowed. Please choose another one.",
        });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("isn't allowed");
        expect(screen.getByLabelText("Nickname")).toBeEnabled();
    });

    it("offers no retry when the game can no longer be published", async () => {
        publishMock.mockResolvedValue({
            ok: false,
            kind: "terminal",
            message: "This game has expired, so its result can no longer be published.",
        });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("expired");
        expect(screen.queryByRole("button", { name: "Publish" })).not.toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Try again" })).not.toBeInTheDocument();
    });

    it("sends one request for a double submit and disables the form while publishing", async () => {
        let resolve!: (value: Awaited<ReturnType<typeof publishToLeaderboard>>) => void;

        publishMock.mockReturnValue(new Promise((res) => (resolve = res)));

        const user = userEvent.setup();

        render(<PublishResult gameType="who-said-it" gameId={GAME_ID} division="standard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo{Enter}");
        await user.keyboard("{Enter}");

        expect(publishMock).toHaveBeenCalledTimes(1);
        expect(screen.getByLabelText("Nickname")).toBeDisabled();
        expect(screen.getByRole("status")).toHaveTextContent("Publishing...");

        resolve({ ok: true, entry });
        expect(await screen.findByText(/Published in .* as Frodo/)).toBeInTheDocument();
    });

    it("names the game and division in the offer and never calls it global", () => {
        render(<PublishResult gameType="where-in-middle-earth" gameId={GAME_ID} division="medium" />);

        expect(screen.getByText(/Where in Middle-earth · Medium leaderboard/)).toBeInTheDocument();
        expect(screen.queryByText(/global/i)).not.toBeInTheDocument();
    });

    it("reports where a Who Am I? result was published and links to that division", async () => {
        publishMock.mockResolvedValue({ ok: true, entry: { ...entry, gameType: "who-am-i" } });

        const user = userEvent.setup();

        render(<PublishResult gameType="who-am-i" gameId={GAME_ID} division="hard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("status")).toHaveTextContent(
            "Published in Who Am I? · Hard as Frodo with 70.00%.",
        );
        expect(screen.getByRole("link", { name: /Who Am I\? · Hard leaderboard/ })).toHaveAttribute(
            "href",
            "/leaderboard?game=who-am-i&division=hard",
        );
        expect(screen.queryByText(/global/i)).not.toBeInTheDocument();
    });

    it("sends only the game, its id and the nickname: the division is never sent", async () => {
        publishMock.mockResolvedValue({ ok: true, entry });

        const user = userEvent.setup();

        render(<PublishResult gameType="lore-challenge" gameId={GAME_ID} division="hard" />);
        await openForm(user);
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(publishMock).toHaveBeenCalledTimes(1);
        expect(publishMock.mock.calls[0]).toEqual(["lore-challenge", GAME_ID, "Frodo"]);
    });
});
