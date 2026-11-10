package com.wunderlore.ranking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

import com.wunderlore.ranking.RankingException.Reason;

// Publishes finished games and reads the leaderboard.
//
// The repository is optional: it exists only when wunderlore.persistence.enabled is true. Without
// it, or when the database fails, publishing and reading answer UNAVAILABLE (503) and nothing
// else in the backend is affected. A game counts as published only once the database confirmed
// the insert, because the unique (game_type, game_id) constraint, not a prior check, decides who
// wins when two requests race.
@Service
public class RankingService {

    private static final Logger log = LoggerFactory.getLogger(RankingService.class);

    // How many results the leaderboard shows.
    public static final int TOP_LIMIT = 50;

    // The overview shows the places up to this one in every division; a tie keeps every player.
    public static final int OVERVIEW_POSITIONS = 3;

    static final int MAX_GAME_ID_LENGTH = 64;

    public record Published(RankingEntry entry, boolean created) {
    }

    // One division of one game in the overview, with its first places (empty when nobody played).
    public record OverviewGroup(GameType game, RankingDivision division, List<RankingEntryRepository.Row> entries) {
    }

    private final ObjectProvider<RankingEntryRepository> repositoryProvider;
    private final FinishedGameLookup lookup;

    public RankingService(ObjectProvider<RankingEntryRepository> repositoryProvider, FinishedGameLookup lookup) {
        this.repositoryProvider = repositoryProvider;
        this.lookup = lookup;
    }

    // Publishes the result of a finished game under a nickname. The score is read from the
    // server-side state of the game; the caller only says which game and which nickname.
    // Publishing a game that is already published changes nothing and returns the existing entry
    // (created = false), with the nickname it was first published under.
    public Published publish(String gameSlug, String gameId, String nickname) {
        GameType type = GameType.fromSlug(gameSlug)
                .orElseThrow(() -> new RankingException(Reason.INVALID_GAME));

        if (gameId == null || gameId.isBlank() || gameId.length() > MAX_GAME_ID_LENGTH) {
            throw new RankingException(Reason.INVALID_GAME_ID);
        }

        String normalized = NicknamePolicy.validate(nickname);
        RankingEntryRepository repository = repository();
        FinishedGame game = lookup.find(type, gameId);

        try {
            var existing = repository.findByGameTypeAndGameId(type, gameId);

            if (existing.isPresent()) {
                return new Published(existing.get(), false);
            }

            return new Published(
                    repository.saveAndFlush(new RankingEntry(type, gameId, normalized, game)), true);
        } catch (DataIntegrityViolationException exception) {
            // Lost a race for the same game: the winner's entry is the published one.
            try {
                return repository.findByGameTypeAndGameId(type, gameId)
                        .map(entry -> new Published(entry, false))
                        .orElseThrow(() -> unavailable(exception));
            } catch (DataAccessException | TransactionException again) {
                throw unavailable(again);
            }
        } catch (DataAccessException | TransactionException exception) {
            throw unavailable(exception);
        }
    }

    // The best TOP_LIMIT results of one division of one game. Both are required: results of
    // different games or divisions are never ranked together. The division must exist in the game
    // (RankingDivision decides). Invalid input is refused before the database is touched.
    public List<RankingEntryRepository.Row> top(String gameSlug, String divisionSlug) {
        GameType type = GameType.fromSlug(gameSlug)
                .orElseThrow(() -> new RankingException(Reason.INVALID_GAME));
        RankingDivision division = RankingDivision.fromSlug(divisionSlug)
                .filter(candidate -> candidate.isValidFor(type))
                .orElseThrow(() -> new RankingException(Reason.INVALID_DIVISION));
        RankingEntryRepository repository = repository();

        try {
            return repository.findTopByGameTypeAndDivision(type.name(), division.name(), TOP_LIMIT);
        } catch (DataAccessException | TransactionException exception) {
            throw unavailable(exception);
        }
    }

    // The first places of every division of every game, one group per valid (game, division) pair,
    // in a fixed order: the games as GameType declares them, and for each its divisions as
    // RankingDivision.of lists them. Divisions nobody has played are present with no entries. The
    // groups are never combined: there is no ranking across games or divisions. One database
    // query serves all of them.
    public List<OverviewGroup> overview() {
        RankingEntryRepository repository = repository();
        List<RankingEntryRepository.Row> rows;

        try {
            rows = repository.findOverview(OVERVIEW_POSITIONS);
        } catch (DataAccessException | TransactionException exception) {
            throw unavailable(exception);
        }

        Map<String, List<RankingEntryRepository.Row>> byDivision = new HashMap<>();

        for (RankingEntryRepository.Row row : rows) {
            byDivision.computeIfAbsent(row.getGameType() + "/" + row.getDivision(), key -> new ArrayList<>())
                    .add(row);
        }

        List<OverviewGroup> groups = new ArrayList<>();

        for (GameType game : GameType.values()) {
            for (RankingDivision division : RankingDivision.of(game)) {
                groups.add(new OverviewGroup(
                        game,
                        division,
                        byDivision.getOrDefault(game.name() + "/" + division.name(), List.of())));
            }
        }

        return List.copyOf(groups);
    }

    private RankingEntryRepository repository() {
        RankingEntryRepository repository = repositoryProvider.getIfAvailable();

        if (repository == null) {
            throw new RankingException(Reason.UNAVAILABLE);
        }

        return repository;
    }

    // Only the exception class is logged: driver messages can carry connection details.
    private static RankingException unavailable(Exception exception) {
        log.warn("Leaderboard database failure: {}", exception.getClass().getName());

        return new RankingException(Reason.UNAVAILABLE, exception);
    }
}
