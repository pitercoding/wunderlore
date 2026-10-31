"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import type { WhoSaidItRound, WhoSaidItAnswerResult } from "@/lib/api/whoSaidIt";
import { submitWhoSaidItAnswer, type WhoSaidItAnswerFailure } from "./actions";
import styles from "./page.module.css";

interface GameContentProps {
    round: WhoSaidItRound;
}

export default function WhoSaidItContent({ round }: GameContentProps) {
    const router = useRouter();

    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [result, setResult] = useState<WhoSaidItAnswerResult | null>(null);
    const [failure, setFailure] = useState<WhoSaidItAnswerFailure | null>(null);

    const isRoundOver = !!result || failure?.kind === "terminal";

    async function handleAnswer(characterId: string) {
        if (isSubmitting || isRoundOver) {
            return;
        }

        setIsSubmitting(true);
        setFailure(null);

        try {
            const response = await submitWhoSaidItAnswer(
                round.roundId,
                characterId,
            );

            if (!response.ok) {
                setFailure(response);
                return;
            }

            setResult(response.result);
        } catch {
            setFailure({
                ok: false,
                kind: "temporary",
                message: "Something went wrong. Please try again.",
            });
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
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

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
                                disabled={isSubmitting || isRoundOver || isRefreshing}
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

                {failure && (
                    <p role="alert" className={styles.error}>
                        {failure.message}
                    </p>
                )}


                {(result || failure) && (
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
                            <p>
                                {isRoundOver
                                    ? "Start a new round to continue playing."
                                    : "Choose an option again, or start a new round."}
                            </p>
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
