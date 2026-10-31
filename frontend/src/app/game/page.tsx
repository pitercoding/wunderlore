import Link from "next/link";
import { connection } from "next/server";
import { Suspense } from "react";
import { createRound } from "@/lib/api/whoSaidIt";
import { unavailableMessage } from "@/lib/api/unavailable";
import RetryButton from "../RetryButton";
import WhoSaidItContent from "./WhoSaidItContent";
import styles from "./page.module.css";

function WhoSaidItFallback() {
    return (
        <main className={styles.container}>
            <p role="status">Loading a new round...</p>
        </main>
    );
}

async function WhoSaidItLoader() {
    // Defer to request time so prerendering never starts the round fetch.
    await connection();

    const result = await createRound().then(
        (round) => ({ ok: true as const, round }),
        (error) => ({ ok: false as const, error }),
    );

    if (!result.ok) {
        const { error } = result;
        console.error("Failed to load Who Said It round", error);

        return (
            <main className={styles.container}>
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

                <h1>Who Said It?</h1>
                <p role="alert">
                    {unavailableMessage(error) ??
                        "Unable to load a game round. Please try again."}
                </p>

                <RetryButton />
            </main>
        );
    }

    return <WhoSaidItContent key={result.round.roundId} round={result.round} />;
}

export default function WhoSaidItPage() {
    return (
        <Suspense fallback={<WhoSaidItFallback />}>
            <WhoSaidItLoader />
        </Suspense>
    );
}
