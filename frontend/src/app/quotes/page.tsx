import { Suspense } from "react";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import QuotesContent from "./QuotesContent";
import { pageMetadata } from "@/lib/seo/site";
import QuotesFrame from "./QuotesFrame";
import styles from "./page.module.css";

// The canonical is /quotes for every ?page=N too: the metadata is static so the page stays
// prerenderable, and the paginated views are not meant to be separate search results.
export const metadata = pageMetadata({
    title: "Quotes",
    description:
        "Browse memorable lines spoken in the Middle-earth films, page by page, each with the character who said it.",
    path: "/quotes",
});

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