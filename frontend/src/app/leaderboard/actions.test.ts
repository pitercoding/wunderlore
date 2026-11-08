import { afterEach, describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/client";
import { LEADERBOARD_CACHE_TAG, postRankingEntry } from "@/lib/api/ranking";
import { updateTag } from "next/cache";
import { publishToLeaderboard } from "./actions";

// Only the network call is replaced: the cache tag stays the real one the read uses.
vi.mock("@/lib/api/ranking", async (importOriginal) => ({
    ...(await importOriginal<typeof import("@/lib/api/ranking")>()),
    postRankingEntry: vi.fn(),
}));
vi.mock("next/cache", () => ({ updateTag: vi.fn() }));

const postMock = vi.mocked(postRankingEntry);
const updateTagMock = vi.mocked(updateTag);

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

describe("publishToLeaderboard cache invalidation", () => {
    it("expires the leaderboard tag after a new publication", async () => {
        postMock.mockResolvedValue(entry);

        await publishToLeaderboard("who-said-it", GAME_ID, "Frodo");

        expect(updateTagMock).toHaveBeenCalledTimes(1);
        expect(updateTagMock).toHaveBeenCalledWith(LEADERBOARD_CACHE_TAG);
    });

    it("also expires it when the game had already been published", async () => {
        postMock.mockResolvedValue({ ...entry, alreadyPublished: true });

        await expect(publishToLeaderboard("who-said-it", GAME_ID, "Frodo")).resolves.toMatchObject({
            ok: true,
        });
        expect(updateTagMock).toHaveBeenCalledWith(LEADERBOARD_CACHE_TAG);
    });

    it("expires it only after the backend answered", async () => {
        const order: string[] = [];

        postMock.mockImplementation(async () => {
            order.push("backend");
            return entry;
        });
        updateTagMock.mockImplementation(() => {
            order.push("expire");
        });

        await publishToLeaderboard("who-said-it", GAME_ID, "Frodo");

        expect(order).toEqual(["backend", "expire"]);
    });

    it.each([400, 404, 409, 429, 503, 500])("does not expire it when the backend answers %i", async (status) => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        postMock.mockRejectedValue(new ApiError("x", status));

        await publishToLeaderboard("who-said-it", GAME_ID, "Frodo");

        expect(updateTagMock).not.toHaveBeenCalled();
    });

    it("does not expire it when the request fails without a response", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        postMock.mockRejectedValue(new Error("network"));

        expect(await publishToLeaderboard("who-said-it", GAME_ID, "Frodo")).toMatchObject({
            ok: false,
        });
        expect(updateTagMock).not.toHaveBeenCalled();
    });

    it.each([
        ["an invalid nickname", "who-said-it", GAME_ID, "ab"],
        ["a reserved nickname", "who-said-it", GAME_ID, "Admin"],
        ["a non-string nickname", "who-said-it", GAME_ID, 42],
        ["a malformed game id", "who-said-it", "not-a-uuid", "Frodo"],
    ])("does not expire it for %s", async (_name, game, id, nickname) => {
        await publishToLeaderboard(game, id, nickname);

        expect(postMock).not.toHaveBeenCalled();
        expect(updateTagMock).not.toHaveBeenCalled();
    });

    it("still reports a confirmed publish when expiring the cache throws", async () => {
        vi.spyOn(console, "error").mockImplementation(() => {});
        postMock.mockResolvedValue(entry);
        updateTagMock.mockImplementation(() => {
            throw new Error("not in a server action");
        });

        expect(await publishToLeaderboard("who-said-it", GAME_ID, "Frodo")).toEqual({
            ok: true,
            entry,
        });
    });
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
