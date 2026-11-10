import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { publishToLeaderboard } from "@/app/leaderboard/actions";
import LoreChallengeSummary from "@/app/games/lore-challenge/LoreChallengeSummary";
import WhereInMiddleEarthResult from "@/app/games/where-in-middle-earth/WhereInMiddleEarthResult";
import WhoAmIResult from "@/app/games/who-am-i/WhoAmIResult";
import type { LoreChallengeResult } from "@/lib/api/loreChallenge";
import type { WhereInMiddleEarthResult as WhereResult } from "@/lib/api/whereInMiddleEarth";
import type { WhoAmIResult as WhoAmIResultData } from "@/lib/api/whoAmI";

// The Who Said It? result screen is covered with its match flow in WhoSaidItMatch.test.tsx.
vi.mock("@/app/leaderboard/actions", () => ({ publishToLeaderboard: vi.fn() }));

const publishMock = vi.mocked(publishToLeaderboard);

beforeEach(() => {
    window.localStorage.clear();
});

afterEach(() => {
    vi.resetAllMocks();
});

const noop = () => {};

const lore: LoreChallengeResult = {
    matchId: "lore-match-id",
    category: "ALL",
    difficulty: "EASY",
    totalQuestions: 10,
    correctAnswers: 8,
    incorrectAnswers: 2,
    score: 80,
    maxScore: 100,
    percentage: 80,
    durationSeconds: 90,
    rank: "Loremaster",
};

const whoAmI: WhoAmIResultData = {
    gameId: "who-am-i-game-id",
    difficulty: "EASY",
    totalRounds: 10,
    score: 500,
    maxScore: 1000,
    percentage: 50,
    identified: 5,
    incorrectAnswers: 3,
    unidentified: 5,
    rounds: [],
};

const where: WhereResult = {
    gameId: "where-game-id",
    difficulty: "EASY",
    totalRounds: 10,
    score: 400,
    maxScore: 1000,
    percentage: 40,
    closeHits: 2,
    averageDistance: 12,
    rounds: [],
};

const screens = [
    {
        name: "Lore Challenge",
        game: "lore-challenge",
        id: "lore-match-id",
        ui: () => (
            <LoreChallengeSummary
                result={lore}
                onPlayAgain={noop}
                onChangeSelection={noop}
                isStarting={false}
                error={null}
            />
        ),
    },
    {
        name: "Who Am I?",
        game: "who-am-i",
        id: "who-am-i-game-id",
        ui: () => (
            <WhoAmIResult
                result={whoAmI}
                onPlayAgain={noop}
                onChangeDifficulty={noop}
                isStarting={false}
                error={null}
            />
        ),
    },
    {
        name: "Where in Middle-earth",
        game: "where-in-middle-earth",
        id: "where-game-id",
        ui: () => (
            <WhereInMiddleEarthResult
                result={where}
                onPlayAgain={noop}
                onChangeDifficulty={noop}
                isStarting={false}
                error={null}
            />
        ),
    },
] as const;

describe.each(screens)("$name result screen", ({ game, id, ui }) => {
    it("offers to publish the result, leaving play-again navigation in place", () => {
        render(ui());

        expect(screen.getByRole("button", { name: "Publish to leaderboard" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: /play again/i })).toBeEnabled();
        expect(screen.getByRole("link", { name: /back to (the )?games/i })).toBeInTheDocument();
    });

    it("publishes this game's id under its own game type and reports success", async () => {
        publishMock.mockResolvedValue({
            ok: true,
            entry: {
                gameType: game,
                nickname: "Frodo",
                percentage: 50,
                playedAt: "2026-03-01T10:15:30Z",
                alreadyPublished: false,
            },
        });

        const user = userEvent.setup();

        render(ui());
        await user.click(screen.getByRole("button", { name: "Publish to leaderboard" }));
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(publishMock).toHaveBeenCalledWith(game, id, "Frodo");
        expect(await screen.findByText(/Published in .* · Easy as Frodo/)).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /leaderboard/i })).toHaveAttribute(
            "href",
            `/leaderboard?game=${game}&division=easy`,
        );
    });

    it("keeps the result and its buttons when publishing fails", async () => {
        publishMock.mockResolvedValue({
            ok: false,
            kind: "temporary",
            message: "Your result was not published: the leaderboard is unavailable right now. Please try again.",
        });

        const user = userEvent.setup();

        render(ui());
        await user.click(screen.getByRole("button", { name: "Publish to leaderboard" }));
        await user.type(screen.getByLabelText("Nickname"), "Frodo");
        await user.click(screen.getByRole("button", { name: "Publish" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("was not published");
        expect(screen.getByRole("button", { name: /play again/i })).toBeEnabled();
    });
});
