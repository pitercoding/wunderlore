"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import Button from "@/components/Button/Button";
import type { LoreChallengeResult } from "@/lib/api/loreChallenge";
import { categoryLabel, difficultyLabel, formatDuration } from "./labels";
import styles from "./page.module.css";

interface LoreChallengeSummaryProps {
    result: LoreChallengeResult;
    // Starts another match with the same category and difficulty.
    onPlayAgain: () => void;
    // Back to the menu, to choose again.
    onChangeSelection: () => void;
    isStarting: boolean;
    error: string | null;
}

// The final screen. Every figure comes from the backend; nothing is computed from the answers
// here, so the page can only show what the server decided.
export default function LoreChallengeSummary({
    result,
    onPlayAgain,
    onChangeSelection,
    isStarting,
    error,
}: LoreChallengeSummaryProps) {
    // The Next button that led here is gone, so focus would be lost: land on the title.
    const titleRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        titleRef.current?.focus();
    }, []);

    return (
        <section className={styles.stage} aria-busy={isStarting}>
            <p className={styles.tags}>
                {categoryLabel(result.category)} · {difficultyLabel(result.difficulty)}
            </p>

            <h2 ref={titleRef} tabIndex={-1} className={styles.rank}>
                {result.rank}
            </h2>

            <p className={styles.totalScore}>
                <span className={styles.totalScoreValue}>{result.score}</span>
                <span className={styles.totalScoreMax}> / {result.maxScore} points</span>
            </p>

            <dl className={styles.stats}>
                <div className={styles.stat}>
                    <dt>Correct</dt>
                    <dd>{result.correctAnswers}</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Incorrect</dt>
                    <dd>{result.incorrectAnswers}</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Accuracy</dt>
                    <dd>{result.percentage}%</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Time</dt>
                    <dd>{formatDuration(result.durationSeconds)}</dd>
                </div>
            </dl>

            {isStarting && (
                <p role="status" className={styles.status}>
                    Preparing your match...
                </p>
            )}

            {error && (
                <p role="alert" className={styles.error}>
                    {error}
                </p>
            )}

            <div className={styles.actions}>
                <Button
                    className={styles.next}
                    stretch="mobile"
                    onClick={onPlayAgain}
                    disabled={isStarting}
                >
                    {isStarting ? "Loading..." : error ? "Try again" : "Play again"}
                </Button>
                <Button
                    variant="secondary"
                    stretch="mobile"
                    onClick={onChangeSelection}
                    disabled={isStarting}
                >
                    Change category or difficulty
                </Button>
                <Link href="/#experiences" className={styles.backToGames}>
                    Back to the games
                </Link>
            </div>
        </section>
    );
}
