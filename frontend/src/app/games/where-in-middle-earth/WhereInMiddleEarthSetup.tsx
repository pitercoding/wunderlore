"use client";

import { useState } from "react";
import Link from "next/link";
import Button from "@/components/Button/Button";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import type { WhereInMiddleEarthDifficulty } from "@/lib/api/whereInMiddleEarth";
import { ACCURACY_BANDS, ACCURACY_LABELS, DIFFICULTY_CHOICES } from "./labels";
import styles from "./page.module.css";

interface WhereInMiddleEarthSetupProps {
    initialDifficulty?: WhereInMiddleEarthDifficulty;
    onStart: (difficulty: WhereInMiddleEarthDifficulty) => void;
    isStarting: boolean;
    error: string | null;
}

// The menu: how a game works, what a click is worth, and the difficulty as a native radio group,
// so arrow keys, Tab and screen readers work without any custom keyboard handling. Starting is
// the only thing that talks to the backend.
export default function WhereInMiddleEarthSetup({
    initialDifficulty = "MEDIUM",
    onStart,
    isStarting,
    error,
}: WhereInMiddleEarthSetupProps) {
    const [difficulty, setDifficulty] = useState<WhereInMiddleEarthDifficulty>(initialDifficulty);

    return (
        <section className={styles.stage} aria-busy={isStarting} aria-labelledby="how-it-works">
            <h2 id="how-it-works" className={styles.sectionTitle}>
                How it works
            </h2>
            <p className={styles.introText}>
                Ten places of Middle-earth, one map. Click where each name lies on the
                unlabelled map, move the marker until you are sure, then confirm. The closer
                you are, the more the round is worth.
            </p>

            <ol className={styles.bands} aria-label="Points by distance">
                {ACCURACY_BANDS.map((band) => (
                    <li key={band.accuracy} className={styles.band}>
                        <span className={styles.bandName}>
                            <span aria-hidden="true">{ACCURACY_LABELS[band.accuracy].symbol} </span>
                            {ACCURACY_LABELS[band.accuracy].label}
                        </span>
                        <span className={styles.bandRange}>{band.range}</span>
                        <span className={styles.bandPoints}>{band.points} points</span>
                    </li>
                ))}
            </ol>
            <p className={styles.fineprint}>
                Distance is measured on the map, which is 1,000 units wide, and the server keeps
                the score. A perfect game is worth 1,000 points. The map shows only the great
                regions: no place you are asked about is written on it, and it is drawn to show
                where things lie, not how far apart they are.
            </p>

            <fieldset className={styles.fieldset} disabled={isStarting}>
                <legend className={styles.legend}>Difficulty</legend>
                <div className={`${styles.choices} ${styles.difficulties}`}>
                    {DIFFICULTY_CHOICES.map((choice) => (
                        <label key={choice.value} className={styles.choice}>
                            <input
                                type="radio"
                                name="difficulty"
                                className={styles.radio}
                                value={choice.value}
                                checked={difficulty === choice.value}
                                onChange={() => setDifficulty(choice.value)}
                            />
                            <span className={styles.choiceBody}>
                                <span className={styles.choiceLabel}>{choice.label}</span>
                                <span className={styles.choiceHint}>{choice.hint}</span>
                            </span>
                        </label>
                    ))}
                </div>
                <p className={styles.fineprint}>
                    Difficulty changes which places you are asked about, never what the points
                    are worth.
                </p>
            </fieldset>

            {isStarting && (
                <>
                    <p role="status" className={styles.status}>
                        Unrolling the map...
                    </p>
                    <ColdStartHint />
                </>
            )}

            {error && (
                <p role="alert" className={styles.error}>
                    {error}
                </p>
            )}

            <div className={styles.actions}>
                <Button
                    size="lg"
                    stretch="mobile"
                    className={styles.play}
                    onClick={() => onStart(difficulty)}
                    disabled={isStarting}
                >
                    {isStarting ? "Loading..." : error ? "Try again" : "Start game"}
                </Button>
                <Link href="/#experiences" className={styles.backToGames}>
                    Back to the games
                </Link>
            </div>
        </section>
    );
}
