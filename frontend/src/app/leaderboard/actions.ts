"use server";

import { updateTag } from "next/cache";
import { ApiError } from "@/lib/api/client";
import { LEADERBOARD_CACHE_TAG, postRankingEntry, type PublishedEntry } from "@/lib/api/ranking";
import { formatWait } from "@/lib/api/unavailable";
import { isRankingGame } from "@/lib/ranking/games";
import { normalizeNickname, validateNickname } from "@/lib/ranking/nickname";

// Every game id is a UUID issued by the backend registries.
const GAME_ID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

// - invalid: the nickname or the request was refused; fix the nickname and send again.
// - terminal: the game can no longer be published (expired, or not finished); do not retry.
// - temporary: the leaderboard could not be reached; the game is still there, so retry later.
export type PublishFailureKind = "invalid" | "terminal" | "temporary";

export type PublishResponse =
    | { ok: true; entry: PublishedEntry }
    | { ok: false; kind: PublishFailureKind; message: string };

function failure(kind: PublishFailureKind, message: string): PublishResponse {
    return { ok: false, kind, message };
}

// Called only after the backend confirmed the publish (a new entry or an already published game),
// so the leaderboard the player opens next is read fresh and includes the result. A failure here
// must not turn a confirmed publish into an error: the cache then just expires on its own.
function expireLeaderboardCache(): void {
    try {
        updateTag(LEADERBOARD_CACHE_TAG);
    } catch (error: unknown) {
        console.error("Failed to expire the leaderboard cache", error);
    }
}

// A Server Action is a public endpoint: its arguments come from the client and are checked here
// before anything reaches the backend. The backend checks them again and is the authority.
export async function publishToLeaderboard(
    gameType: unknown,
    gameId: unknown,
    nickname: unknown,
): Promise<PublishResponse> {
    if (!isRankingGame(gameType) || typeof gameId !== "string" || !GAME_ID_PATTERN.test(gameId)) {
        return failure("terminal", "This result can't be published.");
    }

    if (typeof nickname !== "string") {
        return failure("invalid", "Enter a nickname.");
    }

    const invalid = validateNickname(nickname);

    if (invalid) {
        return failure("invalid", invalid);
    }

    try {
        const entry = await postRankingEntry(gameType, gameId, normalizeNickname(nickname));

        expireLeaderboardCache();

        return { ok: true, entry };
    } catch (error: unknown) {
        if (error instanceof ApiError) {
            if (error.status === 400) {
                return failure("invalid", "That nickname isn't allowed. Please choose another one.");
            }

            if (error.status === 404) {
                return failure(
                    "terminal",
                    "This game has expired, so its result can no longer be published.",
                );
            }

            if (error.status === 409) {
                return failure("terminal", "Finish the game before publishing its result.");
            }

            if (error.status === 429) {
                const wait = error.retryAfterSeconds;

                return failure(
                    "temporary",
                    wait
                        ? `Too many results were published in a short time. Please try again in about ${formatWait(wait)}.`
                        : "Too many results were published in a short time. Please try again in a moment.",
                );
            }
        }

        // A 503 is expected while the leaderboard is not configured or recovers; the rest is not.
        if (!(error instanceof ApiError && error.status === 503)) {
            console.error("Failed to publish result to the leaderboard", error);
        }

        return failure(
            "temporary",
            "Your result was not published: the leaderboard is unavailable right now. Please try again.",
        );
    }
}
