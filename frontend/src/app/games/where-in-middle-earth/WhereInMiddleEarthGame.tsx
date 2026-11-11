"use client";

import { useRef, useState } from "react";
import type {
    WhereInMiddleEarthAnswer,
    WhereInMiddleEarthDifficulty,
    WhereInMiddleEarthResult as Result,
    WhereInMiddleEarthRound,
} from "@/lib/api/whereInMiddleEarth";
import {
    fetchWhereInMiddleEarthResult,
    fetchWhereInMiddleEarthRound,
    startWhereInMiddleEarthGame,
} from "./actions";
import WhereInMiddleEarthFrame from "./WhereInMiddleEarthFrame";
import WhereInMiddleEarthPlay from "./WhereInMiddleEarthPlay";
import WhereInMiddleEarthResult from "./WhereInMiddleEarthResult";
import WhereInMiddleEarthSetup from "./WhereInMiddleEarthSetup";

type Screen =
    | { name: "setup" }
    | { name: "playing"; round: WhereInMiddleEarthRound }
    | { name: "result"; result: Result };

// Owns the screen and the one request that may be in flight. The first screen has asked the
// backend for nothing; a game is created only by the player's own click. The sequence of the
// game belongs to the backend: this component only asks for the round the server says is next,
// or shows the result the server sent with the last answer.
export default function WhereInMiddleEarthGame() {
    const [screen, setScreen] = useState<Screen>({ name: "setup" });
    const [difficulty, setDifficulty] = useState<WhereInMiddleEarthDifficulty>("MEDIUM");
    const [isBusy, setIsBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // The answer that ended the current round: what "Next Round" continues from.
    const endedRound = useRef<WhereInMiddleEarthAnswer | null>(null);

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

    function start(chosen: WhereInMiddleEarthDifficulty) {
        return exclusive(async () => {
            setDifficulty(chosen);

            const response = await startWhereInMiddleEarthGame(chosen);

            if (!response.ok) {
                setError(response.message);
                return;
            }

            endedRound.current = null;
            setScreen({ name: "playing", round: response.round });
        });
    }

    function handleRoundEnded(answer: WhereInMiddleEarthAnswer) {
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
            const response = await fetchWhereInMiddleEarthRound(gameId);

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
            const round = await fetchWhereInMiddleEarthRound(gameId);

            if (round.ok) {
                endedRound.current = null;
                setScreen({ name: "playing", round: round.round });
                return;
            }

            if (round.kind === "stale") {
                const result = await fetchWhereInMiddleEarthResult(gameId);

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
            <WhereInMiddleEarthFrame
                title="Where in Middle-earth"
                badge="Find the Place"
                description="Read the name. Find it on the map."
                compact
            >
                <WhereInMiddleEarthPlay
                    key={`${round.gameId}-${round.roundNumber}`}
                    round={round}
                    onRoundEnded={handleRoundEnded}
                    onContinue={() => advance(round.gameId)}
                    onResync={() => resync(round.gameId)}
                    onRestart={backToSetup}
                    isAdvancing={isBusy}
                    advanceError={error}
                />
            </WhereInMiddleEarthFrame>
        );
    }

    if (screen.name === "result") {
        return (
            <WhereInMiddleEarthFrame
                title="Journey's end"
                badge="Your journey"
                description="Here is how well you know the lay of the land."
            >
                <WhereInMiddleEarthResult
                    result={screen.result}
                    onPlayAgain={() => start(difficulty)}
                    onChangeDifficulty={backToSetup}
                    isStarting={isBusy}
                    error={error}
                />
            </WhereInMiddleEarthFrame>
        );
    }

    return (
        <WhereInMiddleEarthFrame
            title="Where in Middle-earth"
            badge="Click the map. Find the place."
            description="Ten places, one map. How well do you know the lay of Middle-earth?"
        >
            <WhereInMiddleEarthSetup
                initialDifficulty={difficulty}
                onStart={start}
                isStarting={isBusy}
                error={error}
            />
        </WhereInMiddleEarthFrame>
    );
}
