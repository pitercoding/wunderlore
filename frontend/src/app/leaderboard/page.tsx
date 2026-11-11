import { Suspense } from "react";
import QuotesFrame from "@/app/quotes/QuotesFrame";
import { pageMetadata } from "@/lib/seo/site";
import { resolveSelection, type LeaderboardSelection } from "@/lib/ranking/selection";
import LeaderboardContent, { DESCRIPTION } from "./LeaderboardContent";
import LeaderboardLoading, { LeaderboardLoadingShell } from "./LeaderboardLoading";

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

function LeaderboardFallback({ selection }: { selection: LeaderboardSelection }) {
    return (
        <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
            <LeaderboardLoading selection={selection} />
        </QuotesFrame>
    );
}

// Reading searchParams is request-time work, so it happens inside a Suspense boundary and the
// prerendered shell cannot know the selection (it shows neutral placeholders). As soon as the request is
// handled, this resolves the selection once, on the server, and hands the same value to the loading
// state and to the content: the right filters and skeleton appear without waiting for the ranking
// and without any client-side read of the URL.
async function LeaderboardRoute({ searchParams }: LeaderboardPageProps) {
    const selection = resolveSelection(await searchParams);

    return (
        <Suspense fallback={<LeaderboardFallback selection={selection} />}>
            <LeaderboardContent selection={selection} />
        </Suspense>
    );
}

export default function LeaderboardPage({ searchParams }: LeaderboardPageProps) {
    return (
        <Suspense
            fallback={
                <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
                    <LeaderboardLoadingShell />
                </QuotesFrame>
            }
        >
            <LeaderboardRoute searchParams={searchParams} />
        </Suspense>
    );
}
