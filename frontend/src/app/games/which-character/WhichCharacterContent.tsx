"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import type {
    WhichCharacterAnswer,
    WhichCharacterProfile,
    WhichCharacterQuestion,
} from "@/lib/api/whichCharacter";
import {
    submitWhichCharacterAnswers,
    type WhichCharacterSubmitFailure,
} from "./actions";
import styles from "./page.module.css";

interface WhichCharacterContentProps {
    questions: WhichCharacterQuestion[];
}

export default function WhichCharacterContent({
    questions,
}: WhichCharacterContentProps) {
    // questionId -> answerId. The only thing the client knows about the outcome.
    const [selected, setSelected] = useState<Record<string, string>>({});
    const [index, setIndex] = useState(0);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [character, setCharacter] = useState<WhichCharacterProfile | null>(null);
    const [failure, setFailure] = useState<WhichCharacterSubmitFailure | null>(null);

    // State updates are asynchronous, so a double click could pass the isSubmitting check twice.
    const inFlight = useRef(false);
    const resultHeading = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        if (character) {
            resultHeading.current?.focus();
        }
    }, [character]);

    const total = questions.length;
    const question = questions[index];
    const selectedAnswerId = selected[question.id];
    const isLast = index === total - 1;
    const answeredCount = questions.filter((q) => selected[q.id]).length;
    const isComplete = answeredCount === total;

    function handleSelect(answerId: string) {
        if (isSubmitting) {
            return;
        }

        setSelected((current) => ({ ...current, [question.id]: answerId }));
        setFailure(null);
    }

    function handlePrevious() {
        setFailure(null);
        setIndex((current) => Math.max(0, current - 1));
    }

    function handleNext() {
        if (!selectedAnswerId) {
            return;
        }

        setIndex((current) => Math.min(total - 1, current + 1));
    }

    async function handleFinish() {
        if (inFlight.current || !isComplete) {
            return;
        }

        inFlight.current = true;
        setIsSubmitting(true);
        setFailure(null);

        // Built field by field: nothing but the two ids leaves the client.
        const answers: WhichCharacterAnswer[] = questions.map((q) => ({
            questionId: q.id,
            answerId: selected[q.id],
        }));

        try {
            const response = await submitWhichCharacterAnswers(answers);

            if (!response.ok) {
                setFailure(response);
                return;
            }

            setCharacter(response.character);
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

    function handlePlayAgain() {
        setSelected({});
        setIndex(0);
        setCharacter(null);
        setFailure(null);
    }

    if (character) {
        return (
            <main className={styles.container}>
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

                <section className={styles.card} aria-labelledby="result-title">
                    <p className={styles.eyebrow}>Quiz complete</p>
                    <p className={styles.resultLead}>Your journey ends. You are…</p>
                    <h1
                        id="result-title"
                        ref={resultHeading}
                        tabIndex={-1}
                        className={styles.resultName}
                    >
                        {character.name}
                    </h1>
                    <div className={styles.divider} aria-hidden="true" />
                    <p className={styles.resultDescription}>{character.description}</p>

                    <button
                        type="button"
                        className={styles.primary}
                        onClick={handlePlayAgain}
                    >
                        Play again
                    </button>
                </section>
            </main>
        );
    }

    const legendId = `question-${question.id}`;

    return (
        <main className={styles.container}>
            <header className={styles.header}>
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

                <p className={styles.eyebrow}>Which Character Are You?</p>
                <h1>Find your place in the tale</h1>
            </header>

            <section className={styles.card} aria-busy={isSubmitting}>
                <div className={styles.progress}>
                    <p className={styles.counter}>
                        Question {index + 1} of {total}
                    </p>
                    <progress
                        className={styles.bar}
                        value={index + 1}
                        max={total}
                        aria-label="Quiz progress"
                    />
                </div>

                <fieldset className={styles.fieldset} disabled={isSubmitting}>
                    <legend id={legendId} className={styles.question}>
                        {question.text}
                    </legend>

                    <div className={styles.options}>
                        {question.options.map((option) => (
                            <label
                                key={option.id}
                                className={`${styles.option} ${
                                    selectedAnswerId === option.id ? styles.selected : ""
                                }`}
                            >
                                <input
                                    type="radio"
                                    name={legendId}
                                    value={option.id}
                                    className={styles.radio}
                                    checked={selectedAnswerId === option.id}
                                    onChange={() => handleSelect(option.id)}
                                />
                                <span>{option.text}</span>
                            </label>
                        ))}
                    </div>
                </fieldset>

                {isSubmitting && (
                    <p role="status" className={styles.status}>
                        Consulting the chronicles...
                    </p>
                )}

                {failure && (
                    <p role="alert" className={styles.error}>
                        {failure.message}
                    </p>
                )}

                <div className={styles.nav}>
                    <button
                        type="button"
                        className={styles.secondary}
                        onClick={handlePrevious}
                        disabled={index === 0 || isSubmitting}
                    >
                        Previous
                    </button>

                    {isLast ? (
                        <button
                            type="button"
                            className={styles.primary}
                            onClick={handleFinish}
                            disabled={!isComplete || isSubmitting}
                        >
                            {isSubmitting
                                ? "Submitting..."
                                : failure
                                    ? "Try again"
                                    : "See result"}
                        </button>
                    ) : (
                        <button
                            type="button"
                            className={styles.primary}
                            onClick={handleNext}
                            disabled={!selectedAnswerId || isSubmitting}
                        >
                            Next
                        </button>
                    )}
                </div>
            </section>
        </main>
    );
}
