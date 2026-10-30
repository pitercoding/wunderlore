"use client";

import { useTransition } from "react";
import { useRouter } from "next/navigation";
import styles from "./RetryButton.module.css";

// refresh keeps the current URL, so a failed page is retried with its own query.
export default function RetryButton() {
    const router = useRouter();
    const [isPending, startTransition] = useTransition();

    return (
        <button
            type="button"
            className={styles.retry}
            disabled={isPending}
            onClick={() => startTransition(() => router.refresh())}
        >
            {isPending ? "Trying again..." : "Try again"}
        </button>
    );
}
