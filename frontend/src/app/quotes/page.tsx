import { Suspense } from "react";
import QuotesContent from "./QuotesContent";
import QuotesFrame from "./QuotesFrame";
import styles from "./page.module.css";

interface QuotesPageProps {
    searchParams: Promise<{
        page?: string;
    }>;
}

function QuotesFallback() {
    return (
        <QuotesFrame title="Quotes">
            <p className={styles.loading} role="status">
                Loading quotes...
            </p>
        </QuotesFrame>
    );
}

export default function QuotesPage({
    searchParams,
}: QuotesPageProps) {
    return (
        <Suspense fallback={<QuotesFallback />}>
            <QuotesContent searchParams={searchParams} />
        </Suspense>
    );
}