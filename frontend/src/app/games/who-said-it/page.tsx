import { connection } from "next/server";
import { Suspense } from "react";
import BackLink from "@/components/BackLink/BackLink";
import PageShell from "@/components/PageShell/PageShell";
import { createRound } from "@/lib/api/whoSaidIt";
import { unavailableMessage } from "@/lib/api/unavailable";
import RetryButton from "../../RetryButton";
import WhoSaidItContent from "./WhoSaidItContent";
import styles from "./page.module.css";

function WhoSaidItFallback() {
    return (
        <PageShell>
            <p role="status" className={styles.loading}>
                Loading a new round...
            </p>
        </PageShell>
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
            <PageShell>
                <BackLink />

                <h1 className={styles.errorTitle}>Who Said It?</h1>
                <p role="alert" className={styles.error}>
                    {unavailableMessage(error) ??
                        "Unable to load a game round. Please try again."}
                </p>

                <RetryButton />
            </PageShell>
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
