"use client";

import { useRef, useState } from "react";
import Button from "@/components/Button/Button";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import type { WhoSaidItRound } from "@/lib/api/whoSaidIt";
import type { WhoSaidItMatchQuestion } from "@/lib/api/whoSaidItMatch";
import { createWhoSaidItRound } from "./actions";
import { startWhoSaidItMatch } from "./matchActions";
import WhoSaidItContent from "./WhoSaidItContent";
import WhoSaidItFrame from "./WhoSaidItFrame";
import WhoSaidItMatch from "./WhoSaidItMatch";
import styles from "./page.module.css";

// Owns the scene and whether a round or a match exists. The first state has neither and has
// asked the backend for nothing; they are created only by the player's own click. A round is the
// original single-quote game; a match is the competitive one: ten quotes scored by the backend,
// whose result can be published to the leaderboard.
export default function WhoSaidItGame() {
    const [round, setRound] = useState<WhoSaidItRound | null>(null);
    const [match, setMatch] = useState<WhoSaidItMatchQuestion | null>(null);
    const [isCreating, setIsCreating] = useState(false);
    const [creationError, setCreationError] = useState<string | null>(null);

    // State updates are asynchronous, so a double click could pass the isCreating check twice.
    const inFlight = useRef(false);

    async function handleNewRound() {
        if (inFlight.current) {
            return;
        }

        inFlight.current = true;
        setIsCreating(true);
        setCreationError(null);

        try {
            const response = await createWhoSaidItRound();

            if (!response.ok) {
                setCreationError(response.message);
                return;
            }

            setRound(response.round);
        } catch {
            setCreationError("Something went wrong. Please try again.");
        } finally {
            inFlight.current = false;
            setIsCreating(false);
        }
    }

    async function handleNewMatch() {
        if (inFlight.current) {
            return;
        }

        inFlight.current = true;
        setIsCreating(true);
        setCreationError(null);

        try {
            const response = await startWhoSaidItMatch();

            if (!response.ok) {
                setCreationError(response.message);
                return;
            }

            setMatch(response.question);
        } catch {
            setCreationError("Something went wrong. Please try again.");
        } finally {
            inFlight.current = false;
            setIsCreating(false);
        }
    }

    function backToIntro() {
        setMatch(null);
        setRound(null);
        setCreationError(null);
    }

    if (match) {
        return (
            <WhoSaidItFrame
                title="Ranked match"
                description="Ten quotes, four speakers each. Your score is checked by Wunderlore, so you can share it on the leaderboard."
            >
                <WhoSaidItMatch
                    // A new match replaces the whole component, so its state starts clean.
                    key={match.matchId}
                    initial={match}
                    onNewMatch={handleNewMatch}
                    isCreating={isCreating}
                    creationError={creationError}
                    onExit={backToIntro}
                />
            </WhoSaidItFrame>
        );
    }

    if (round) {
        return (
            <WhoSaidItFrame
                title="Who said this quote?"
                description="Choose the character you think said it."
            >
                <WhoSaidItContent
                    key={round.roundId}
                    round={round}
                    onNewRound={handleNewRound}
                    isCreating={isCreating}
                    creationError={creationError}
                />
            </WhoSaidItFrame>
        );
    }

    return (
        <WhoSaidItFrame
            title="Who Said It?"
            description="A line from Middle-earth, four possible speakers."
        >
            <section className={`${styles.stage} ${styles.intro}`} aria-busy={isCreating}>
                <p className={styles.introText}>
                    A quote is read from the archives, and four characters stand
                    before you. Only one of them said it. Choose wisely.
                </p>

                {isCreating && (
                    <>
                        <p role="status" className={styles.status}>
                            Preparing your round...
                        </p>
                        <ColdStartHint />
                    </>
                )}

                {creationError && (
                    <p role="alert" className={styles.error}>
                        {creationError}
                    </p>
                )}

                <div className={styles.modes}>
                    <Button
                        size="lg"
                        stretch="mobile"
                        className={styles.play}
                        onClick={handleNewRound}
                        disabled={isCreating}
                    >
                        {isCreating ? "Loading..." : creationError ? "Try again" : "Play"}
                    </Button>
                    <Button
                        size="lg"
                        variant="secondary"
                        stretch="mobile"
                        className={styles.play}
                        onClick={handleNewMatch}
                        disabled={isCreating}
                    >
                        Start ranked match
                    </Button>
                </div>
                <p className={styles.modeHint}>
                    A ranked match is ten quotes, scored out of 100, and can be published to
                    the leaderboard.
                </p>
            </section>
        </WhoSaidItFrame>
    );
}
