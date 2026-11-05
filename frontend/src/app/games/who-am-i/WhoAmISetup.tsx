"use client";

import { useState } from "react";
import Link from "next/link";
import Button from "@/components/Button/Button";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import type { WhoAmIDifficulty } from "@/lib/api/whoAmI";
import { CLUE_POINTS, DIFFICULTY_CHOICES } from "./labels";
import styles from "./page.module.css";

interface WhoAmISetupProps {
    initialDifficulty?: WhoAmIDifficulty;
    onStart: (difficulty: WhoAmIDifficulty) => void;
    isStarting: boolean;
    error: string | null;
}

// The menu: how a game works, what a clue is worth, and the difficulty as a native radio
// group, so arrow keys, Tab and screen readers work without any custom keyboard handling.
// Starting is the only thing that talks to the backend.
export default function WhoAmISetup({
    initialDifficulty = "MEDIUM",
    onStart,
    isStarting,
    error,
}: WhoAmISetupProps) {
    const [difficulty, setDifficulty] = useState<WhoAmIDifficulty>(initialDifficulty);

    return (
        <section className={styles.stage} aria-busy={isStarting} aria-labelledby="how-it-works">
            <h2 id="how-it-works" className={styles.sectionTitle}>
                How it works
            </h2>
            <p className={styles.introText}>
                Ten characters of Middle-earth, five clues each. You see the vaguest clue
                first and make one guess per clue: a wrong guess reveals the next clue and
                a fresh set of names. The sooner you name the character, the more it is
                worth.
            </p>

            <ol className={styles.ladder} aria-label="Points by clue">
                {CLUE_POINTS.map((points, index) => (
                    <li key={index} className={styles.rung}>
                        <span className={styles.rungClue}>Clue {index + 1}</span>
                        <span className={styles.rungPoints}>{points} points</span>
                    </li>
                ))}
            </ol>
            <p className={styles.fineprint}>
                A character you never name scores nothing. A perfect game is worth 1,000
                points, and the server keeps the score.
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
                    Difficulty changes who you meet and how deep the clues go, never what the
                    points are worth.
                </p>
            </fieldset>

            {isStarting && (
                <>
                    <p role="status" className={styles.status}>
                        Opening the case file...
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
                    {isStarting ? "Loading..." : error ? "Try again" : "Start Game"}
                </Button>
                <Link href="/#experiences" className={styles.backToGames}>
                    Back to the games
                </Link>
            </div>
        </section>
    );
}
