package com.wunderlore.game.whosaidit;

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

import com.wunderlore.game.whosaidit.WhoSaidItMatch.AnswerOutcome;
import com.wunderlore.game.whosaidit.WhoSaidItMatch.QuestionView;
import com.wunderlore.game.whosaidit.WhoSaidItMatch.Result;
import com.wunderlore.game.whosaidit.WhoSaidItMatchException.Reason;

// Competitive matches live in memory for one TTL, counted from their creation, and are lost on
// restart. Same limits and bookkeeping as WhoAmIGameRegistry, with a match in place of a game:
// - maxActiveMatches: matches that are neither finished nor expired. Reaching it rejects new
//   matches with CAPACITY_EXCEEDED. Finishing a match frees a slot at once.
// - maxRetainedMatches: every stored match. A finished match is kept until its TTL so that its
//   result can still be read (and published); when the total limit is reached, the finished
//   matches that finished first are dropped to make room. Open matches are never dropped early.
//
// All state is guarded by this monitor and every operation is O(1) amortized: every match has the
// same TTL, so creation order is also expiry order and the sweep stops at the first unexpired one.
@Component
public class WhoSaidItMatchRegistry {

    // Insertion order is creation order.
    private final Map<String, WhoSaidItMatch> matches = new LinkedHashMap<>();

    // Ids of finished matches, in the order they finished: the order they are evicted in.
    private final Set<String> finished = new LinkedHashSet<>();

    // Matches that are neither finished nor expired.
    private int open;

    private final Clock clock;
    private final Duration ttl;
    private final int maxActiveMatches;
    private final int maxRetainedMatches;

    // maxRetainedMatches: 0 means twice maxActiveMatches.
    @Autowired
    public WhoSaidItMatchRegistry(
            Clock clock,
            @Value("${wunderlore.who-said-it.match-ttl:PT30M}") Duration ttl,
            @Value("${wunderlore.who-said-it.max-active-matches:2000}") int maxActiveMatches,
            @Value("${wunderlore.who-said-it.max-retained-matches:0}") int maxRetainedMatches) {

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Match TTL must be positive");
        }

        if (maxActiveMatches < 1) {
            throw new IllegalArgumentException("Maximum active matches must be at least 1");
        }

        if (maxRetainedMatches < 0
                || (maxRetainedMatches != 0 && maxRetainedMatches < maxActiveMatches)) {
            throw new IllegalArgumentException(
                    "Maximum retained matches must not be below the maximum active matches");
        }

        this.clock = clock;
        this.ttl = ttl;
        this.maxActiveMatches = maxActiveMatches;
        this.maxRetainedMatches = maxRetainedMatches == 0
                ? (int) Math.min(2L * maxActiveMatches, Integer.MAX_VALUE)
                : maxRetainedMatches;
    }

    public WhoSaidItMatchRegistry(Clock clock, Duration ttl, int maxActiveMatches) {
        this(clock, ttl, maxActiveMatches, 0);
    }

    public synchronized String register(WhoSaidItMatch match) {
        evictExpired(clock.instant());

        if (open >= maxActiveMatches) {
            throw new WhoSaidItMatchException(Reason.CAPACITY_EXCEEDED);
        }

        if (matches.size() >= maxRetainedMatches) {
            evictOldestFinished();
        }

        String matchId = UUID.randomUUID().toString();

        matches.put(matchId, match);

        if (match.isFinished()) {
            finished.add(matchId);
        } else {
            open++;
        }

        return matchId;
    }

    public synchronized QuestionView currentQuestion(String matchId) {
        return find(matchId).currentQuestion();
    }

    public synchronized AnswerOutcome answer(String matchId, int questionNumber, String characterId) {
        WhoSaidItMatch match = find(matchId);
        boolean wasFinished = match.isFinished();
        AnswerOutcome outcome = match.answer(questionNumber, characterId);

        if (!wasFinished && match.isFinished()) {
            open--;
            finished.add(matchId);
        }

        return outcome;
    }

    // When the game was created, i.e. when it was played. Read by the ranking, which takes
    // the score from result(); a finished game never changes, so the two reads agree.
    public synchronized Instant createdAt(String matchId) {
        return find(matchId).createdAt();
    }

    public synchronized Result result(String matchId) {
        return find(matchId).result();
    }

    private WhoSaidItMatch find(String matchId) {
        Instant now = clock.instant();

        evictExpired(now);

        WhoSaidItMatch match = matches.get(matchId);

        // The sweep already dropped what expired in order; the check also covers a match that
        // expired behind a younger one, which can only happen if the clock went backwards.
        if (match == null || isExpired(match, now)) {
            throw new WhoSaidItMatchException(Reason.MATCH_NOT_FOUND);
        }

        return match;
    }

    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, WhoSaidItMatch>> iterator = matches.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, WhoSaidItMatch> entry = iterator.next();

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

    // Only called when the total limit is reached while fewer than maxActiveMatches are open,
    // and the total limit is never below the active limit, so a finished match always exists.
    private void evictOldestFinished() {
        Iterator<String> iterator = finished.iterator();
        String oldest = iterator.next();

        iterator.remove();
        matches.remove(oldest);
    }

    private boolean isExpired(WhoSaidItMatch match, Instant now) {
        return !now.isBefore(match.createdAt().plus(ttl));
    }
}
