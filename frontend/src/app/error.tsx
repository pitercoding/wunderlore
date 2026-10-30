"use client";

import Link from "next/link";
import styles from "./error.module.css";

interface ErrorPageProps {
    error: unknown;
    retry: () => void;
}

// retry, not reset: reset alone re-renders the same failed server payload.
export default function ErrorPage({ retry }: ErrorPageProps) {
    return (
        <main className={styles.container}>
            <Link href="/" className={styles.backLink}>
                ← Wunderlore
            </Link>

            <h1 className={styles.title}>Something went wrong</h1>
            <p role="alert">An unexpected error occurred. You can try again.</p>

            <button
                type="button"
                className={styles.retry}
                onClick={() => retry()}
            >
                Try again
            </button>
        </main>
    );
}
