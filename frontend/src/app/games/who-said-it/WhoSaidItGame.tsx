"use client";

import { useRef, useState } from "react";
import Button from "@/components/Button/Button";
import type { WhoSaidItRound } from "@/lib/api/whoSaidIt";
import { createWhoSaidItRound } from "./actions";
import WhoSaidItContent from "./WhoSaidItContent";
import WhoSaidItFrame from "./WhoSaidItFrame";
import styles from "./page.module.css";

// Owns the scene and whether a round exists. The first state has no round and has
// asked the backend for nothing; a round is created only by the player's own click.
export default function WhoSaidItGame() {
    const [round, setRound] = useState<WhoSaidItRound | null>(null);
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
                    <p role="status" className={styles.status}>
                        Preparing your round...
                    </p>
                )}

                {creationError && (
                    <p role="alert" className={styles.error}>
                        {creationError}
                    </p>
                )}

                <Button
                    size="lg"
                    stretch="mobile"
                    className={styles.play}
                    onClick={handleNewRound}
                    disabled={isCreating}
                >
                    {isCreating ? "Loading..." : creationError ? "Try again" : "Play"}
                </Button>
            </section>
        </WhoSaidItFrame>
    );
}
