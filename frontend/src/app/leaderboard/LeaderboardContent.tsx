import Link from "next/link";
import QuotesFrame from "@/app/quotes/QuotesFrame";
import {
    fetchLeaderboard,
    fetchLeaderboardOverview,
    type LeaderboardEntry,
    type LeaderboardOverviewGroup,
    type LeaderboardOverviewResponse,
    type LeaderboardResponse,
} from "@/lib/api/ranking";
import {
    defaultDivisionOf,
    divisionLabel,
    divisionsOf,
    gameLabel,
    OVERVIEW_HREF,
    RANKING_GAMES,
    rankingHref,
    type RankingDivision,
    type RankingGame,
} from "@/lib/ranking/games";
import { resolveSelection, type LeaderboardSelection } from "@/lib/ranking/selection";
import LocalDate from "./LocalDate";
import styles from "./page.module.css";

const DESCRIPTION =
    "Each division competes only with itself. There is no overall ranking. Equal percentages share a position; the earlier game comes first.";

interface LeaderboardContentProps {
    searchParams: Promise<{
        game?: string | string[];
        division?: string | string[];
    }>;
}

function activeGame(selection: LeaderboardSelection): RankingGame | undefined {
    return selection.kind === "division" ? selection.game : undefined;
}

function selectionHref(selection: LeaderboardSelection): string {
    return selection.kind === "division" ? rankingHref(selection.game, selection.division) : OVERVIEW_HREF;
}

