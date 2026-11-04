import { connection } from "next/server";
import { Suspense } from "react";
import { getQuestions } from "@/lib/api/whichCharacter";
import { unavailableMessage } from "@/lib/api/unavailable";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import { WHICH_CHARACTER_IMAGE, pageMetadata } from "@/lib/seo/site";
import RetryButton from "../../RetryButton";
import WhichCharacterContent from "./WhichCharacterContent";
import WhichCharacterFrame from "./WhichCharacterFrame";
import styles from "./page.module.css";

export const metadata = pageMetadata({
    title: "Which Character Are You?",
    description:
        "Answer a few questions about how you think and choose, and follow the clues to the Tolkien character you most resemble.",
    path: "/games/which-character",
    image: WHICH_CHARACTER_IMAGE,
});

function WhichCharacterFallback() {
    return (
        <WhichCharacterFrame title="Which Character Are You?">
            <p role="status" className={styles.loading}>
                Loading the questions...
            </p>
            <ColdStartHint />
        </WhichCharacterFrame>
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
            <WhichCharacterFrame title="Which Character Are You?" compactNav>
                <div className={styles.notice}>
                    <p role="alert" className={styles.noticeText}>
                        {unavailableMessage(error) ??
                            "Unable to load the quiz. Please try again."}
                    </p>

                    <RetryButton />
                </div>
            </WhichCharacterFrame>
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
