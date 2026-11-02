"use client";

import BackLink from "@/components/BackLink/BackLink";
import Button from "@/components/Button/Button";
import PageShell from "@/components/PageShell/PageShell";
import styles from "./error.module.css";

interface ErrorPageProps {
    error: unknown;
    retry: () => void;
}

// retry, not reset: reset alone re-renders the same failed server payload.
export default function ErrorPage({ retry }: ErrorPageProps) {
    return (
        <PageShell>
            <BackLink />

            <h1 className={styles.title}>Something went wrong</h1>
            <p role="alert">An unexpected error occurred. You can try again.</p>

            <Button
                variant="secondary"
                stretch="mobile"
                className={styles.retry}
                onClick={() => retry()}
            >
                Try again
            </Button>
        </PageShell>
    );
}
