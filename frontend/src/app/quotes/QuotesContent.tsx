import Link from "next/link";
import { redirect } from "next/navigation";
import { connection } from "next/server";
import { getQuotes } from "@/lib/api/quotes";
import { unavailableMessage } from "@/lib/api/unavailable";
import RetryButton from "../RetryButton";
import styles from "./page.module.css";

interface QuotesPageProps {
    searchParams: Promise<{
        page?: string;
    }>;
}

export default async function QuotesContent({
    searchParams,
}: QuotesPageProps) {
    // Defer to request time so prerendering never starts the quotes fetch.
    await connection();

    const params = await searchParams;
    const requestedPage = Number(params.page ?? "1");

    const page =
        Number.isInteger(requestedPage) && requestedPage >= 1
            ? requestedPage
            : 1;

    let quotePage;

    try {
        quotePage = await getQuotes(page, 20);
    } catch (error) {
        console.error("Failed to load quotes", error);

        return (
            <main className={styles.container}>
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

                <h1>Quotes</h1>
                <p role="alert">
                    {unavailableMessage(error) ??
                        "We couldn't load the quotes. Please try again."}
                </p>

                <RetryButton />
            </main>
        );
    }

    if (quotePage.pages > 0 && quotePage.page > quotePage.pages) {
        redirect(`/quotes?page=${quotePage.pages}`);
    }

    return (
        <main className={styles.container}>
            <header className={styles.header}>
                <Link href="/" className={styles.backLink}>
                    ← Wunderlore
                </Link>

                <p className={styles.eyebrow}>EXPLORE MIDDLE-EARTH</p>
                <h1>Who said it?</h1>
                <p className={styles.description}>
                    Explore memorable quotes from the world of The Lord of the Rings.
                </p>
            </header>

            <section aria-label="Quote catalog" className={styles.quoteList}>
                {quotePage.docs.length === 0 ? (
                    <p>No quotes found on this page.</p>
                ) : (
                    quotePage.docs.map((quote) => (
                        <article key={quote._id} className={styles.quoteCard}>
                            <blockquote>{quote.dialog}</blockquote>

                            <p className={styles.metadata}>
                                {quote.characterName?.trim()
                                    ? quote.characterName
                                    : "Unknown character"}
                            </p>
                        </article>
                    ))
                )}
            </section>

            <nav aria-label="Quote pagination" className={styles.pagination}>
                {quotePage.page > 1 ? (
                    <Link
                        href={`/quotes?page=${quotePage.page - 1}`}
                        className={styles.pageLink}
                    >
                        Previous
                    </Link>
                ) : (
                    <span className={styles.disabledLink} aria-disabled="true">
                        Previous
                    </span>
                )}

                <span>
                    Page {quotePage.page} of {quotePage.pages}
                </span>

                {quotePage.page < quotePage.pages ? (
                    <Link
                        href={`/quotes?page=${quotePage.page + 1}`}
                        className={styles.pageLink}
                    >
                        Next
                    </Link>
                ) : (
                    <span className={styles.disabledLink} aria-disabled="true">
                        Next
                    </span>
                )}
            </nav>

            <p className={styles.total}>
                {quotePage.total} quotes available
            </p>
        </main>
    );
}
