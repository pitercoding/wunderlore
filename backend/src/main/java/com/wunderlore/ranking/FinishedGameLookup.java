package com.wunderlore.ranking;

// Reads a game's result from the server-side state of that game.
public interface FinishedGameLookup {

    // Throws RankingException GAME_NOT_FOUND (unknown, expired or evicted) or GAME_NOT_FINISHED.
    FinishedGame find(GameType type, String gameId);
}
