"use client";

import { useId, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import Button from "@/components/Button/Button";
import { publishToLeaderboard } from "@/app/leaderboard/actions";
import type { PublishedEntry } from "@/lib/api/ranking";
import {
    divisionLabel,
    gameLabel,
    rankingHref,
    type RankingDivision,
    type RankingGame,
} from "@/lib/ranking/games";
import {
    NICKNAME_MAX_LENGTH,
    NICKNAME_RULES,
    validateNickname,
} from "@/lib/ranking/nickname";
import { saveNickname, useSavedNickname } from "@/lib/ranking/savedNickname";
import styles from "./PublishResult.module.css";

interface PublishResultProps {
    gameType: RankingGame;
    // The id of the finished game, as the backend issued it. The score is never sent: the backend
    // reads it from the game it kept.
    gameId: string;
    // The division this game competes in, shown to the player (Easy, Medium, Hard, or Standard for
    // Who Said It?). It is never sent: the backend decides it from the game it kept.
    division: RankingDivision;
}

type Phase =
    | { name: "idle" }
    | { name: "editing" }
    | { name: "published"; entry: PublishedEntry };

// Offers to publish a finished game to the leaderboard of its game and division. Optional: ignoring it changes
// nothing. The same block serves the four games' result screens. A game is shown as published
// only once the backend confirmed it, and a failure leaves the form in place to try again.
export default function PublishResult({ gameType, gameId, division }: PublishResultProps) {
    const saved = useSavedNickname();
    // null: the player has not typed, so the saved nickname (if any) is the suggestion.
    const [draft, setDraft] = useState<string | null>(null);
    const [phase, setPhase] = useState<Phase>({ name: "idle" });
    const [isPublishing, setIsPublishing] = useState(false);
    const [validation, setValidation] = useState<string | null>(null);
    const [failure, setFailure] = useState<{ message: string; terminal: boolean } | null>(null);

    // State updates are asynchronous, so a double submit could pass the isPublishing check twice.
    const inFlight = useRef(false);
    const inputId = useId();
    const hintId = useId();
    const errorId = useId();

    const nickname = draft ?? saved;
    const label = `${gameLabel(gameType)} · ${divisionLabel(division)}`;

    async function handleSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();

        if (inFlight.current) {
            return;
        }

        const invalid = validateNickname(nickname);

        setValidation(invalid);

        if (invalid) {
            return;
        }

        inFlight.current = true;
        setIsPublishing(true);
        setFailure(null);

        try {
            const response = await publishToLeaderboard(gameType, gameId, nickname);

            if (response.ok) {
                saveNickname(response.entry.nickname);
                setPhase({ name: "published", entry: response.entry });
                return;
            }

            if (response.kind === "invalid") {
                setValidation(response.message);
                return;
            }

            setFailure({ message: response.message, terminal: response.kind === "terminal" });
        } catch {
            setFailure({
                message:
                    "Your result was not published. Please check your connection and try again.",
                terminal: false,
            });
        } finally {
            inFlight.current = false;
            setIsPublishing(false);
        }
    }

    if (phase.name === "published") {
        const { entry } = phase;

        return (
            <section className={styles.block} aria-labelledby={`${inputId}-title`}>
                <h3 id={`${inputId}-title`} className={styles.title}>
                    Leaderboard
                </h3>
                <p role="status" className={styles.success}>
                    {entry.alreadyPublished
                        ? `This result was already published in ${label} as ${entry.nickname}.`
                        : `Published in ${label} as ${entry.nickname} with ${entry.percentage.toFixed(2)}%.`}
                </p>
                <Link href={rankingHref(gameType, division)} className={styles.link}>
                    View the {label} leaderboard
                </Link>
            </section>
        );
    }

    return (
        <section className={styles.block} aria-labelledby={`${inputId}-title`} aria-busy={isPublishing}>
            <h3 id={`${inputId}-title`} className={styles.title}>
                Leaderboard
            </h3>

            {phase.name === "idle" ? (
                <>
                    <p className={styles.text}>
                        Publish this result to the {label} leaderboard under a nickname. It&apos;s
                        optional.
                    </p>
                    <Button
                        variant="secondary"
                        stretch="mobile"
                        onClick={() => setPhase({ name: "editing" })}
                    >
                        Publish to leaderboard
                    </Button>
                </>
            ) : (
                <form className={styles.form} onSubmit={handleSubmit} noValidate>
                    <label htmlFor={inputId} className={styles.label}>
                        Nickname
                    </label>
                    <input
                        id={inputId}
                        className={styles.input}
                        type="text"
                        name="nickname"
                        value={nickname}
                        onChange={(event) => {
                            setDraft(event.target.value);
                            setValidation(null);
                        }}
                        autoComplete="nickname"
                        autoCapitalize="words"
                        spellCheck={false}
                        maxLength={NICKNAME_MAX_LENGTH * 2}
                        disabled={isPublishing || failure?.terminal}
                        aria-invalid={validation ? true : undefined}
                        aria-describedby={validation ? `${hintId} ${errorId}` : hintId}
                        required
                    />
                    <p id={hintId} className={styles.hint}>
                        {NICKNAME_RULES} It only labels this result; it isn&apos;t an account.
                    </p>

                    {validation && (
                        <p id={errorId} role="alert" className={styles.error}>
                            {validation}
                        </p>
                    )}

                    {isPublishing && (
                        <p role="status" className={styles.status}>
                            Publishing...
                        </p>
                    )}

                    {failure && (
                        <p role="alert" className={styles.error}>
                            {failure.message}
                        </p>
                    )}

                    {!failure?.terminal && (
                        <div className={styles.actions}>
                            <Button type="submit" stretch="mobile" disabled={isPublishing}>
                                {isPublishing ? "Publishing..." : failure ? "Try again" : "Publish"}
                            </Button>
                            <Button
                                variant="ghost"
                                stretch="mobile"
                                disabled={isPublishing}
                                onClick={() => {
                                    setPhase({ name: "idle" });
                                    setValidation(null);
                                    setFailure(null);
                                }}
                            >
                                Cancel
                            </Button>
                        </div>
                    )}
                </form>
            )}
        </section>
    );
}
