import { Suspense } from "react";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
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
            <ColdStartHint />
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