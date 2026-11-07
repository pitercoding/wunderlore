"use client";

import { useEffect, useRef, useState } from "react";
import Button from "@/components/Button/Button";
import type {
    MapPosition,
    WhereInMiddleEarthAnswer,
    WhereInMiddleEarthRound,
} from "@/lib/api/whereInMiddleEarth";
import { submitWhereInMiddleEarthAnswer, type WhereInMiddleEarthFailure } from "./actions";
import { difficultyLabel } from "./labels";
import { describePosition } from "./mapCoordinates";
import MiddleEarthMap from "./MiddleEarthMap";
import RoundFeedback from "./RoundFeedback";
import styles from "./page.module.css";

interface WhereInMiddleEarthPlayProps {
    round: WhereInMiddleEarthRound;
    // Called once, when the round has been answered.
    onRoundEnded: (answer: WhereInMiddleEarthAnswer) => void;
    // Asks the game for what comes next: the next place, or the result after the last one.
    onContinue: () => void;
    // Asks the game to resynchronize with the server after a 409.
    onResync: () => void;
    onRestart: () => void;
    isAdvancing: boolean;
    advanceError: string | null;
}

// One round. The game keys it by game and round, so each place starts with a fresh state: no
// marker, nothing answered. Nothing is sent when the map is clicked; the answer goes to the
// server only when the player confirms, and only once.
export default function WhereInMiddleEarthPlay({
    round,
    onRoundEnded,
    onContinue,
    onResync,
    onRestart,
    isAdvancing,
    advanceError,
}: WhereInMiddleEarthPlayProps) {
    const [selection, setSelection] = useState<MapPosition | null>(null);
    const [score, setScore] = useState(round.score);
    const [ended, setEnded] = useState<WhereInMiddleEarthAnswer | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [failure, setFailure] = useState<WhereInMiddleEarthFailure | null>(null);

    // State updates are asynchronous, so a double click could pass the isSubmitting check twice.
    const inFlight = useRef(false);

    // The control that led here is gone, so focus would be lost: land on the question.
    const questionRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        questionRef.current?.focus();
    }, []);

    const isLocked = isSubmitting || !!ended || failure?.kind === "terminal" || isAdvancing;
    const isLastRound = round.roundNumber === round.totalRounds;

    async function handleConfirm() {
        if (!selection || inFlight.current || isLocked) {
            return;
        }

        inFlight.current = true;
        setIsSubmitting(true);
        setFailure(null);

        try {
            const response = await submitWhereInMiddleEarthAnswer(
                round.gameId,
                round.roundNumber,
                selection.x,
                selection.y,
            );

            if (!response.ok) {
                setFailure(response);
                return;
            }

            setScore(response.answer.score);
            setEnded(response.answer);
            onRoundEnded(response.answer);
        } catch {
            setFailure({
                ok: false,
                kind: "temporary",
                message: "Something went wrong. Please try again.",
            });
        } finally {
            inFlight.current = false;
            setIsSubmitting(false);
        }
    }

    return (
        <section className={styles.stage} aria-busy={isSubmitting || isAdvancing}>
            <div className={styles.progressRow}>
                <h2 className={styles.roundHeading}>
                    Round {round.roundNumber} of {round.totalRounds}
                </h2>
                <p className={styles.scoreLabel}>
                    <span className={styles.scoreValue}>{score}</span> points
                </p>
            </div>

            <progress
                className={styles.progress}
                max={round.totalRounds}
                value={ended ? round.roundNumber : round.roundNumber - 1}
                aria-label="Game progress"
            />

            <p className={styles.tags}>{difficultyLabel(round.difficulty)}</p>

            <div className={styles.prompt}>
                <p className={styles.promptLead}>Find on the map</p>
                <h3 ref={questionRef} tabIndex={-1} className={styles.question}>
                    Where is <span className={styles.placeName}>{round.locationName}</span>?
                </h3>
                <p className={styles.placeDescription}>{round.description}</p>
            </div>

            <div className={styles.mapWrap}>
                <MiddleEarthMap
                    selection={selection}
                    onSelect={setSelection}
                    onClear={() => setSelection(null)}
                    disabled={isLocked}
                    feedback={
                        ended
                            ? {
                                  guess: ended.submitted,
                                  answer: { x: ended.reveal.x, y: ended.reveal.y },
                                  answerLabel: ended.reveal.locationName,
                                  distance: ended.distance,
                              }
                            : null
                    }
                />
            </div>

            {!ended && (
                <div className={styles.confirmRow}>
                    <p className={`${styles.readout} ${selection ? "" : styles.readoutMuted}`}>
                        {selection
                            ? `Your marker is at ${describePosition(selection)}.`
                            : "No marker yet. Choose a spot on the map."}
                    </p>
                    <Button
                        className={styles.confirm}
                        stretch="mobile"
                        onClick={handleConfirm}
                        disabled={!selection || isLocked}
                    >
                        {isSubmitting ? "Checking..." : "Confirm"}
                    </Button>
                </div>
            )}

            {isSubmitting && (
                <p role="status" className={styles.status}>
                    Checking your answer...
                </p>
            )}

            {failure && (
                <div role="alert" className={styles.error}>
                    <p>{failure.message}</p>
                    {!ended && advanceError && <p>{advanceError}</p>}
                    {failure.kind === "stale" && (
                        <Button
                            variant="secondary"
                            className={styles.inlineAction}
                            onClick={onResync}
                            disabled={isAdvancing}
                        >
                            Continue
                        </Button>
                    )}
                    {failure.kind === "terminal" && (
                        <Button
                            variant="secondary"
                            className={styles.inlineAction}
                            onClick={onRestart}
                        >
                            Start a new game
                        </Button>
                    )}
                </div>
            )}

            {ended && (
                <RoundFeedback
                    answer={ended}
                    isLastRound={isLastRound}
                    onContinue={onContinue}
                    isAdvancing={isAdvancing}
                    advanceError={advanceError}
                />
            )}
        </section>
    );
}
