package com.wunderlore.game.lorechallenge;

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

import com.wunderlore.game.lorechallenge.LoreChallengeException.Reason;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.AnswerOutcome;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.QuestionView;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Result;

// Matches live in memory for one TTL, counted from their creation, and are lost on restart (the
// production database is off, and Render Free restarts or sleeps the process). It follows the
// limits of WhoSaidItRoundRegistry, with a match in place of a round:
// - maxActiveMatches: matches that are neither finished nor expired. Reaching it rejects new
//   matches with CAPACITY_EXCEEDED. Finishing a match frees a slot at once.
// - maxRetainedMatches: every stored match. A finished match is kept until its TTL so that its
//   result can still be read; when the total limit is reached, the finished matches that
//   finished first are dropped to make room. Open matches are never dropped early.
//
// All state is guarded by this monitor and every operation is O(1) amortized: every match has
// the same TTL, so creation order is also expiry order and the sweep stops at the first
// unexpired match. There is no scheduler: the sweep runs at the start of every operation.
@Component
public class LoreChallengeMatchRegistry {

    // Insertion order is creation order.
    private final Map<String, LoreChallengeMatch> matches = new LinkedHashMap<>();

    // Ids of finished matches, in the order they finished: the order they are evicted in.
    private final Set<String> finished = new LinkedHashSet<>();

    // Matches that are neither finished nor expired.
    private int open;

    private final Clock clock;
    private final Duration ttl;
    private final int maxActiveMatches;
    private final int maxRetainedMatches;

    // maxRetainedMatches: 0 means twice maxActiveMatches. A match holds ten references to shared
    // questions and about forty short option strings, roughly 3 KB, so the defaults (2,000 open,
    // 4,000 stored) stay under about 12 MB.
    @Autowired
    public LoreChallengeMatchRegistry(
            Clock clock,
            @Value("${wunderlore.lore-challenge.match-ttl:PT30M}") Duration ttl,
            @Value("${wunderlore.lore-challenge.max-active-matches:2000}") int maxActiveMatches,
            @Value("${wunderlore.lore-challenge.max-retained-matches:0}") int maxRetainedMatches) {

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

    public LoreChallengeMatchRegistry(Clock clock, Duration ttl, int maxActiveMatches) {
        this(clock, ttl, maxActiveMatches, 0);
    }

    public synchronized String register(LoreChallengeMatch match) {
        evictExpired(clock.instant());

        if (open >= maxActiveMatches) {
            throw new LoreChallengeException(Reason.CAPACITY_EXCEEDED);
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

    public synchronized AnswerOutcome answer(String matchId, String questionId, String optionId) {
        Instant now = clock.instant();
        LoreChallengeMatch match = find(matchId, now);
        AnswerOutcome outcome = match.answer(questionId, optionId, now);

        if (match.isFinished()) {
            open--;
            finished.add(matchId);
        }

        return outcome;
    }

    public synchronized Result result(String matchId) {
        return find(matchId).result();
    }

    private LoreChallengeMatch find(String matchId) {
        return find(matchId, clock.instant());
    }

    private LoreChallengeMatch find(String matchId, Instant now) {
        evictExpired(now);

        LoreChallengeMatch match = matches.get(matchId);

        // The sweep already dropped what expired in order; the check also covers a match that
        // expired behind a younger one, which can only happen if the clock went backwards.
        if (match == null || isExpired(match, now)) {
            throw new LoreChallengeException(Reason.MATCH_NOT_FOUND);
        }

        return match;
    }

    // Drops matches from the oldest end while they are expired; stops at the first one that is not.
    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, LoreChallengeMatch>> iterator = matches.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, LoreChallengeMatch> entry = iterator.next();

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

    // Makes room for one more match by dropping the one that finished first. Only called when
    // the total limit is reached while fewer than maxActiveMatches are open, and the total limit
    // is never below the active limit, so a finished match always exists.
    private void evictOldestFinished() {
        Iterator<String> iterator = finished.iterator();
        String oldest = iterator.next();

        iterator.remove();
        matches.remove(oldest);
    }

    private boolean isExpired(LoreChallengeMatch match, Instant now) {
        return !now.isBefore(match.createdAt().plus(ttl));
    }
}
