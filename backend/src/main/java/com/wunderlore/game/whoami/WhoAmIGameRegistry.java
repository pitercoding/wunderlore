package com.wunderlore.game.whoami;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.wunderlore.game.whoami.WhoAmIException.Reason;
import com.wunderlore.game.whoami.WhoAmIGame.AnswerOutcome;
import com.wunderlore.game.whoami.WhoAmIGame.Result;
import com.wunderlore.game.whoami.WhoAmIGame.RoundView;

// Games live in memory for one TTL, counted from their creation, and are lost on restart (game
// state is never persisted, only a published result is stored, and Render Free restarts or
// sleeps the process). Same limits and same
// bookkeeping as LoreChallengeMatchRegistry, with a game in place of a match:
// - maxActiveGames: games that are neither finished nor expired. Reaching it rejects new games
//   with CAPACITY_EXCEEDED. Finishing a game frees a slot at once.
// - maxRetainedGames: every stored game. A finished game is kept until its TTL so that its result
//   can still be read; when the total limit is reached, the finished games that finished first
//   are dropped to make room. Open games are never dropped early.
//
// All state is guarded by this monitor and every operation is O(1) amortized: every game has the
// same TTL, so creation order is also expiry order and the sweep stops at the first unexpired
// game. There is no scheduler: the sweep runs at the start of every operation.
@Component
public class WhoAmIGameRegistry {

    // Insertion order is creation order.
    private final Map<String, WhoAmIGame> games = new LinkedHashMap<>();

    // Ids of finished games, in the order they finished: the order they are evicted in.
    private final Set<String> finished = new LinkedHashSet<>();

    // Games that are neither finished nor expired.
    private int open;

    private final Clock clock;
    private final Duration ttl;
    private final int maxActiveGames;
    private final int maxRetainedGames;

    // maxRetainedGames: 0 means twice maxActiveGames. A game holds ten references to shared
    // characters and a few dozen short option strings, a few KB, so the defaults (2,000 open,
    // 4,000 stored) stay within a few tens of MB at worst.
    @Autowired
    public WhoAmIGameRegistry(
            Clock clock,
            @Value("${wunderlore.who-am-i.game-ttl:PT30M}") Duration ttl,
            @Value("${wunderlore.who-am-i.max-active-games:2000}") int maxActiveGames,
            @Value("${wunderlore.who-am-i.max-retained-games:0}") int maxRetainedGames) {

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Game TTL must be positive");
        }

        if (maxActiveGames < 1) {
            throw new IllegalArgumentException("Maximum active games must be at least 1");
        }

        if (maxRetainedGames < 0
                || (maxRetainedGames != 0 && maxRetainedGames < maxActiveGames)) {
            throw new IllegalArgumentException(
                    "Maximum retained games must not be below the maximum active games");
        }

        this.clock = clock;
        this.ttl = ttl;
        this.maxActiveGames = maxActiveGames;
        this.maxRetainedGames = maxRetainedGames == 0
                ? (int) Math.min(2L * maxActiveGames, Integer.MAX_VALUE)
                : maxRetainedGames;
    }

    public WhoAmIGameRegistry(Clock clock, Duration ttl, int maxActiveGames) {
        this(clock, ttl, maxActiveGames, 0);
    }

    public synchronized String register(WhoAmIGame game) {
        evictExpired(clock.instant());

        if (open >= maxActiveGames) {
            throw new WhoAmIException(Reason.CAPACITY_EXCEEDED);
        }

        if (games.size() >= maxRetainedGames) {
            evictOldestFinished();
        }

        String gameId = UUID.randomUUID().toString();

        games.put(gameId, game);

        if (game.isFinished()) {
            finished.add(gameId);
        } else {
            open++;
        }

        return gameId;
    }

    public synchronized RoundView currentRound(String gameId) {
        return find(gameId).currentRound();
    }

    public synchronized AnswerOutcome answer(
            String gameId, int roundNumber, int clueNumber, String optionId) {
        WhoAmIGame game = find(gameId);
        AnswerOutcome outcome = game.answer(roundNumber, clueNumber, optionId);

        if (game.isFinished()) {
            open--;
            finished.add(gameId);
        }

        return outcome;
    }

    // When the game was created, i.e. when it was played. Read by the ranking, which takes
    // the score from result(); a finished game never changes, so the two reads agree.
    public synchronized Instant createdAt(String gameId) {
        return find(gameId).createdAt();
    }

    public synchronized Result result(String gameId) {
        return find(gameId).result();
    }

    private WhoAmIGame find(String gameId) {
        Instant now = clock.instant();

        evictExpired(now);

        WhoAmIGame game = games.get(gameId);

        // The sweep already dropped what expired in order; the check also covers a game that
        // expired behind a younger one, which can only happen if the clock went backwards.
        if (game == null || isExpired(game, now)) {
            throw new WhoAmIException(Reason.GAME_NOT_FOUND);
        }

        return game;
    }

    // Drops games from the oldest end while they are expired; stops at the first one that is not.
    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, WhoAmIGame>> iterator = games.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, WhoAmIGame> entry = iterator.next();

            if (!isExpired(entry.getValue(), now)) {
                return;
            }

            iterator.remove();

            if (entry.getValue().isFinished()) {
                finished.remove(entry.getKey());
            } else {
                open--;
            }
        }
    }

    // Makes room for one more game by dropping the one that finished first. Only called when the
    // total limit is reached while fewer than maxActiveGames are open, and the total limit is
    // never below the active limit, so a finished game always exists.
    private void evictOldestFinished() {
        Iterator<String> iterator = finished.iterator();
        String oldest = iterator.next();

        iterator.remove();
        games.remove(oldest);
    }

    private boolean isExpired(WhoAmIGame game, Instant now) {
        return !now.isBefore(game.createdAt().plus(ttl));
    }
}
