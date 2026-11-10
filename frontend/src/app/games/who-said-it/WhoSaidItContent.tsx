"use client";

import { useEffect, useRef, useState } from "react";
import Button from "@/components/Button/Button";
import type { WhoSaidItRound, WhoSaidItAnswerResult } from "@/lib/api/whoSaidIt";
import { submitWhoSaidItAnswer, type WhoSaidItAnswerFailure } from "./actions";
import styles from "./page.module.css";

interface GameContentProps {
    round: WhoSaidItRound;
    // Asks the game for another round; creation (and its failure) belongs to the game.
    onNewRound: () => void;
    isCreating: boolean;
    creationError: string | null;
}

// One round. The game keys it by round, so each new round starts with fresh answer state.
export default function WhoSaidItContent({
    round,
    onNewRound,
    isCreating,
    creationError,
}: GameContentProps) {
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [result, setResult] = useState<WhoSaidItAnswerResult | null>(null);
    const [failure, setFailure] = useState<WhoSaidItAnswerFailure | null>(null);

    const isRoundOver = !!result || failure?.kind === "terminal";

    // The Play button that led here is gone, so focus would be lost: land on the quote.
    const quoteRef = useRef<HTMLQuoteElement>(null);

    useEffect(() => {
        quoteRef.current?.focus();
    }, []);

    // Answering disables the option that had focus, which would drop it to the top of the page and
    // send a keyboard player back to the navigation. Land on the way forward instead, as the other
    // games do.
    const newRoundRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        if (result) {
            newRoundRef.current?.focus();
        }
    }, [result]);

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

    return (
        <section className={styles.stage} aria-busy={isSubmitting || isCreating}>
            <blockquote ref={quoteRef} tabIndex={-1} className={styles.quote}>
                “{round.quote}”
            </blockquote>

            <div
                className={styles.options}
                role="group"
                aria-label="Answer choices"
            >
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
                            disabled={isSubmitting || isRoundOver || isCreating}
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
                            <h2
                                className={
                                    result.correct
                                        ? styles.resultCorrect
                                        : styles.resultIncorrect
                                }
                            >
                                {result.correct ? "Correct!" : "Not quite!"}
                            </h2>
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

                    {isCreating && (
                        <p role="status" className={styles.status}>
                            Preparing your round...
                        </p>
                    )}

                    {creationError && (
                        <p role="alert" className={styles.error}>
                            {creationError}
                        </p>
                    )}

                    <Button
                        ref={newRoundRef}
                        className={styles.newRound}
                        stretch="mobile"
                        onClick={onNewRound}
                        disabled={isCreating}
                    >
                        {isCreating
                            ? "Loading..."
                            : creationError
                                ? "Try again"
                                : "Play another round"}
                    </Button>
                </div>
            )}

    </section>
    );
}
