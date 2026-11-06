"use client";

import { useEffect, useRef, useState } from "react";
import Button from "@/components/Button/Button";
import type { WhoAmIAnswer, WhoAmIOption, WhoAmIRound } from "@/lib/api/whoAmI";
import { submitWhoAmIAnswer, type WhoAmIFailure } from "./actions";
import { difficultyLabel } from "./labels";
import styles from "./page.module.css";

interface WhoAmIPlayProps {
    round: WhoAmIRound;
    // Called once, when the round ends (identified, or the fifth clue answered wrongly).
    onRoundEnded: (answer: WhoAmIAnswer) => void;
    // Asks the game for what comes next: the next character, or the result after the last one.
    onContinue: () => void;
    // Asks the game to resynchronize with the server after a 409.
    onResync: () => void;
    onRestart: () => void;
    isAdvancing: boolean;
    advanceError: string | null;
}

// What the page knows of the round while it is played: the clues and options the server has
// shown so far. Nothing else exists on the page, so there is nothing to inspect.
interface RoundView {
    clueNumber: number;
    clues: string[];
    options: WhoAmIOption[];
}

// One round. The game keys it by game and round, so each character starts with a fresh state.
export default function WhoAmIPlay({
    round,
    onRoundEnded,
    onContinue,
    onResync,
    onRestart,
    isAdvancing,
    advanceError,
}: WhoAmIPlayProps) {
    const [view, setView] = useState<RoundView>({
        clueNumber: round.clueNumber,
        clues: round.clues,
        options: round.options,
    });
    const [score, setScore] = useState(round.score);
    const [ruledOut, setRuledOut] = useState<string[]>([]);
    const [notice, setNotice] = useState<string | null>(null);
    const [ended, setEnded] = useState<WhoAmIAnswer | null>(null);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [failure, setFailure] = useState<WhoAmIFailure | null>(null);

    // State updates are asynchronous, so a double click could pass the isSubmitting check twice.
    const inFlight = useRef(false);

    // The control that led here is gone, so focus would be lost: land on the round heading.
    const headingRef = useRef<HTMLHeadingElement>(null);
    const latestClueRef = useRef<HTMLLIElement>(null);
    const revealRef = useRef<HTMLHeadingElement>(null);
    const mounted = useRef(false);

    useEffect(() => {
        headingRef.current?.focus();
    }, []);

    // After a wrong answer the buttons are disabled while the answer is checked, which drops
    // focus: move it to the clue that was just revealed.
    useEffect(() => {
        if (!mounted.current) {
            mounted.current = true;
            return;
        }

        latestClueRef.current?.focus();
    }, [view.clueNumber]);

    useEffect(() => {
        if (ended) {
            revealRef.current?.focus();
        }
    }, [ended]);

    const isLocked = isSubmitting || !!ended || failure?.kind === "terminal" || isAdvancing;
    const isLastRound = round.roundNumber === round.totalRounds;

    async function handleGuess(option: WhoAmIOption) {
        if (inFlight.current || isLocked) {
            return;
        }

        inFlight.current = true;
        setIsSubmitting(true);
        setFailure(null);

        try {
            const response = await submitWhoAmIAnswer(
                round.gameId,
                round.roundNumber,
                view.clueNumber,
                option.id,
            );

            if (!response.ok) {
                setFailure(response);
                return;
            }

            const { answer } = response;

            setScore(answer.score);

            if (answer.roundFinished) {
                setNotice(null);
                setEnded(answer);
                onRoundEnded(answer);
            } else if (answer.next) {
                setRuledOut((names) => [...names, option.text]);
                setNotice(
                    `${option.text} is not the one. Clue ${answer.next.clueNumber} is now revealed.`,
                );
                setView(answer.next);
            }
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

    // After the round ends the server shows every clue; until then only the revealed ones.
    const shownClues = ended?.reveal ? ended.reveal.clues : view.clues;
    const pips = Array.from({ length: round.totalClues }, (_, index) => index + 1);
    // The clue the player is on, or the one that ended the round.
    const pipLast = ended ? ended.clueNumber : view.clueNumber;

    return (
        <section className={styles.stage} aria-busy={isSubmitting || isAdvancing}>
            <div className={styles.progressRow}>
                <h2 ref={headingRef} tabIndex={-1} className={styles.roundHeading}>
                    Round {round.roundNumber} of {round.totalRounds}
                </h2>
                <p className={styles.scoreLabel}>
                    <span className={styles.scoreValue}>{score}</span> points
                </p>
            </div>

            <progress
                className={styles.progress}
                max={round.totalRounds}
                value={ended ? round.roundNumber : round.roundNumber - 1}
                aria-label="Game progress"
            />

            <p className={styles.tags}>{difficultyLabel(round.difficulty)}</p>

            <ol className={styles.tracker} aria-label="Clues used">
                {pips.map((number) => {
                    const state =
                        number < pipLast || (ended && number === pipLast)
                            ? "revealed"
                            : number === pipLast
                                ? "current"
                                : "locked";

                    return (
                        <li
                            key={number}
                            className={`${styles.pip} ${styles[`pip_${state}`]}`}
                            aria-current={state === "current" ? "step" : undefined}
                        >
                            <span aria-hidden="true">{number}</span>
                            <span className={styles.srOnly}>
                                Clue {number}, {state === "locked" ? "not yet revealed" : state}
                            </span>
                        </li>
                    );
                })}
            </ol>

            <ol className={styles.clues}>
                {shownClues.map((clue, index) => {
                    const number = index + 1;
                    const isCurrent = !ended && number === view.clueNumber;
                    // Clues the player did not need, shown once the round is over.
                    const isUnused = !!ended && number > ended.clueNumber;
                    let className = styles.clue;

                    if (isCurrent) {
                        className += ` ${styles.clueCurrent}`;
                    } else if (isUnused) {
                        className += ` ${styles.clueUnused}`;
                    }

                    return (
                        <li
                            key={number}
                            ref={isCurrent ? latestClueRef : undefined}
                            tabIndex={isCurrent ? -1 : undefined}
                            className={className}
                        >
                            <span className={styles.clueNumber}>Clue {number}</span>
                            <span className={styles.clueText}>
                                {clue}
                                {isUnused && <span className={styles.srOnly}> (not needed)</span>}
                            </span>
                        </li>
                    );
                })}
            </ol>

            {!ended && (
                <>
                    <p className={styles.question}>Who am I?</p>

                    {ruledOut.length > 0 && (
                        <p className={styles.ruledOut}>
                            Ruled out so far: {ruledOut.join(", ")}
                        </p>
                    )}

                    <div className={styles.options} role="group" aria-label="Choose a character">
                        {view.options.map((option) => (
                            <button
                                key={option.id}
                                type="button"
                                className={styles.option}
                                onClick={() => handleGuess(option)}
                                disabled={isLocked}
                            >
                                {option.text}
                            </button>
                        ))}
                    </div>
                </>
            )}

            {isSubmitting && (
                <p role="status" className={styles.status}>
                    Checking your answer...
                </p>
            )}

            {/* Always in the page, so a screen reader announces the text when it appears. */}
            <p role="status" className={styles.notice}>
                {!ended && !isSubmitting ? notice : null}
            </p>

            {failure && (
                <div role="alert" className={styles.error}>
                    <p>{failure.message}</p>
                    {!ended && advanceError && <p>{advanceError}</p>}
                    {failure.kind === "stale" && (
                        <Button
                            variant="secondary"
                            className={styles.inlineAction}
                            onClick={onResync}
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
                            Start a new game
                        </Button>
                    )}
                </div>
            )}

            {ended?.reveal && (
                <div className={styles.reveal} aria-live="polite">
                    <h3
                        ref={revealRef}
                        tabIndex={-1}
                        className={ended.correct ? styles.revealCorrect : styles.revealMissed}
                    >
                        {ended.correct
                            ? `Correct! +${ended.pointsAwarded} points`
                            : "Not identified. This round is worth 0 points."}
                    </h3>
                    <p className={styles.revealName}>
                        {ended.correct ? "You found " : "It was "}
                        <strong>{ended.reveal.characterName}</strong>.
                    </p>

                    {isAdvancing && (
                        <p role="status" className={styles.status}>
                            {isLastRound ? "Tallying your score..." : "Finding the next character..."}
                        </p>
                    )}

                    {advanceError && (
                        <p role="alert" className={styles.error}>
                            {advanceError}
                        </p>
                    )}

                    <Button
                        className={styles.next}
                        stretch="mobile"
                        onClick={onContinue}
                        disabled={isAdvancing}
                    >
                        {isAdvancing
                            ? "Loading..."
                            : advanceError
                                ? "Try again"
                                : ended.gameFinished
                                    ? "See your results"
                                    : "Next character"}
                    </Button>
                </div>
            )}
        </section>
    );
}
