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
    isDivisionOf,
    OVERVIEW_HREF,
    RANKING_GAMES,
    rankingHref,
    type RankingDivision,
    type RankingGame,
} from "@/lib/ranking/games";
import { resolveSelection, type LeaderboardSelection } from "@/lib/ranking/selection";
import GameFilterList from "./GameFilterList";
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

// The division a game link leads to: the current one when the target game has it, otherwise the
// target's first. From the overview there is no current division, so it is the first too.
function divisionForGame(game: RankingGame, selection: LeaderboardSelection): RankingDivision {
    return selection.kind === "division" && isDivisionOf(game, selection.division)
        ? selection.division
        : defaultDivisionOf(game);
}

// Step one: the way back to the overview, and one link per game. These are links, not tabs: each
// one is a page. The row scrolls sideways by itself when the width runs out (the links are all
// focusable, so keyboard focus brings any of them into view).
function GameFilters({ selection }: { selection: LeaderboardSelection }) {
    const active = activeGame(selection);

    return (
        <nav aria-label="Choose a game" className={styles.games}>
            <GameFilterList className={styles.gameList} activeKey={active ?? "overview"}>
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
                            href={rankingHref(game.slug, divisionForGame(game.slug, selection))}
                            className={styles.filter}
                            aria-current={active === game.slug ? "page" : undefined}
                        >
                            {game.label}
                        </Link>
                    </li>
                ))}
            </GameFilterList>
        </nav>
    );
}

// Step two, only for a game with levels: Who Said It? has the single standard division and no
// choice. Smaller and quieter than the games, since it belongs to the selected one.
function DivisionFilters({ game, active }: { game: RankingGame; active: RankingDivision }) {
    const divisions = divisionsOf(game);

    if (divisions.length < 2) {
        return null;
    }

    return (
        <nav aria-label={`Choose a ${gameLabel(game)} division`} className={styles.divisionNav}>
            <ul className={styles.divisionList}>
                {divisions.map((division) => (
                    <li key={division}>
                        <Link
                            href={rankingHref(game, division)}
                            className={styles.divisionFilter}
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
        <div className={styles.filters}>
            <GameFilters selection={selection} />
            {selection.kind === "division" && (
                <DivisionFilters game={selection.game} active={selection.division} />
            )}
        </div>
    );
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
