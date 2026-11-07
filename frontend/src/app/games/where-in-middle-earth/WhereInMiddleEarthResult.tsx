"use client";

import { useEffect, useRef } from "react";
import Link from "next/link";
import Button from "@/components/Button/Button";
import PublishResult from "@/components/PublishResult/PublishResult";
import type { WhereInMiddleEarthResult as Result } from "@/lib/api/whereInMiddleEarth";
import { ACCURACY_LABELS, difficultyLabel, formatDistance } from "./labels";
import styles from "./page.module.css";

interface WhereInMiddleEarthResultProps {
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
export default function WhereInMiddleEarthResult({
    result,
    onPlayAgain,
    onChangeDifficulty,
    isStarting,
    error,
}: WhereInMiddleEarthResultProps) {
    // The button that led here is gone, so focus would be lost: land on the title.
    const titleRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        titleRef.current?.focus();
    }, []);

    return (
        <section className={styles.stage} aria-busy={isStarting}>
            <p className={styles.tags}>{difficultyLabel(result.difficulty)}</p>

            <h2 ref={titleRef} tabIndex={-1} className={styles.verdict}>
                Journey&apos;s end
            </h2>

            <p className={styles.totalScore}>
                <span className={styles.totalScoreValue}>{result.score}</span>
                <span className={styles.totalScoreMax}> / {result.maxScore} points</span>
            </p>

            <dl className={styles.stats}>
                <div className={styles.stat}>
                    <dt>Accuracy</dt>
                    <dd>{result.percentage}%</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Near hits</dt>
                    <dd>
                        {result.closeHits} of {result.totalRounds}
                    </dd>
                </div>
                <div className={styles.stat}>
                    <dt>Average distance</dt>
                    <dd>{formatDistance(result.averageDistance)}</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Rounds</dt>
                    <dd>{result.totalRounds}</dd>
                </div>
            </dl>

            <h3 className={styles.sectionTitle}>Round by round</h3>
            <ol className={styles.summary}>
                {result.rounds.map((round) => {
                    const band = ACCURACY_LABELS[round.accuracy];

                    return (
                        <li key={round.roundNumber} className={styles.summaryRow}>
                            <span className={styles.summaryNumber}>{round.roundNumber}</span>
                            <span className={styles.summaryBody}>
                                <span className={styles.summaryName}>{round.locationName}</span>
                                <span className={styles.summaryDetail}>
                                    <span aria-hidden="true">{band.symbol} </span>
                                    {band.label}
                                    <span className={styles.summaryDistance}>
                                        {" "}
                                        · {formatDistance(round.distance)} away
                                    </span>
                                </span>
                            </span>
                            <span
                                className={round.points > 0 ? styles.summaryPoints : styles.summaryMissed}
                            >
                                {round.points} pts
                            </span>
                        </li>
                    );
                })}
            </ol>

            <PublishResult gameType="where-in-middle-earth" gameId={result.gameId} />

            {isStarting && (
                <p role="status" className={styles.status}>
                    Unrolling a new map...
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
