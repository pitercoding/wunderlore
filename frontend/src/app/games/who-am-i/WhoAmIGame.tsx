"use client";

import { useRef, useState } from "react";
import type {
    WhoAmIAnswer,
    WhoAmIDifficulty,
    WhoAmIResult as Result,
    WhoAmIRound,
} from "@/lib/api/whoAmI";
import { fetchWhoAmIResult, fetchWhoAmIRound, startWhoAmIGame } from "./actions";
import WhoAmIFrame from "./WhoAmIFrame";
import WhoAmIPlay from "./WhoAmIPlay";
import WhoAmIResult from "./WhoAmIResult";
import WhoAmISetup from "./WhoAmISetup";

type Screen =
    | { name: "setup" }
    | { name: "playing"; round: WhoAmIRound }
    | { name: "result"; result: Result };

// Owns the screen and the one request that may be in flight. The first screen has asked the
// backend for nothing; a game is created only by the player's own click. The sequence of the
// game belongs to the backend: this component only asks for the round the server says is next,
// or shows the result the server sent with the last answer.
export default function WhoAmIGame() {
    const [screen, setScreen] = useState<Screen>({ name: "setup" });
    const [difficulty, setDifficulty] = useState<WhoAmIDifficulty>("MEDIUM");
    const [isBusy, setIsBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // The answer that ended the current round: what "Next character" continues from.
    const endedRound = useRef<WhoAmIAnswer | null>(null);

    // State updates are asynchronous, so a double click could pass the isBusy check twice.
    const inFlight = useRef(false);

    // Runs one request at a time; the rest of the game only reacts to its outcome.
    async function exclusive(task: () => Promise<void>) {
        if (inFlight.current) {
            return;
        }

        inFlight.current = true;
        setIsBusy(true);
        setError(null);

        try {
            await task();
        } catch {
            setError("Something went wrong. Please try again.");
        } finally {
            inFlight.current = false;
            setIsBusy(false);
        }
    }

    function start(chosen: WhoAmIDifficulty) {
        return exclusive(async () => {
            setDifficulty(chosen);

            const response = await startWhoAmIGame(chosen);

            if (!response.ok) {
                setError(response.message);
                return;
            }

            endedRound.current = null;
            setScreen({ name: "playing", round: response.round });
        });
    }

    function handleRoundEnded(answer: WhoAmIAnswer) {
        endedRound.current = answer;
    }

    // After a round: the result if the game is over (the server sent it with the last answer),
    // otherwise the next round.
    function advance(gameId: string) {
        const ended = endedRound.current;

        if (ended?.gameFinished && ended.result) {
            setError(null);
            setScreen({ name: "result", result: ended.result });
            return;
        }

        return exclusive(async () => {
            const response = await fetchWhoAmIRound(gameId);

            if (response.ok) {
                endedRound.current = null;
                setScreen({ name: "playing", round: response.round });
                return;
            }

            setError(response.message);
        });
    }

    // After a 409 the client and the server disagree about where the game is: ask for the round
    // and, if the game is already over, for the result, so a lost response or a second tab can
    // never leave the player stuck.
    function resync(gameId: string) {
        return exclusive(async () => {
            const round = await fetchWhoAmIRound(gameId);

            if (round.ok) {
                endedRound.current = null;
                setScreen({ name: "playing", round: round.round });
                return;
            }

            if (round.kind === "stale") {
                const result = await fetchWhoAmIResult(gameId);

                if (result.ok) {
                    setScreen({ name: "result", result: result.result });
                    return;
                }

                setError(result.message);
                return;
            }

            setError(round.message);
        });
    }

    function backToSetup() {
        setError(null);
        endedRound.current = null;
        setScreen({ name: "setup" });
    }

    if (screen.name === "playing") {
        const { round } = screen;

        return (
            <WhoAmIFrame
                title="Who Am I?"
                badge="Guess the Character"
                description="Read the clues. Name the character."
            >
                <WhoAmIPlay
                    // The clue too: after a resynchronization the same round can come back
                    // further along, and the page must start from what the server says.
                    key={`${round.gameId}-${round.roundNumber}-${round.clueNumber}`}
                    round={round}
                    onRoundEnded={handleRoundEnded}
                    onContinue={() => advance(round.gameId)}
                    onResync={() => resync(round.gameId)}
                    onRestart={backToSetup}
                    isAdvancing={isBusy}
                    advanceError={error}
                />
            </WhoAmIFrame>
        );
    }

    if (screen.name === "result") {
        return (
            <WhoAmIFrame
                title="Case closed"
                badge="Your verdict"
                description="Here is how well you read the clues."
            >
                <WhoAmIResult
                    result={screen.result}
                    onPlayAgain={() => start(difficulty)}
                    onChangeDifficulty={backToSetup}
                    isStarting={isBusy}
                    error={error}
                />
            </WhoAmIFrame>
        );
    }

    return (
        <WhoAmIFrame
            title="Who Am I?"
            badge="Guess the Character"
            description="Five clues. One character. How well do you know Middle-earth?"
        >
            <WhoAmISetup
                initialDifficulty={difficulty}
                onStart={start}
                isStarting={isBusy}
                error={error}
            />
        </WhoAmIFrame>
    );
}
