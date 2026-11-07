"use client";

import { useEffect, useRef, useState } from "react";
import Button from "@/components/Button/Button";
import PublishResult from "@/components/PublishResult/PublishResult";
import type { WhoSaidItMatchQuestion, WhoSaidItMatchResult } from "@/lib/api/whoSaidItMatch";
import { answerWhoSaidItMatch, fetchWhoSaidItMatchState } from "./matchActions";
import styles from "./page.module.css";

interface WhoSaidItMatchProps {
    // The first question, as the backend returned it when the match was created.
    initial: WhoSaidItMatchQuestion;
    // Starts another match; creation (and its failure) belongs to the game.
    onNewMatch: () => void;
    isCreating: boolean;
    creationError: string | null;
    // Back to the first screen.
    onExit: () => void;
}

type Screen =
    | { name: "question"; question: WhoSaidItMatchQuestion }
    | { name: "result"; result: WhoSaidItMatchResult }
    | { name: "expired"; message: string };

// A competitive match. The backend owns the sequence, the answers and the score: this component
// sends the chosen character for the question the server last showed, and renders whatever
// comes back. It never learns which option is right until the match is over.
export default function WhoSaidItMatch({
    initial,
    onNewMatch,
    isCreating,
    creationError,
    onExit,
}: WhoSaidItMatchProps) {
    const [screen, setScreen] = useState<Screen>({ name: "question", question: initial });
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [notice, setNotice] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    // State updates are asynchronous, so a double click could pass the isSubmitting check twice.
    const inFlight = useRef(false);

    async function handleAnswer(question: WhoSaidItMatchQuestion, characterId: string) {
        if (inFlight.current) {
            return;
        }

        inFlight.current = true;
        setIsSubmitting(true);
        setSelectedId(characterId);
        setError(null);
        setNotice(null);

        try {
            const response = await answerWhoSaidItMatch(
                question.matchId,
                question.questionNumber,
                characterId,
            );

            if (response.ok) {
                const { answer } = response;

                setSelectedId(null);
                setScreen(
                    answer.matchFinished && answer.result
                        ? { name: "result", result: answer.result }
                        : { name: "question", question: answer.next! },
                );
                return;
            }

            if (response.kind === "terminal") {
                setScreen({ name: "expired", message: response.message });
                return;
            }

            if (response.kind === "stale") {
                // The server and the page disagree about where the match is: believe the server.
                const state = await fetchWhoSaidItMatchState(question.matchId);

                if (state.ok) {
                    setSelectedId(null);
                    setNotice(response.message);
                    setScreen(
                        "result" in state
                            ? { name: "result", result: state.result }
                            : { name: "question", question: state.question },
                    );
                } else if (state.terminal) {
                    setScreen({ name: "expired", message: state.message });
                } else {
                    setSelectedId(null);
                    setError(state.message);
                }
                return;
            }

            // Invalid or temporary: the answer did not count, or sending it again is safe.
            setSelectedId(null);
            setError(response.message);
        } catch {
            setSelectedId(null);
            setError("Something went wrong. Please try again.");
        } finally {
            inFlight.current = false;
            setIsSubmitting(false);
        }
    }

    if (screen.name === "result") {
        return (
            <MatchResult
                result={screen.result}
                notice={notice}
                onNewMatch={onNewMatch}
                isCreating={isCreating}
                creationError={creationError}
                onExit={onExit}
            />
        );
    }

    if (screen.name === "expired") {
        return (
            <section className={`${styles.stage} ${styles.intro}`}>
                <p role="alert" className={styles.error}>
                    {screen.message}
                </p>
                <div className={styles.matchActions}>
                    <Button stretch="mobile" onClick={onNewMatch} disabled={isCreating}>
                        {isCreating ? "Loading..." : "Start a new match"}
                    </Button>
                    <Button variant="secondary" stretch="mobile" onClick={onExit}>
                        Back
                    </Button>
                </div>
                {creationError && (
                    <p role="alert" className={styles.error}>
                        {creationError}
                    </p>
                )}
            </section>
        );
    }

    return (
        <MatchQuestion
            // A fresh question starts with fresh focus.
            key={`${screen.question.matchId}-${screen.question.questionNumber}`}
            question={screen.question}
            selectedId={selectedId}
            isSubmitting={isSubmitting}
            notice={notice}
            error={error}
            onAnswer={(characterId) => handleAnswer(screen.question, characterId)}
        />
    );
}

interface MatchQuestionProps {
    question: WhoSaidItMatchQuestion;
    selectedId: string | null;
    isSubmitting: boolean;
    notice: string | null;
    error: string | null;
    onAnswer: (characterId: string) => void;
}

