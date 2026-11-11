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
import { unavailableMessage } from "@/lib/api/unavailable";
import {
    divisionLabel,
    divisionsOf,
    gameLabel,
    LEADERBOARD_TOP_LIMIT,
    RANKING_GAMES,
    rankingHref,
    type RankingDivision,
    type RankingGame,
} from "@/lib/ranking/games";
import type { LeaderboardSelection } from "@/lib/ranking/selection";
import RetryButton from "../RetryButton";
import LeaderboardFilters from "./LeaderboardFilters";
import LocalDate from "./LocalDate";
import styles from "./page.module.css";

// Also the description of the loading state (see page.tsx), so the title area does not change size.
export const DESCRIPTION =
    "Each division competes only with itself. There is no overall ranking. Equal percentages share a position; the earlier game comes first.";

interface LeaderboardContentProps {
    selection: LeaderboardSelection;
}

// What the division is, so a player sees why a result competes where it does.
function scopeNote(game: RankingGame, division: RankingDivision): string {
    return division === "standard"
        ? `${gameLabel(game)} has a single mode, so every match competes together.`
        : `${divisionLabel(division)} results compete only with other ${divisionLabel(division)} games of ${gameLabel(game)}.`;
}

// Only the visual tier of a position, read from what the backend sent: 1, 2 and 3 get a
// podium emphasis (ties keep it, since they share that position); everything else is plain.
function tierClass(position: number): string | undefined {
    if (position === 1) return styles.tier1;
    if (position === 2) return styles.tier2;
    if (position === 3) return styles.tier3;
    return undefined;
}

// An entry is tied when a neighbour in the list carries the same position. Nothing is
// recomputed: positions stay exactly as received, this only compares them.
function isTied(entries: LeaderboardEntry[], index: number): boolean {
    const position = entries[index].position;

    return entries[index - 1]?.position === position || entries[index + 1]?.position === position;
}

// A discreet "=" drawn by CSS (so it adds no text to the cell); screen readers get the label.
function TieMark() {
    return <span role="img" aria-label="tied" className={styles.tie} />;
}

