import { connection } from "next/server";
import { Suspense } from "react";
import BackLink from "@/components/BackLink/BackLink";
import PageShell from "@/components/PageShell/PageShell";
import { getQuestions } from "@/lib/api/whichCharacter";
import { unavailableMessage } from "@/lib/api/unavailable";
import RetryButton from "../../RetryButton";
import WhichCharacterContent from "./WhichCharacterContent";
import styles from "./page.module.css";

function WhichCharacterFallback() {
    return (
        <PageShell size="narrow">
            <p role="status" className={styles.loading}>
                Loading the questions...
            </p>
        </PageShell>
    );
}

async function WhichCharacterLoader() {
    // Defer to request time so prerendering never starts the questions fetch.
    await connection();

    const result = await getQuestions().then(
        (data) => ({ ok: true as const, questions: data.questions }),
        (error) => ({ ok: false as const, error }),
    );

    if (!result.ok) {
        const { error } = result;
        console.error("Failed to load Which Character Are You questions", error);

        return (
            <PageShell size="narrow">
                <BackLink />

                <h1 className={styles.errorTitle}>Which Character Are You?</h1>
                <p role="alert" className={styles.error}>
                    {unavailableMessage(error) ??
                        "Unable to load the quiz. Please try again."}
                </p>

                <RetryButton />
            </PageShell>
        );
    }

    return <WhichCharacterContent questions={result.questions} />;
}

export default function WhichCharacterPage() {
    return (
        <Suspense fallback={<WhichCharacterFallback />}>
            <WhichCharacterLoader />
        </Suspense>
    );
}
