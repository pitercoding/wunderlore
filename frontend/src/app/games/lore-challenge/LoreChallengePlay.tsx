"use client";

import { useEffect, useRef, useState } from "react";
import Button from "@/components/Button/Button";
import type { LoreChallengeAnswer, LoreChallengeQuestion } from "@/lib/api/loreChallenge";
import { submitLoreChallengeAnswer, type LoreChallengeFailure } from "./actions";
import { categoryLabel, difficultyLabel } from "./labels";
import styles from "./page.module.css";

interface LoreChallengePlayProps {
    question: LoreChallengeQuestion;
    // Points scored so far, before this question.
    score: number;
    onAnswered: (answer: LoreChallengeAnswer) => void;
    // Asks the game for what comes next (the next question, or the result after the last one).
    // finished tells which one is expected; the game resynchronizes if the server disagrees.
    onContinue: (finished: boolean) => void;
    onRestart: () => void;
    isAdvancing: boolean;
    advanceError: string | null;
}

// One question. The game keys it by question, so each one starts with fresh answer state.
export default function LoreChallengePlay({
    question,
    score,
    onAnswered,
    onContinue,
    onRestart,
    isAdvancing,
    advanceError,
}: LoreChallengePlayProps) {
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [answer, setAnswer] = useState<LoreChallengeAnswer | null>(null);
    const [failure, setFailure] = useState<LoreChallengeFailure | null>(null);

    // State updates are asynchronous, so a double click could pass the isSubmitting check twice.
    const inFlight = useRef(false);

    // The control that led here is gone, so focus would be lost: land on the question.
    const promptRef = useRef<HTMLHeadingElement>(null);
    const continueRef = useRef<HTMLButtonElement>(null);

    useEffect(() => {
        promptRef.current?.focus();
    }, []);

    // The answered option is disabled, which drops focus: move it to the way forward.
    useEffect(() => {
        if (answer) {
            continueRef.current?.focus();
        }
    }, [answer]);

    const isLocked = isSubmitting || !!answer || failure?.kind === "terminal" || isAdvancing;
    const isLast = question.number === question.total;
    const shownScore = answer ? answer.score : score;

    async function handleAnswer(optionId: string) {
        if (inFlight.current || isLocked) {
            return;
        }

        inFlight.current = true;
        setIsSubmitting(true);
        setFailure(null);

        try {
            const response = await submitLoreChallengeAnswer(
                question.matchId,
                question.questionId,
                optionId,
            );

            if (!response.ok) {
                setFailure(response);
                return;
            }

            setAnswer(response.answer);
            onAnswered(response.answer);
        } catch {
            setFailure({
                ok: false,
                kind: "temporary",
                message: "Something went wrong. Please try again.",
            });
        } finally {
            inFlight.current = false;
            setIsSubmitting(false);
        }
    }

    return (
        <section className={styles.stage} aria-busy={isSubmitting || isAdvancing}>
            <div className={styles.progressRow}>
                <p className={styles.progressLabel}>
                    Question {question.number} of {question.total}
                </p>
                <p className={styles.scoreLabel}>
                    <span className={styles.scoreValue}>{shownScore}</span> points
                </p>
            </div>

            <progress
                className={styles.progress}
                max={question.total}
                value={answer ? answer.answered : question.number - 1}
                aria-label="Match progress"
            />

            <p className={styles.tags}>
                {categoryLabel(question.questionCategory)} · {difficultyLabel(question.difficulty)}
            </p>

            <h2 ref={promptRef} tabIndex={-1} className={styles.prompt}>
                {question.prompt}
            </h2>

            <div className={styles.options} role="group" aria-label="Answer choices">
                {question.options.map((option) => {
                    const isCorrect = answer?.correctOptionId === option.id;
                    const isSelected = answer?.submittedOptionId === option.id;
                    let optionClass = styles.option;
                    let note: string | null = null;

                    if (answer && isCorrect) {
                        optionClass += ` ${styles.correct}`;
                        note = isSelected ? "Your answer, correct" : "Correct answer";
                    } else if (answer && isSelected) {
                        optionClass += ` ${styles.incorrect}`;
                        note = "Your answer, incorrect";
                    }

                    return (
                        <button
                            key={option.id}
                            type="button"
                            className={optionClass}
                            onClick={() => handleAnswer(option.id)}
                            disabled={isLocked}
                        >
                            <span>
                                {option.text}
                                {note && <span className={styles.srOnly}>. {note}</span>}
                            </span>
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
                <div role="alert" className={styles.error}>
                    <p>{failure.message}</p>
                    {!answer && advanceError && <p>{advanceError}</p>}
                    {failure.kind === "stale" && (
                        <Button
                            variant="secondary"
                            className={styles.inlineAction}
                            onClick={() => onContinue(false)}
                            disabled={isAdvancing}
                        >
                            Continue
                        </Button>
                    )}
                    {failure.kind === "terminal" && (
                        <Button
                            variant="secondary"
                            className={styles.inlineAction}
                            onClick={onRestart}
                        >
                            Start a new match
                        </Button>
                    )}
                </div>
            )}

            {answer && (
                <div className={styles.feedback} aria-live="polite">
                    <h3
                        className={answer.correct ? styles.feedbackCorrect : styles.feedbackIncorrect}
                    >
                        {answer.correct
                            ? `Correct! +${answer.pointsAwarded} points`
                            : "Not quite!"}
                    </h3>
                    <p>{answer.explanation}</p>

                    {isAdvancing && (
                        <p role="status" className={styles.status}>
                            {isLast ? "Tallying your score..." : "Preparing the next question..."}
                        </p>
                    )}

                    {advanceError && (
                        <p role="alert" className={styles.error}>
                            {advanceError}
                        </p>
                    )}

                    <Button
                        ref={continueRef}
                        className={styles.next}
                        stretch="mobile"
                        onClick={() => onContinue(answer.finished)}
                        disabled={isAdvancing}
                    >
                        {isAdvancing
                            ? "Loading..."
                            : advanceError
                                ? "Try again"
                                : answer.finished
                                    ? "See your results"
                                    : "Next question"}
                    </Button>
                </div>
            )}
        </section>
    );
}
