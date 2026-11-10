import Link from "next/link";
import QuotesFrame from "@/app/quotes/QuotesFrame";
import styles from "./not-found.module.css";

// Shown for any address that leads nowhere. It wears the same forest scene and navigation as the
// pages around it, so a wrong turn still feels like part of the world, and the way back is plain.
export default function NotFound() {
    return (
        <QuotesFrame
            title="Page not found"
            description="The road you followed is not on the map. Return to the start and choose another path."
        >
            <p className={styles.actions}>
                <Link href="/" className={styles.home}>
                    Back to the home page
                </Link>
            </p>
        </QuotesFrame>
    );
}
