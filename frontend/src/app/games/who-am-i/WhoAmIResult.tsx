"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import Button from "@/components/Button/Button";
import PublishResult from "@/components/PublishResult/PublishResult";
import type { WhoAmIResult as Result } from "@/lib/api/whoAmI";
import { cluesUsedLabel, difficultyLabel } from "./labels";
import styles from "./page.module.css";

interface WhoAmIResultProps {
    result: Result;
    // Starts another game with the same difficulty.
    onPlayAgain: () => void;
    // Back to the menu, to choose again.
    onChangeDifficulty: () => void;
    isStarting: boolean;
    error: string | null;
}

// The final screen. Every figure comes from the backend; nothing is computed from the answers
// here, so the page can only show what the server decided.
export default function WhoAmIResult({
    result,
    onPlayAgain,
    onChangeDifficulty,
    isStarting,
    error,
}: WhoAmIResultProps) {
    // The button that led here is gone, so focus would be lost: land on the title.
    const titleRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        titleRef.current?.focus();
    }, []);

    return (
        <section className={styles.stage} aria-busy={isStarting}>
            <p className={styles.tags}>{difficultyLabel(result.difficulty)}</p>

            <h2 ref={titleRef} tabIndex={-1} className={styles.verdict}>
                Case closed
            </h2>

            <p className={styles.totalScore}>
                <span className={styles.totalScoreValue}>{result.score}</span>
                <span className={styles.totalScoreMax}> / {result.maxScore} points</span>
            </p>

            <dl className={styles.stats}>
                <div className={styles.stat}>
                    <dt>Performance</dt>
                    <dd>{result.percentage}%</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Identified</dt>
                    <dd>
                        {result.identified} of {result.totalRounds}
                    </dd>
                </div>
                <div className={styles.stat}>
                    <dt>Wrong guesses</dt>
                    <dd>{result.incorrectAnswers}</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Not identified</dt>
                    <dd>{result.unidentified}</dd>
                </div>
            </dl>

            <h3 className={styles.sectionTitle}>Round by round</h3>
            <ol className={styles.summary}>
                {result.rounds.map((round) => (
                    <li key={round.roundNumber} className={styles.summaryRow}>
                        <span className={styles.summaryNumber}>{round.roundNumber}</span>
                        <span className={styles.summaryBody}>
                            <span className={styles.summaryName}>{round.characterName}</span>
                            <span className={styles.summaryDetail}>
                                {cluesUsedLabel(round.cluesUsed, round.identified)}
                            </span>
                        </span>
                        <span
                            className={
                                round.identified ? styles.summaryPoints : styles.summaryMissed
                            }
                        >
                            {round.points} pts
                        </span>
                    </li>
                ))}
            </ol>

            <PublishResult gameType="who-am-i" gameId={result.gameId} />

            {isStarting && (
                <p role="status" className={styles.status}>
                    Opening a new case file...
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
                    {isStarting ? "Loading..." : error ? "Try again" : "Play Again"}
                </Button>
                <Button
                    variant="secondary"
                    stretch="mobile"
                    onClick={onChangeDifficulty}
                    disabled={isStarting}
                >
                    Change difficulty
                </Button>
                <Link href="/#experiences" className={styles.backToGames}>
                    Back to Games
                </Link>
            </div>
        </section>
    );
}
