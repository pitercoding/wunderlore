"use client";

import { useState } from "react";
import Button from "@/components/Button/Button";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import type {
    LoreChallengeCategoryChoice,
    LoreChallengeDifficulty,
} from "@/lib/api/loreChallenge";
import { CATEGORY_CHOICES, DIFFICULTY_CHOICES } from "./labels";
import styles from "./page.module.css";

interface LoreChallengeSetupProps {
    initialCategory?: LoreChallengeCategoryChoice;
    initialDifficulty?: LoreChallengeDifficulty;
    onStart: (
        category: LoreChallengeCategoryChoice,
        difficulty: LoreChallengeDifficulty,
    ) => void;
    isStarting: boolean;
    error: string | null;
}

// The menu: native radio groups, so arrow keys, Tab and screen readers work without any
// custom keyboard handling. Starting is the only thing that talks to the backend.
export default function LoreChallengeSetup({
    initialCategory = "ALL",
    initialDifficulty = "MEDIUM",
    onStart,
    isStarting,
    error,
}: LoreChallengeSetupProps) {
    const [category, setCategory] = useState<LoreChallengeCategoryChoice>(initialCategory);
    const [difficulty, setDifficulty] = useState<LoreChallengeDifficulty>(initialDifficulty);

    return (
        <section className={styles.stage} aria-busy={isStarting}>
            <p className={styles.introText}>
                Ten questions on the lore of Middle-earth. Choose a subject and how
                hard you want it to be, then see what the archives say of your
                knowledge.
            </p>

            <fieldset className={styles.fieldset} disabled={isStarting}>
                <legend className={styles.legend}>Category</legend>
                <div className={styles.choices}>
                    {CATEGORY_CHOICES.map((choice) => (
                        <label key={choice.value} className={styles.choice}>
                            <input
                                type="radio"
                                name="category"
                                className={styles.radio}
                                value={choice.value}
                                checked={category === choice.value}
                                onChange={() => setCategory(choice.value)}
                            />
                            <span className={styles.choiceBody}>
                                <span className={styles.choiceLabel}>{choice.label}</span>
                                <span className={styles.choiceHint}>{choice.hint}</span>
                            </span>
                        </label>
                    ))}
                </div>
            </fieldset>

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
                                <span className={styles.choiceHint}>
                                    {choice.hint} · {choice.points} points each
                                </span>
                            </span>
                        </label>
                    ))}
                </div>
            </fieldset>

            {isStarting && (
                <>
                    <p role="status" className={styles.status}>
                        Preparing your match...
                    </p>
                    <ColdStartHint />
                </>
            )}

            {error && (
                <p role="alert" className={styles.error}>
                    {error}
                </p>
            )}

            <Button
                size="lg"
                stretch="mobile"
                className={styles.play}
                onClick={() => onStart(category, difficulty)}
                disabled={isStarting}
            >
                {isStarting ? "Loading..." : error ? "Try again" : "Begin the challenge"}
            </Button>
        </section>
    );
}