function MatchQuestion({
    question,
    selectedId,
    isSubmitting,
    notice,
    error,
    onAnswer,
}: MatchQuestionProps) {
    // The button that was clicked is gone, so focus would be lost: land on the quote.
    const quoteRef = useRef<HTMLQuoteElement>(null);

    useEffect(() => {
        quoteRef.current?.focus();
    }, []);

    return (
        <section className={styles.stage} aria-busy={isSubmitting}>
            <p className={styles.progress}>
                Question {question.questionNumber} of {question.totalQuestions}
            </p>
            <div className={styles.progressTrack} aria-hidden="true">
                <div
                    className={styles.progressFill}
                    style={{
                        width: `${((question.questionNumber - 1) / question.totalQuestions) * 100}%`,
                    }}
                />
            </div>

            <blockquote ref={quoteRef} tabIndex={-1} className={styles.quote}>
                “{question.quote}”
            </blockquote>

            <div className={styles.options} role="group" aria-label="Answer choices">
                {question.options.map((option) => (
                    <button
                        key={option.id}
                        type="button"
                        className={
                            selectedId === option.id
                                ? `${styles.option} ${styles.selected}`
                                : styles.option
                        }
                        aria-pressed={selectedId === option.id}
                        onClick={() => onAnswer(option.id)}
                        disabled={isSubmitting}
                    >
                        {option.name}
                    </button>
                ))}
            </div>

            {isSubmitting && (
                <p role="status" className={styles.status}>
                    Sending your answer...
                </p>
            )}

            {notice && (
                <p role="status" className={styles.status}>
                    {notice}
                </p>
            )}

            {error && (
                <p role="alert" className={styles.error}>
                    {error} Choose an option to try again.
                </p>
            )}
        </section>
    );
}

interface MatchResultProps {
    result: WhoSaidItMatchResult;
    notice: string | null;
    onNewMatch: () => void;
    isCreating: boolean;
    creationError: string | null;
    onExit: () => void;
}

function MatchResult({
    result,
    notice,
    onNewMatch,
    isCreating,
    creationError,
    onExit,
}: MatchResultProps) {
    // The button that was clicked is gone, so focus would be lost: land on the title.
    const titleRef = useRef<HTMLHeadingElement>(null);

    useEffect(() => {
        titleRef.current?.focus();
    }, []);

    return (
        <section className={styles.stage} aria-busy={isCreating}>
            <h2 ref={titleRef} tabIndex={-1} className={styles.verdict}>
                Match complete
            </h2>

            <p className={styles.totalScore}>
                <span className={styles.totalScoreValue}>{result.score}</span>
                <span className={styles.totalScoreMax}> / {result.maxScore} points</span>
            </p>

            <dl className={styles.stats}>
                <div className={styles.stat}>
                    <dt>Performance</dt>
                    <dd>{result.percentage.toFixed(2)}%</dd>
                </div>
                <div className={styles.stat}>
                    <dt>Correct</dt>
                    <dd>
                        {result.correctAnswers} of {result.totalQuestions}
                    </dd>
                </div>
                <div className={styles.stat}>
                    <dt>Incorrect</dt>
                    <dd>{result.incorrectAnswers}</dd>
                </div>
            </dl>

            {notice && (
                <p role="status" className={styles.status}>
                    {notice}
                </p>
            )}

            <h3 className={styles.sectionTitle}>Quote by quote</h3>
            <ol className={styles.summary}>
                {result.questions.map((question) => (
                    <li key={question.questionNumber} className={styles.summaryRow}>
                        <span className={styles.summaryNumber}>{question.questionNumber}</span>
                        <span className={styles.summaryBody}>
                            <span className={styles.summaryQuote}>“{question.quote}”</span>
                            <span className={styles.summaryDetail}>
                                {question.correct
                                    ? `${question.correctCharacterName}`
                                    : `You chose ${question.submittedCharacterName}. It was ${question.correctCharacterName}.`}
                            </span>
                        </span>
                        <span className={question.correct ? styles.summaryRight : styles.summaryWrong}>
                            {question.correct ? "Correct" : "Missed"}
                        </span>
                    </li>
                ))}
            </ol>

            <PublishResult gameType="who-said-it" gameId={result.matchId} />

            {isCreating && (
                <p role="status" className={styles.status}>
                    Preparing your match...
                </p>
            )}

            {creationError && (
                <p role="alert" className={styles.error}>
                    {creationError}
                </p>
            )}

            <div className={styles.matchActions}>
                <Button stretch="mobile" onClick={onNewMatch} disabled={isCreating}>
                    {isCreating ? "Loading..." : creationError ? "Try again" : "Play another match"}
                </Button>
                <Button variant="secondary" stretch="mobile" onClick={onExit} disabled={isCreating}>
                    Back
                </Button>
            </div>
        </section>
    );
}
