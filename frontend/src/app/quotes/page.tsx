import { Suspense } from "react";
import QuotesContent from "./QuotesContent";

interface QuotesPageProps {
    searchParams: Promise<{
        page?: string;
    }>;
}

function QuotesFallback() {
    return (
        <main>
            <h1>Who said it?</h1>
            <p>Loading quotes...</p>
        </main>
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