function OverviewEntries({ entries }: { entries: LeaderboardEntry[] }) {
    return (
        <ol className={styles.miniList}>
            {entries.map((entry, index) => (
                <li
                    // Equal percentages share a position, so the position alone isn't unique.
                    key={`${entry.position}-${entry.nickname}-${entry.playedAt}`}
                    className={[styles.miniItem, tierClass(entry.position)].filter(Boolean).join(" ")}
                >
                    <span className={styles.miniRank}>
                        {entry.position}
                        {isTied(entries, index) && <TieMark />}
                    </span>
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

// One card per division. An empty one stays compact (name, a short note, the way to play); the
// single-division game (Who Said It?) gets a wide card laid out horizontally.
function DivisionCard({
    game,
    division,
    entries,
    wide,
}: {
    game: RankingGame;
    division: RankingDivision;
    entries: LeaderboardEntry[];
    wide: boolean;
}) {
    const label = gameLabel(game);
    const empty = entries.length === 0;
    const className = [styles.division, empty && styles.divisionEmpty, wide && styles.divisionWide]
        .filter(Boolean)
        .join(" ");

    return (
        <div className={className}>
            <div className={styles.divisionHead}>
                <h3 className={styles.divisionTitle}>{divisionLabel(division)}</h3>
                <Link
                    href={rankingHref(game, division)}
                    className={styles.fullLink}
                    aria-label={`${label} ${divisionLabel(division)}: full ranking`}
                >
                    Full ranking
                </Link>
                {wide && <p className={styles.divisionNote}>{scopeNote(game, division)}</p>}
            </div>
            {empty ? (
                <p className={styles.noResults}>
                    <span>No results yet</span>{" "}
                    <Link
                        href={`/games/${game}`}
                        className={styles.playLink}
                        aria-label={`Play ${label}`}
                    >
                        Play
                    </Link>
                </p>
            ) : (
                <OverviewEntries entries={entries} />
            )}
        </div>
    );
}

function Overview({ groups }: { groups: LeaderboardOverviewGroup[] }) {
    return (
        <div className={styles.overview}>
            {RANKING_GAMES.map((game) => {
                const divisions = divisionsOf(game.slug);

                return (
                    <section
                        key={game.slug}
                        className={styles.gameSection}
                        aria-labelledby={`overview-${game.slug}`}
                    >
                        <h2 id={`overview-${game.slug}`} className={styles.gameTitle}>
                            {game.label}
                        </h2>
                        <div className={divisions.length === 1 ? styles.divisionsSingle : styles.divisions}>
                            {divisions.map((division) => {
                                const group = groups.find(
                                    (candidate) =>
                                        candidate.game === game.slug && candidate.division === division,
                                );

                                return (
                                    <DivisionCard
                                        key={division}
                                        game={game.slug}
                                        division={division}
                                        entries={group?.entries ?? []}
                                        wide={divisions.length === 1}
                                    />
                                );
                            })}
                        </div>
                    </section>
                );
            })}
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
                <div className={styles.empty}>
                    <p className={styles.emptyTitle}>
                        No {title} results have been published yet.
                    </p>
                    <p className={styles.emptyText}>
                        Finish a game and publish your result to be the first.
                    </p>
                    <Link href={`/games/${game}`} className={styles.cta}>
                        Play {gameLabel(game)}
                    </Link>
                </div>
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
                                <th scope="col" role="columnheader" className={styles.numeric}>Score</th>
                                <th scope="col" role="columnheader">Date</th>
                            </tr>
                        </thead>
                        <tbody role="rowgroup">
                            {entries.map((entry, index) => (
                                <tr
                                    // Equal percentages share a position, so the position alone isn't unique.
                                    key={`${entry.position}-${entry.nickname}-${entry.playedAt}`}
                                    role="row"
                                    className={tierClass(entry.position)}
                                >
                                    <td role="cell" data-label="Rank" className={styles.rank}>
                                        {entry.position}
                                        {isTied(entries, index) && <TieMark />}
                                    </td>
                                    <td role="cell" data-label="Player" className={styles.player}>
                                        {entry.nickname}
                                    </td>
                                    <td role="cell" data-label="Score" className={styles.numeric}>
                                        {entry.percentage.toFixed(2)}%
                                    </td>
                                    <td role="cell" data-label="Date" className={styles.date}>
                                        <LocalDate iso={entry.playedAt} />
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            )}
            {entries.length >= LEADERBOARD_TOP_LIMIT && (
                <p className={styles.limitNote}>Showing the top {LEADERBOARD_TOP_LIMIT} results.</p>
            )}
        </section>
    );
}

// The overview, or the full ranking of one division. The selection (see resolveSelection) is a pair
// the backend accepts. Positions and percentages come from the backend as they are.
export default async function LeaderboardContent({ selection }: LeaderboardContentProps) {
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

        // Only a temporary outage (503, timeout, network) is told apart; anything else, an invalid
        // payload included, gets the generic message. Neither shows the backend's own text.
        const unavailable = unavailableMessage(error) !== undefined;

        return (
            <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
                <LeaderboardFilters selection={selection} />
                <div className={styles.error}>
                    <div role="alert">
                        <p className={styles.errorTitle}>
                            {unavailable
                                ? "The leaderboard is taking a while to answer."
                                : "The leaderboard couldn't be loaded right now."}
                        </p>
                        <p>
                            {unavailable
                                ? "The service may be waking up after a quiet period, which can take a little while. Please try again in a moment."
                                : "The results could not be fetched, so none are shown. Please try again in a moment."}
                        </p>
                    </div>
                    {/* refresh keeps the URL, so the game and the division are kept. */}
                    <RetryButton />
                </div>
            </QuotesFrame>
        );
    }

    return (
        <QuotesFrame scene="dragon" title="Leaderboard" description={DESCRIPTION}>
            <LeaderboardFilters selection={selection} />
            {overview && <Overview groups={overview.groups} />}
            {ranking && <Results leaderboard={ranking} />}
        </QuotesFrame>
    );
}
