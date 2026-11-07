"use server";

import { ApiError } from "@/lib/api/client";
import { formatWait, unavailableMessage } from "@/lib/api/unavailable";
import {
    createMatch,
    getMatchQuestion,
    getMatchResult,
    submitMatchAnswer,
    type WhoSaidItMatchAnswer,
    type WhoSaidItMatchQuestion,
    type WhoSaidItMatchResult,
} from "@/lib/api/whoSaidItMatch";

// Server Actions are public endpoints: their arguments are checked here before anything reaches
// the backend. Match ids are the UUIDs issued by WhoSaidItMatchRegistry; character ids are the
// upstream ids, which the backend limits to 64 characters.
const MATCH_ID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const MAX_CHARACTER_ID_LENGTH = 64;
const TOTAL_QUESTIONS = 10;

const INVALID_OPTION_MESSAGE = "That isn't a valid option for this question.";

function statusOf(error: unknown): number | undefined {
    return error instanceof ApiError ? error.status : undefined;
}

export type WhoSaidItMatchStartResponse =
    | { ok: true; question: WhoSaidItMatchQuestion }
    | { ok: false; message: string };

// The only way a match starts: the page itself never does, so rendering or reloading it creates
// no backend state.
export async function startWhoSaidItMatch(): Promise<WhoSaidItMatchStartResponse> {
    try {
        return { ok: true as const, question: await createMatch() };
    } catch (error: unknown) {
        if (error instanceof ApiError && error.status === 429) {
            const wait = error.retryAfterSeconds;

            return {
                ok: false as const,
                message: wait
                    ? `Too many matches were started in a short time. Please try again in about ${formatWait(wait)}.`
                    : "Too many matches were started in a short time. Please try again in a moment.",
            };
        }

        // A 503 is expected while the service recovers, so only the other failures are logged.
        if (statusOf(error) !== 503) {
            console.error("Failed to create Who Said It match", error);
        }

        return {
            ok: false as const,
            message: unavailableMessage(error) ?? "Unable to start a match. Please try again.",
        };
    }
}

// Follows WhoSaidItMatch.answer:
// - stale (409): the match is not where the client thinks it is (a changed answer, a lost
//   response, a second tab). The client asks the server where it is.
// - terminal (404): the match expired or is unknown; it can no longer be played.
// - invalid (400): rejected before it counted.
// - temporary: nothing is known to be lost. Sending the same answer again is safe: the backend
//   accepts a repeat of a recorded answer without counting it twice.
export type WhoSaidItMatchAnswerFailureKind = "stale" | "terminal" | "invalid" | "temporary";

export type WhoSaidItMatchAnswerResponse =
    | { ok: true; answer: WhoSaidItMatchAnswer }
    | { ok: false; kind: WhoSaidItMatchAnswerFailureKind; message: string };

export async function answerWhoSaidItMatch(
    matchId: unknown,
    questionNumber: unknown,
    characterId: unknown,
): Promise<WhoSaidItMatchAnswerResponse> {
    if (
        typeof matchId !== "string" ||
        !MATCH_ID_PATTERN.test(matchId) ||
        typeof questionNumber !== "number" ||
        !Number.isInteger(questionNumber) ||
        questionNumber < 1 ||
        questionNumber > TOTAL_QUESTIONS ||
        typeof characterId !== "string" ||
        characterId.trim() === "" ||
        characterId.length > MAX_CHARACTER_ID_LENGTH
    ) {
        return { ok: false as const, kind: "invalid", message: INVALID_OPTION_MESSAGE };
    }

    try {
        return {
            ok: true as const,
            answer: await submitMatchAnswer(matchId, questionNumber, characterId),
        };
    } catch (error: unknown) {
        const status = statusOf(error);

        if (status === 400) {
            return { ok: false as const, kind: "invalid", message: INVALID_OPTION_MESSAGE };
        }

        if (status === 404) {
            return {
                ok: false as const,
                kind: "terminal",
                message: "This match has expired or is no longer available.",
            };
        }

        if (status === 409) {
            return {
                ok: false as const,
                kind: "stale",
                message: "Your match was out of step with the server, so it was brought up to date.",
            };
        }

        if (status !== 503) {
            console.error("Failed to submit Who Said It match answer", error);
        }

        return {
            ok: false as const,
            kind: "temporary",
            message: unavailableMessage(error) ?? "Something went wrong. Please try again.",
        };
    }
}

export type WhoSaidItMatchStateResponse =
    | { ok: true; question: WhoSaidItMatchQuestion }
    | { ok: true; result: WhoSaidItMatchResult }
    | { ok: false; terminal: boolean; message: string };

// Where the match stands according to the server: the question waiting for an answer or, when the
// match is over, its result.
export async function fetchWhoSaidItMatchState(
    matchId: unknown,
): Promise<WhoSaidItMatchStateResponse> {
    if (typeof matchId !== "string" || !MATCH_ID_PATTERN.test(matchId)) {
        return { ok: false as const, terminal: true, message: "This match is no longer available." };
    }

    try {
        return { ok: true as const, question: await getMatchQuestion(matchId) };
    } catch (error: unknown) {
        const status = statusOf(error);

        if (status === 409) {
            // No current question: the match is over.
            try {
                return { ok: true as const, result: await getMatchResult(matchId) };
            } catch (resultError: unknown) {
                if (statusOf(resultError) !== 503) {
                    console.error("Failed to read Who Said It match result", resultError);
                }

                return {
                    ok: false as const,
                    terminal: statusOf(resultError) === 404,
                    message:
                        unavailableMessage(resultError) ?? "Something went wrong. Please try again.",
                };
            }
        }

        if (status === 404) {
            return {
                ok: false as const,
                terminal: true,
                message: "This match has expired or is no longer available.",
            };
        }

        if (status !== 503) {
            console.error("Failed to read Who Said It match", error);
        }

        return {
            ok: false as const,
            terminal: false,
            message: unavailableMessage(error) ?? "Something went wrong. Please try again.",
        };
    }
}
