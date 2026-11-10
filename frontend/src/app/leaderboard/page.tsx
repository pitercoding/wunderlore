import { Suspense } from "react";
import QuotesFrame from "@/app/quotes/QuotesFrame";
import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import { pageMetadata } from "@/lib/seo/site";
import LeaderboardContent from "./LeaderboardContent";
import styles from "./page.module.css";

// The canonical is /leaderboard for every ?game=&division= too: the metadata is static so the page stays
// cheap, and the filtered views are not meant to be separate search results.
export const metadata = pageMetadata({
    title: "Leaderboard",
    description:
        "The best published results of each Wunderlore game, ranked separately by division: Lore Challenge, Who Am I?, Where in Middle-earth and Who Said It?. Each division competes only with itself.",
    path: "/leaderboard",
});

interface LeaderboardPageProps {
    searchParams: Promise<{
        game?: string | string[];
        division?: string | string[];
    }>;
}

function LeaderboardFallback() {
    return (
        <QuotesFrame scene="dragon" title="Leaderboard">
            <p className={styles.loading} role="status">
                Loading the leaderboard...
            </p>
            <ColdStartHint />
        </QuotesFrame>
    );
}

export default function LeaderboardPage({ searchParams }: LeaderboardPageProps) {
    return (
        <Suspense fallback={<LeaderboardFallback />}>
            <LeaderboardContent searchParams={searchParams} />
        </Suspense>
    );
}
