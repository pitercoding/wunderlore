import Link from "next/link";
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
import type { LeaderboardSelection } from "@/lib/ranking/selection";
import GameFilterList from "./GameFilterList";
import styles from "./page.module.css";

function activeGame(selection: LeaderboardSelection): RankingGame | undefined {
    return selection.kind === "division" ? selection.game : undefined;
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

export default function LeaderboardFilters({ selection }: { selection: LeaderboardSelection }) {
    return (
        <div className={styles.filters}>
            <GameFilters selection={selection} />
            {selection.kind === "division" && (
                <DivisionFilters game={selection.game} active={selection.division} />
            )}
        </div>
    );
}
