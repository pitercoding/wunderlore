import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { getRound } from "@/lib/api/game";
import RetryButton from "../RetryButton";
import GameContent from "./GameContent";
import styles from "./page.module.css";

function GameFallback() {
    return (
        <main className={styles.container}>
            <p role="status">Loading a new round...</p>
        </main>
    );
}

async function GameLoader() {
    // Defer to request time so prerendering never starts the round fetch.
    await connection();

    const result = await getRound().then(
        (round) => ({ ok: true as const, round }),
        (error) => ({ ok: false as const, error }),
    );

    if (!result.ok) {
        const { error } = result;
        console.error("Failed to load game round", error);

        return (
            <main className={styles.container}>
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

                <h1>Who Said It?</h1>
                <p role="alert">
                    Unable to load a game round. Please try again.
                </p>

                <RetryButton />
            </main>
        );
    }

    return <GameContent key={result.round.roundId} round={result.round} />;
}

export default function GamePage() {
    return (
        <Suspense fallback={<GameFallback />}>
            <GameLoader />
        </Suspense>
    );
}
