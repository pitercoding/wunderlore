import Link from "next/link";
import QuotesFrame from "@/app/quotes/QuotesFrame";
import { fetchLeaderboard, type Leaderboard } from "@/lib/api/ranking";
import { gameLabel, isRankingGame, RANKING_GAMES, type RankingGame } from "@/lib/ranking/games";
import LocalDate from "./LocalDate";
import styles from "./page.module.css";

const DESCRIPTION =
    "The best published results across the Wunderlore games, ranked by percentage. Equal percentages share a position; the earlier game comes first.";

interface LeaderboardContentProps {
    searchParams: Promise<{
        game?: string | string[];
    }>;
}

function parseGame(value: string | string[] | undefined): RankingGame | undefined {
    const first = Array.isArray(value) ? value[0] : value;

    return isRankingGame(first) ? first : undefined;
}

function Filters({ active }: { active: RankingGame | undefined }) {
    return (
        <nav aria-label="Filter by game" className={styles.filters}>
            <ul className={styles.filterList}>
                <li>
                    <Link
                        href="/leaderboard"
                        className={styles.filter}
                        aria-current={active ? undefined : "page"}
                    >
                        All games
                    </Link>
                </li>
                {RANKING_GAMES.map((game) => (
                    <li key={game.slug}>
                        <Link
                            href={`/leaderboard?game=${game.slug}`}
                            className={styles.filter}
                            aria-current={active === game.slug ? "page" : undefined}
                        >
                            {game.label}
                        </Link>
                    </li>
                ))}
            </ul>
        </nav>
    );
}

function Results({ leaderboard, active }: { leaderboard: Leaderboard; active: RankingGame | undefined }) {
    if (leaderboard.entries.length === 0) {
        return (
            <p className={styles.empty}>
                {active
                    ? `No ${gameLabel(active)} results have been published yet.`
                    : "No results have been published yet."}{" "}
                Finish a game and publish your result to be the first.
            </p>
        );
    }

    // Roles are spelled out because the stacked layout on small screens changes the display of
    // the cells, which some browsers treat as dropping the table semantics.
    return (
        <div className={styles.tableWrap}>
            <table className={styles.table} role="table">
                <caption className={styles.caption}>
                    {active ? `${gameLabel(active)} leaderboard` : "Global leaderboard"}, top{" "}
                    {leaderboard.entries.length}
                </caption>
                <thead role="rowgroup">
                    <tr role="row">
                        <th scope="col" role="columnheader">Rank</th>
                        <th scope="col" role="columnheader">Player</th>
                        <th scope="col" role="columnheader">Game</th>
                        <th scope="col" role="columnheader">Date</th>
                        <th scope="col" role="columnheader" className={styles.numeric}>Score</th>
                    </tr>
                </thead>
                <tbody role="rowgroup">
                    {leaderboard.entries.map((entry) => (
                        <tr
                            // Equal percentages share a position, so the position alone isn't unique.
                            key={`${entry.position}-${entry.nickname}-${entry.playedAt}-${entry.gameType}`}
                            role="row"
                            className={entry.position <= 3 ? styles.podium : undefined}
                        >
                            <td role="cell" data-label="Rank" className={styles.rank}>
                                {entry.position}
                            </td>
                            <td role="cell" data-label="Player" className={styles.player}>
                                {entry.nickname}
                            </td>
                            <td role="cell" data-label="Game">
                                {gameLabel(entry.gameType)}
                            </td>
                            <td role="cell" data-label="Date">
                                <LocalDate iso={entry.playedAt} />
                            </td>
                            <td role="cell" data-label="Score" className={styles.numeric}>
                                {entry.percentage.toFixed(2)}%
                            </td>
                        </tr>
                    ))}
                </tbody>
            </table>
        </div>
    );
}

// The best 50 results, globally or for one game (?game=). Positions come from the backend and
// are counted inside the filtered set.
export default async function LeaderboardContent({ searchParams }: LeaderboardContentProps) {
    const active = parseGame((await searchParams).game);

    let leaderboard: Leaderboard;

    try {
        leaderboard = await fetchLeaderboard(active);
    } catch (error: unknown) {
        console.error("Failed to load the leaderboard", error);

        return (
            <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
                <Filters active={active} />
                <div role="alert" className={styles.error}>
                    <p>The leaderboard couldn&apos;t be loaded right now. Please try again.</p>
                    <Link
                        href={active ? `/leaderboard?game=${active}` : "/leaderboard"}
                        className={styles.retry}
                    >
                        Try again
                    </Link>
                </div>
            </QuotesFrame>
        );
    }

    return (
        <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
            <Filters active={active} />
            <Results leaderboard={leaderboard} active={active} />
        </QuotesFrame>
    );
}
