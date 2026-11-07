import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { postRankingEntry } from "@/lib/api/ranking";
import { publishToLeaderboard } from "./actions";

vi.mock("@/lib/api/ranking", () => ({ postRankingEntry: vi.fn() }));

const postMock = vi.mocked(postRankingEntry);

const GAME_ID = "3f2b8c1e-5a47-4d0e-9c36-7b1e2a9d4f60";

const entry = {
    gameType: "who-said-it" as const,
    nickname: "Frodo",
    percentage: 70,
    playedAt: "2026-03-01T10:15:30Z",
    alreadyPublished: false,
};

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("publishToLeaderboard", () => {
    it("publishes with the normalized nickname", async () => {
        postMock.mockResolvedValue(entry);

        await expect(publishToLeaderboard("who-said-it", GAME_ID, "  Frodo  ")).resolves.toEqual({
            ok: true,
            entry,
        });
        expect(postMock).toHaveBeenCalledWith("who-said-it", GAME_ID, "Frodo");
    });

    it.each([
        ["an unknown game", "which-character", GAME_ID, "Frodo", "terminal"],
        ["a malformed game id", "who-said-it", "not-a-uuid", "Frodo", "terminal"],
        ["a non-string game id", "who-said-it", 42, "Frodo", "terminal"],
        ["a non-string nickname", "who-said-it", GAME_ID, 42, "invalid"],
        ["a short nickname", "who-said-it", GAME_ID, "ab", "invalid"],
        ["a reserved nickname", "who-said-it", GAME_ID, "Admin", "invalid"],
    ])("refuses %s without calling the backend", async (_name, game, id, nickname, kind) => {
        const response = await publishToLeaderboard(game, id, nickname);

        expect(response).toMatchObject({ ok: false, kind });
        expect(postMock).not.toHaveBeenCalled();
    });

    it.each([
        [400, "invalid"],
        [404, "terminal"],
        [409, "terminal"],
        [429, "temporary"],
        [503, "temporary"],
    ])("maps a %i to %s", async (status, kind) => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        postMock.mockRejectedValue(new ApiError("x", status));

        expect(await publishToLeaderboard("who-said-it", GAME_ID, "Frodo")).toMatchObject({
            ok: false,
            kind,
        });
    });

    it("names the wait on a 429 with Retry-After", async () => {
        postMock.mockRejectedValue(new ApiError("x", 429, 30));

        expect(await publishToLeaderboard("who-said-it", GAME_ID, "Frodo")).toMatchObject({
            message: expect.stringContaining("30 seconds"),
        });
    });

    it("never claims publication when the network fails", async () => {
        const log = vi.spyOn(console, "error").mockImplementation(() => {});

        postMock.mockRejectedValue(new TypeError("fetch failed"));

        const response = await publishToLeaderboard("who-said-it", GAME_ID, "Frodo");

        expect(response).toMatchObject({ ok: false, kind: "temporary" });
        expect(log).toHaveBeenCalled();
    });

    it("does not log an expected 503", async () => {
        const log = vi.spyOn(console, "error").mockImplementation(() => {});

        postMock.mockRejectedValue(new ApiError("x", 503));
        await publishToLeaderboard("who-said-it", GAME_ID, "Frodo");

        expect(log).not.toHaveBeenCalled();
    });
});