// The way back to the overview, and one link per game (to its first division).
function GameFilters({ selection }: { selection: LeaderboardSelection }) {
    const active = activeGame(selection);

    return (
        <nav aria-label="Choose a game" className={styles.filters}>
            <ul className={styles.filterList}>
                <li>
                    <Link
                        href={OVERVIEW_HREF}
                        className={styles.filter}
                        aria-current={active ? undefined : "page"}
                    >
                        Overview
                    </Link>
                </li>
                {RANKING_GAMES.map((game) => (
                    <li key={game.slug}>
                        <Link
                            href={rankingHref(game.slug, defaultDivisionOf(game.slug))}
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

// Only for a game with levels: Who Said It? has the single standard division and no choice.
function DivisionFilters({ game, active }: { game: RankingGame; active: RankingDivision }) {
    const divisions = divisionsOf(game);

    if (divisions.length < 2) {
        return null;
    }

    return (
        <nav aria-label={`Choose a ${gameLabel(game)} division`} className={styles.filters}>
            <ul className={styles.filterList}>
                {divisions.map((division) => (
                    <li key={division}>
                        <Link
                            href={rankingHref(game, division)}
                            className={styles.filter}
                            aria-current={active === division ? "page" : undefined}
                        >
                            {divisionLabel(division)}
                        </Link>
                    </li>
                ))}
            </ul>
        </nav>
    );
}

function Filters({ selection }: { selection: LeaderboardSelection }) {
    return (
        <>
            <GameFilters selection={selection} />
            {selection.kind === "division" && (
                <DivisionFilters game={selection.game} active={selection.division} />
            )}
        </>
    );
}

// What the division is, so a player sees why a result competes where it does.
function scopeNote(game: RankingGame, division: RankingDivision): string {
    return division === "standard"
        ? `${gameLabel(game)} has a single mode, so every match competes together.`
        : `${divisionLabel(division)} results compete only with other ${divisionLabel(division)} games of ${gameLabel(game)}.`;
}

function OverviewEntries({ entries }: { entries: LeaderboardEntry[] }) {
    return (
        <ol className={styles.miniList}>
            {entries.map((entry) => (
                <li
                    // Equal percentages share a position, so the position alone isn't unique.
                    key={`${entry.position}-${entry.nickname}-${entry.playedAt}`}
                    className={styles.miniItem}
                >
                    <span className={styles.miniRank}>{entry.position}</span>
                    <span className={styles.miniPlayer}>{entry.nickname}</span>
                    <span className={styles.miniScore}>{entry.percentage.toFixed(2)}%</span>
                    <span className={styles.miniDate}>
                        <LocalDate iso={entry.playedAt} />
                    </span>
                </li>
            ))}
        </ol>
    );
}

function Overview({ groups }: { groups: LeaderboardOverviewGroup[] }) {
    return (
        <div className={styles.overview}>
            {RANKING_GAMES.map((game) => (
                <section
                    key={game.slug}
                    className={styles.gameSection}
                    aria-labelledby={`overview-${game.slug}`}
                >
                    <h2 id={`overview-${game.slug}`} className={styles.gameTitle}>
                        {game.label}
                    </h2>
                    <div className={styles.divisions}>
                        {divisionsOf(game.slug).map((division) => {
                            const group = groups.find(
                                (candidate) =>
                                    candidate.game === game.slug && candidate.division === division,
                            );
                            const entries = group?.entries ?? [];

                            return (
                                <div key={division} className={styles.division}>
                                    <div className={styles.divisionHead}>
                                        <h3 className={styles.divisionTitle}>{divisionLabel(division)}</h3>
                                        <Link
                                            href={rankingHref(game.slug, division)}
                                            className={styles.fullLink}
                                            aria-label={`${game.label} ${divisionLabel(division)}: full ranking`}
                                        >
                                            Full ranking
                                        </Link>
                                    </div>
                                    {entries.length === 0 ? (
                                        <p className={styles.noResults}>No results yet</p>
                                    ) : (
                                        <OverviewEntries entries={entries} />
                                    )}
                                </div>
                            );
                        })}
                    </div>
                </section>
            ))}
        </div>
    );
}

function Results({ leaderboard }: { leaderboard: LeaderboardResponse }) {
    const { game, division, entries } = leaderboard;
    const title = `${gameLabel(game)} · ${divisionLabel(division)}`;

    return (
        <section aria-labelledby="ranking-title">
            <h2 id="ranking-title" className={styles.rankingTitle}>
                {title}
            </h2>
            <p className={styles.scope}>{scopeNote(game, division)}</p>

            {entries.length === 0 ? (
                <p className={styles.empty}>
                    No {title} results have been published yet. Finish a game and publish your
                    result to be the first.
                </p>
            ) : (
                // Roles are spelled out because the stacked layout on small screens changes the
                // display of the cells, which some browsers treat as dropping the table semantics.
                <div className={styles.tableWrap}>
                    <table className={styles.table} role="table">
                        <caption className={styles.caption}>
                            {title} leaderboard, {entries.length} {entries.length === 1 ? "result" : "results"}
                        </caption>
                        <thead role="rowgroup">
                            <tr role="row">
                                <th scope="col" role="columnheader">Rank</th>
                                <th scope="col" role="columnheader">Player</th>
                                <th scope="col" role="columnheader">Date</th>
                                <th scope="col" role="columnheader" className={styles.numeric}>Score</th>
                            </tr>
                        </thead>
                        <tbody role="rowgroup">
                            {entries.map((entry) => (
                                <tr
                                    // Equal percentages share a position, so the position alone isn't unique.
                                    key={`${entry.position}-${entry.nickname}-${entry.playedAt}`}
                                    role="row"
                                    className={entry.position <= 3 ? styles.podium : undefined}
                                >
                                    <td role="cell" data-label="Rank" className={styles.rank}>
                                        {entry.position}
                                    </td>
                                    <td role="cell" data-label="Player" className={styles.player}>
                                        {entry.nickname}
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
            )}
        </section>
    );
}

// The overview by default (?game= absent or unknown), or the full ranking of one division
// (?game=...&division=...). The URL is reduced to a pair the backend accepts before anything is
// requested. Positions and percentages come from the backend as they are.
export default async function LeaderboardContent({ searchParams }: LeaderboardContentProps) {
    const selection = resolveSelection(await searchParams);

    // Only the data requests are inside the try: the JSX is built after it.
    let overview: LeaderboardOverviewResponse | undefined;
    let ranking: LeaderboardResponse | undefined;

    try {
        if (selection.kind === "overview") {
            overview = await fetchLeaderboardOverview();
        } else {
            ranking = await fetchLeaderboard(selection.game, selection.division);
        }
    } catch (error: unknown) {
        console.error("Failed to load the leaderboard", error);

        return (
            <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
                <Filters selection={selection} />
                <div role="alert" className={styles.error}>
                    <p>The leaderboard couldn&apos;t be loaded right now. Please try again.</p>
                    <Link href={selectionHref(selection)} className={styles.retry}>
                        Try again
                    </Link>
                </div>
            </QuotesFrame>
        );
    }

    return (
        <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
            <Filters selection={selection} />
            {overview && <Overview groups={overview.groups} />}
            {ranking && <Results leaderboard={ranking} />}
        </QuotesFrame>
    );
}
