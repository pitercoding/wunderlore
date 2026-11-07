"use client";

import { useEffect, useRef } from "react";
import Button from "@/components/Button/Button";
import type { WhereInMiddleEarthAnswer } from "@/lib/api/whereInMiddleEarth";
import { ACCURACY_LABELS, formatDistance } from "./labels";
import styles from "./page.module.css";

interface RoundFeedbackProps {
    answer: WhereInMiddleEarthAnswer;
    isLastRound: boolean;
    onContinue: () => void;
    isAdvancing: boolean;
    advanceError: string | null;
}

// What an answered round says: how close the click was, what it earned, and where the place is.
// Everything comes from the backend's answer. The band has a word and a symbol, so the verdict
// never depends on color alone.
export default function RoundFeedback({
    answer,
    isLastRound,
    onContinue,
    isAdvancing,
    advanceError,
}: RoundFeedbackProps) {
    // The button that confirmed the answer is gone, so focus would be lost: land on the verdict.
    const titleRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        titleRef.current?.focus();
    }, []);

    const band = ACCURACY_LABELS[answer.accuracy];
    const { reveal } = answer;

    return (
        <div className={styles.feedback} aria-live="polite">
            <h3
                ref={titleRef}
                tabIndex={-1}
                className={`${styles.feedbackTitle} ${
                    answer.accuracy === "FAR" ? styles.feedbackFar : styles.feedbackGood
                }`}
            >
                <span className={styles.symbol} aria-hidden="true">
                    {band.symbol}
                </span>
                {band.label}: +{answer.pointsAwarded} points
            </h3>

            <p className={styles.feedbackDistance}>
                Your marker was <strong>{formatDistance(answer.distance)}</strong> from{" "}
                {reveal.locationName}.
            </p>

            <dl className={styles.facts}>
                <div className={styles.fact}>
                    <dt>Place</dt>
                    <dd>{reveal.locationName}</dd>
                </div>
                <div className={styles.fact}>
                    <dt>Region</dt>
                    <dd>{reveal.regionName}</dd>
                </div>
                <div className={styles.fact}>
                    <dt>Age</dt>
                    <dd>{reveal.period}</dd>
                </div>
                <div className={styles.fact}>
                    <dt>Score</dt>
                    <dd>{answer.score} points</dd>
                </div>
            </dl>

            <p className={styles.explanation}>{reveal.explanation}</p>

            {isAdvancing && (
                <p role="status" className={styles.status}>
                    {isLastRound ? "Tallying your score..." : "Finding the next place..."}
                </p>
            )}

            {advanceError && (
                <p role="alert" className={styles.error}>
                    {advanceError}
                </p>
            )}

            <Button
                className={styles.next}
                stretch="mobile"
                onClick={onContinue}
                disabled={isAdvancing}
            >
                {isAdvancing
                    ? "Loading..."
                    : advanceError
                        ? "Try again"
                        : answer.gameFinished
                            ? "See your results"
                            : "Next Round"}
            </Button>
        </div>
    );
}
