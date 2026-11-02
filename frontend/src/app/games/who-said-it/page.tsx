import { connection } from "next/server";
import { Suspense } from "react";
import { createRound } from "@/lib/api/whoSaidIt";
import { unavailableMessage } from "@/lib/api/unavailable";
import RetryButton from "../../RetryButton";
import WhoSaidItContent from "./WhoSaidItContent";
import WhoSaidItFrame from "./WhoSaidItFrame";
import styles from "./page.module.css";

function WhoSaidItFallback() {
    return (
        <WhoSaidItFrame title="Who Said It?">
            <p role="status" className={styles.loading}>
                Loading a new round...
            </p>
        </WhoSaidItFrame>
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
            <WhoSaidItFrame title="Who Said It?" compactNav>
                <div className={styles.notice}>
                    <p role="alert" className={styles.noticeText}>
                        {unavailableMessage(error) ??
                            "Unable to load a game round. Please try again."}
                    </p>

                    <RetryButton />
                </div>
            </WhoSaidItFrame>
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
