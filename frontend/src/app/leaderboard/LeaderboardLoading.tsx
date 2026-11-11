import ColdStartHint from "@/components/ColdStartHint/ColdStartHint";
import { divisionLabel, divisionsOf, gameLabel, RANKING_GAMES } from "@/lib/ranking/games";
import type { LeaderboardSelection } from "@/lib/ranking/selection";
import LeaderboardFilters from "./LeaderboardFilters";
import styles from "./page.module.css";

// Placeholders with the footprint of what will arrive: no text, hidden from assistive technology
// (the status line says what is loading). Static blocks under prefers-reduced-motion.
function Block({ className }: { className: string }) {
    return <span aria-hidden="true" className={`${styles.skeleton} ${className}`} />;
}

function SkeletonRows({ count }: { count: number }) {
    return Array.from({ length: count }, (_, index) => (
        <li key={index} className={styles.skeletonRow} aria-hidden="true">
            <Block className={styles.skeletonRank} />
            <Block className={styles.skeletonName} />
            <Block className={styles.skeletonScore} />
        </li>
    ));
}

// The overview: a title and the division cards of each game, like the real sections.
function OverviewSkeleton() {
    return (
        <div className={styles.overview} aria-hidden="true">
            {RANKING_GAMES.map((game) => {
                const divisions = divisionsOf(game.slug);

                return (
                    <div key={game.slug} className={styles.gameSection}>
                        <Block className={styles.skeletonTitle} />
                        <div className={divisions.length === 1 ? styles.divisionsSingle : styles.divisions}>
                            {divisions.map((division) => (
                                <div key={division} className={styles.division}>
                                    <Block className={styles.skeletonHead} />
                                    <ul className={styles.skeletonList}>
                                        <SkeletonRows count={3} />
                                    </ul>
                                </div>
                            ))}
                        </div>
                    </div>
                );
            })}
        </div>
    );
}

// One division: the title and scope line, then a table-like list.
function RankingSkeleton() {
    return (
        <div aria-hidden="true">
            <Block className={styles.skeletonTitle} />
            <Block className={styles.skeletonScope} />
            <div className={styles.tableWrap}>
                <ul className={`${styles.skeletonList} ${styles.skeletonTable}`}>
                    <SkeletonRows count={6} />
                </ul>
            </div>
        </div>
    );
}

function statusText(selection: LeaderboardSelection): string {
    return selection.kind === "division"
        ? `Loading the ${gameLabel(selection.game)} · ${divisionLabel(selection.division)} ranking`
        : "Loading the leaderboard";
}

// What the Suspense fallback shows inside the page frame while the ranking loads. The selection is
// the one the server resolved from the URL (the same one the content gets), so the real filters are
// already in place and the placeholder has the shape of the overview or of one division.
export default function LeaderboardLoading({ selection }: { selection: LeaderboardSelection }) {
    return (
        <>
            <LeaderboardFilters selection={selection} />
            <p role="status" className={styles.srOnly}>
                {statusText(selection)}
            </p>
            {selection.kind === "overview" ? <OverviewSkeleton /> : <RankingSkeleton />}
            <ColdStartHint />
        </>
    );
}

// The prerendered shell, sent before the URL is known: it must not claim a selection, so the
// filters are placeholders of the same height (no current game) and the body is the lighter,
// single-division shape. The request-time fallback above replaces it right after.
export function LeaderboardLoadingShell() {
    return (
        <>
            <div className={styles.filters} aria-hidden="true">
                <div className={styles.skeletonPills}>
                    <Block className={styles.skeletonPill} />
                    <Block className={styles.skeletonPill} />
                    <Block className={styles.skeletonPill} />
                    <Block className={styles.skeletonPill} />
                    <Block className={styles.skeletonPill} />
                </div>
            </div>
            <p role="status" className={styles.srOnly}>
                Loading the leaderboard
            </p>
            <RankingSkeleton />
            <ColdStartHint />
        </>
    );
}
