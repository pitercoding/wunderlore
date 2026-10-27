"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import type { GameRound, AnswerResult } from "@/lib/api/game";
import { submitGameAnswer } from "./actions";
import styles from "./page.module.css";

interface GameContentProps {
    round: GameRound;
}

export default function GameContent({ round }: GameContentProps) {
    const router = useRouter();

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [result, setResult] = useState<AnswerResult | null>(null);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    async function handleAnswer(characterId: string) {
        if (isSubmitting || result) {
            return;
        }

        setIsSubmitting(true);
        setErrorMessage(null);

        try {
            const response = await submitGameAnswer(
                round.roundId,
                characterId,
            );

            if (!response.ok) {
                setErrorMessage(response.message);
                return;
            }

            setResult(response.result);
        } catch {
            setErrorMessage("Something went wrong. Please try again.");
        } finally {
            setIsSubmitting(false);
        }
    }

    function handleNewRound() {
        setIsRefreshing(true);
        router.refresh();
    }

    return (
        <main className={styles.container}>
            <header className={styles.header}>
                <p className={styles.eyebrow}>WHO SAID IT?</p>
                <h1>Who said this quote?</h1>
                <p>Choose the character you think said it.</p>
            </header>

            <section className={styles.gameCard} aria-busy={isSubmitting}>
                <blockquote className={styles.quote}>
                    “{round.quote}”
                </blockquote>

                <div className={styles.options}>
                    {round.options.map((option) => {
                        const isCorrect =
                            result?.correctCharacterId === option.id;
                        const isSelected =
                            result?.submittedCharacterId === option.id;

                        let optionClass = styles.option;

                        if (result && isCorrect) {
                            optionClass += ` ${styles.correct}`;
                        } else if (result && isSelected && !result.correct) {
                            optionClass += ` ${styles.incorrect}`;
                        }

                        return (
                            <button
                                key={option.id}
                                type="button"
                                className={optionClass}
                                onClick={() => handleAnswer(option.id)}
                                disabled={isSubmitting || !!result || isRefreshing}
                            >
                                {option.name}
                            </button>
                        );
                    })}
                </div>

                {isSubmitting && (
                    <p role="status" className={styles.status}>
                        Checking your answer...
                    </p>
                )}

                {errorMessage && (
                    <p role="alert" className={styles.error}>
                        {errorMessage}
                    </p>
                )}


                {(result || errorMessage) && (
                    <div className={styles.result} aria-live="polite">
                        {result ? (
                            <>
                                <h2>{result.correct ? "Correct!" : "Not quite!"}</h2>
                                <p>
                                    The correct answer is{" "}
                                    <strong>
                                        {round.options.find(
                                            (option) =>
                                                option.id === result.correctCharacterId,
                                        )?.name ?? "Unknown"}
                                    </strong>.
                                </p>
                            </>
                        ) : (
                            <p>Start a new round to continue playing.</p>
                        )}

                        <button
                            type="button"
                            className={styles.newRound}
                            onClick={handleNewRound}
                            disabled={isRefreshing}
                        >
                            {isRefreshing ? "Loading..." : "Play another round"}
                        </button>
                    </div>
                )}

            </section>
        </main>
    );
}
