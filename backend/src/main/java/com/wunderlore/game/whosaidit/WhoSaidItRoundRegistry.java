package com.wunderlore.game.whosaidit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// Rounds live in memory for one TTL. Two limits apply:
// - maxActiveRounds: rounds that are neither answered nor expired. Reaching it rejects new
//   rounds with WhoSaidItRoundCapacityExceededException. Answering frees a slot at once.
// - maxRetainedRounds: every stored round, answered ones included. An answered round is kept
//   until its TTL only so that a repeated answer is a 409 instead of a 404; when the total
//   limit is reached, the answered rounds that were answered first are dropped to make room
//   (their repeated answers then read as 404). Open rounds are never dropped early.
//
// All state is guarded by this monitor and every operation is O(1) amortized: a round never
// moves in the map, and since every round has the same TTL, creation order is also expiry
// order, so the sweep only looks at the oldest rounds and stops at the first unexpired one.
// There is no scheduler: the sweep runs at the start of register() and answer().
@Component
public class WhoSaidItRoundRegistry {

    // Insertion order is creation order.
    private final Map<String, ActiveRound> rounds = new LinkedHashMap<>();

    // Ids of answered rounds, in the order they were answered: the order they are evicted in.
    private final Set<String> completed = new LinkedHashSet<>();

    // Rounds that are neither answered nor expired. rounds.size() - unanswered == completed.size().
    private int unanswered;

    private final Clock clock;
    private final Duration ttl;
    private final int maxActiveRounds;
    private final int maxRetainedRounds;

    // maxRetainedRounds: 0 means twice maxActiveRounds. That leaves room to remember as many
    // completed rounds as there can be open ones, which costs about 270 bytes each (measured),
    // so the default 20,000 rounds stay under about 6 MB.
    @Autowired
    public WhoSaidItRoundRegistry(
            Clock clock,
            @Value("${wunderlore.game.round-ttl:PT10M}") Duration ttl,
            @Value("${wunderlore.game.max-active-rounds:10000}") int maxActiveRounds,
            @Value("${wunderlore.game.max-retained-rounds:0}") int maxRetainedRounds) {

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Round TTL must be positive");
        }

        if (maxActiveRounds < 1) {
            throw new IllegalArgumentException(
                    "Maximum active rounds must be at least 1");
        }

        if (maxRetainedRounds < 0
                || (maxRetainedRounds != 0 && maxRetainedRounds < maxActiveRounds)) {
            throw new IllegalArgumentException(
                    "Maximum retained rounds must not be below the maximum active rounds");
        }

        this.clock = clock;
        this.ttl = ttl;
        this.maxActiveRounds = maxActiveRounds;
        this.maxRetainedRounds = maxRetainedRounds == 0
                ? (int) Math.min(2L * maxActiveRounds, Integer.MAX_VALUE)
                : maxRetainedRounds;
    }

    public WhoSaidItRoundRegistry(Clock clock, Duration ttl, int maxActiveRounds) {
        this(clock, ttl, maxActiveRounds, 0);
    }

    public synchronized String register(WhoSaidItRound round) {
        evictExpired(clock.instant());

        if (unanswered >= maxActiveRounds) {
            throw new WhoSaidItRoundCapacityExceededException();
        }

        Set<String> optionIds = new HashSet<>();

        for (WhoSaidItRound.CharacterOption option : round.options()) {
            optionIds.add(option.id());
        }

        if (!optionIds.contains(round.correctCharacterId())) {
            throw new IllegalArgumentException(
                    "Correct character must be one of the round options");
        }

        if (rounds.size() >= maxRetainedRounds) {
            evictOldestCompleted();
        }

        String roundId = UUID.randomUUID().toString();

        rounds.put(roundId, new ActiveRound(
                round.correctCharacterId(),
                Set.copyOf(optionIds),
                clock.instant()));
        unanswered++;

        return roundId;
    }

    public synchronized WhoSaidItAnswerResponse answer(String roundId, String characterId) {
        Instant now = clock.instant();

        evictExpired(now);

        ActiveRound round = rounds.get(roundId);

        // The sweep already dropped what expired in order; the check also covers a round
        // that expired behind a younger one, which can only happen if the clock went backwards.
        if (round == null || isExpired(round, now)) {
            throw new WhoSaidItRoundNotFoundException();
        }

        if (!round.optionIds().contains(characterId)) {
            throw new WhoSaidItInvalidCharacterOptionException();
        }

        if (round.answered) {
            throw new WhoSaidItRoundAlreadyAnsweredException();
        }

        round.answered = true;
        unanswered--;
        completed.add(roundId);

        return new WhoSaidItAnswerResponse(
                roundId,
                characterId,
                round.correctCharacterId().equals(characterId),
                round.correctCharacterId());
    }

    // Drops rounds from the oldest end while they are expired; stops at the first one that is not.
    private void evictExpired(Instant now) {
        Iterator<Map.Entry<String, ActiveRound>> iterator = rounds.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<String, ActiveRound> entry = iterator.next();

            if (!isExpired(entry.getValue(), now)) {
                return;
            }

            iterator.remove();

            if (entry.getValue().answered) {
                completed.remove(entry.getKey());
            } else {
                unanswered--;
            }
        }
    }

    // Makes room for one more round by dropping the round that was answered first. Only called
    // when the total limit is reached while fewer than maxActiveRounds rounds are open, and the
    // total limit is never below the active limit, so a completed round always exists.
    private void evictOldestCompleted() {
        Iterator<String> iterator = completed.iterator();
        String oldest = iterator.next();

        iterator.remove();
        rounds.remove(oldest);
    }

    private boolean isExpired(ActiveRound round, Instant now) {
        return !now.isBefore(round.createdAt().plus(ttl));
    }

    // answered is only read and written while holding the registry monitor.
    private static final class ActiveRound {

        private final String correctCharacterId;
        private final Set<String> optionIds;
        private final Instant createdAt;
        private boolean answered;

        private ActiveRound(String correctCharacterId, Set<String> optionIds, Instant createdAt) {
            this.correctCharacterId = correctCharacterId;
            this.optionIds = optionIds;
            this.createdAt = createdAt;
        }

        private String correctCharacterId() {
            return correctCharacterId;
        }

        private Set<String> optionIds() {
            return optionIds;
        }

        private Instant createdAt() {
            return createdAt;
        }
    }
}
