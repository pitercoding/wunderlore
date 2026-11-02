import Link from "next/link";
import { redirect } from "next/navigation";
import { connection } from "next/server";
import { getQuotes } from "@/lib/api/quotes";
import { unavailableMessage } from "@/lib/api/unavailable";
import RetryButton from "../RetryButton";
import QuotesFrame from "./QuotesFrame";
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
            <QuotesFrame title="Quotes" compactNav>
                <div className={styles.notice}>
                    <p role="alert" className={styles.noticeText}>
                        {unavailableMessage(error) ??
                            "We couldn't load the quotes. Please try again."}
                    </p>

                    <RetryButton />
                </div>
            </QuotesFrame>
        );
    }

    if (quotePage.pages > 0 && quotePage.page > quotePage.pages) {
        redirect(`/quotes?page=${quotePage.pages}`);
    }

    return (
        <QuotesFrame
            title="Quotes"
            description="Explore memorable quotes from the world of The Lord of the Rings."
        >
            <section aria-label="Quote catalog" className={styles.quoteList}>
                {quotePage.docs.length === 0 ? (
                    <p className={styles.empty}>No quotes found.</p>
                ) : (
                    quotePage.docs.map((quote) => (
                        <article key={quote._id} className={styles.quote}>
                            <blockquote className={styles.text}>{quote.dialog}</blockquote>

                            <p className={styles.metadata}>
                                {quote.characterName?.trim()
                                    ? quote.characterName
                                    : "Unknown character"}
                            </p>
                        </article>
                    ))
                )}
            </section>

            {quotePage.pages > 0 && (
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

                    <span className={styles.pageStatus}>
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
            )}

            <p className={styles.total}>
                {quotePage.total} quotes available
            </p>
        </QuotesFrame>
    );
}
