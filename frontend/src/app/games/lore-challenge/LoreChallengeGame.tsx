"use client";

import { useRef, useState } from "react";
import type {
    LoreChallengeAnswer,
    LoreChallengeCategoryChoice,
    LoreChallengeDifficulty,
    LoreChallengeQuestion,
    LoreChallengeResult,
} from "@/lib/api/loreChallenge";
import {
    fetchLoreChallengeQuestion,
    fetchLoreChallengeResult,
    startLoreChallengeMatch,
} from "./actions";
import LoreChallengeFrame from "./LoreChallengeFrame";
import LoreChallengePlay from "./LoreChallengePlay";
import LoreChallengeSetup from "./LoreChallengeSetup";
import LoreChallengeSummary from "./LoreChallengeSummary";

interface Selection {
    category: LoreChallengeCategoryChoice;
    difficulty: LoreChallengeDifficulty;
}

type Screen =
    | { name: "setup" }
    | { name: "playing"; question: LoreChallengeQuestion; score: number }
    | { name: "result"; result: LoreChallengeResult };

// Owns the screen and the one request that may be in flight. The first screen has asked the
// backend for nothing; a match is created only by the player's own click. The sequence of the
// match belongs to the backend: this component only asks for the question the server says is
// next, or for the result once the server says the match is over.
export default function LoreChallengeGame() {
    const [screen, setScreen] = useState<Screen>({ name: "setup" });
    const [selection, setSelection] = useState<Selection>({ category: "ALL", difficulty: "MEDIUM" });
    const [isBusy, setIsBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);

    // State updates are asynchronous, so a double click could pass the isBusy check twice.
    const inFlight = useRef(false);

    // Runs one request at a time; the rest of the game only reacts to its outcome.
    async function exclusive(task: () => Promise<void>) {
        if (inFlight.current) {
            return;
        }

        inFlight.current = true;
        setIsBusy(true);
        setError(null);

        try {
            await task();
        } catch {
            setError("Something went wrong. Please try again.");
        } finally {
            inFlight.current = false;
            setIsBusy(false);
        }
    }

    function start(category: LoreChallengeCategoryChoice, difficulty: LoreChallengeDifficulty) {
        return exclusive(async () => {
            setSelection({ category, difficulty });

            const response = await startLoreChallengeMatch(category, difficulty);

            if (!response.ok) {
                setError(response.message);
                return;
            }

            setScreen({ name: "playing", question: response.question, score: 0 });
        });
    }

    function handleAnswered(answer: LoreChallengeAnswer) {
        setScreen((current) =>
            current.name === "playing" ? { ...current, score: answer.score } : current,
        );
    }

    // After an answer: the result if the match is over, otherwise the next question. If the
    // server disagrees (409: it is the other way round), ask for the other one instead, so a
    // lost response or a second tab can never leave the player stuck.
    function advance(matchId: string, expectResult: boolean) {
        return exclusive(async () => {
            const loadResult = async () => {
                const response = await fetchLoreChallengeResult(matchId);

                if (response.ok) {
                    setScreen({ name: "result", result: response.result });
                }

                return response;
            };
            const loadQuestion = async () => {
                const response = await fetchLoreChallengeQuestion(matchId);

                if (response.ok) {
                    setScreen((current) => ({
                        name: "playing",
                        question: response.question,
                        score: current.name === "playing" ? current.score : 0,
                    }));
                }

                return response;
            };

            const [first, second] = expectResult
                ? [loadResult, loadQuestion]
                : [loadQuestion, loadResult];
            let response = await first();

            if (!response.ok && response.kind === "stale") {
                response = await second();
            }

            if (!response.ok) {
                setError(response.message);
            }
        });
    }

    function backToSetup() {
        setError(null);
        setScreen({ name: "setup" });
    }

    if (screen.name === "playing") {
        const { question } = screen;

        return (
            <LoreChallengeFrame
                title="Lore Challenge"
                description="Choose the answer you believe the archives hold."
            >
                <LoreChallengePlay
                    key={question.questionId}
                    question={question}
                    score={screen.score}
                    onAnswered={handleAnswered}
                    onContinue={(finished) => advance(question.matchId, finished)}
                    onRestart={backToSetup}
                    isAdvancing={isBusy}
                    advanceError={error}
                />
            </LoreChallengeFrame>
        );
    }

    if (screen.name === "result") {
        return (
            <LoreChallengeFrame
                title="Your results"
                description="Here is what the archives make of your knowledge."
            >
                <LoreChallengeSummary
                    result={screen.result}
                    onPlayAgain={() => start(selection.category, selection.difficulty)}
                    onChangeSelection={backToSetup}
                    isStarting={isBusy}
                    error={error}
                />
            </LoreChallengeFrame>
        );
    }

    return (
        <LoreChallengeFrame
            title="Lore Challenge"
            description="Ten questions on Middle-earth, from easy tales to deep lore."
        >
            <LoreChallengeSetup
                initialCategory={selection.category}
                initialDifficulty={selection.difficulty}
                onStart={start}
                isStarting={isBusy}
                error={error}
            />
        </LoreChallengeFrame>
    );
}
