package com.wunderlore.game.whosaidit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WhoSaidItRoundRegistry {

    private final ConcurrentHashMap<String, ActiveRound> rounds = new ConcurrentHashMap<>();

    private final Clock clock;
    private final Duration ttl;
    private final int maxActiveRounds;

    public WhoSaidItRoundRegistry(
            Clock clock,
            @Value("${wunderlore.game.round-ttl:PT10M}") Duration ttl,
            @Value("${wunderlore.game.max-active-rounds:10000}") int maxActiveRounds) {

        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Round TTL must be positive");
        }

        if (maxActiveRounds < 1) {
            throw new IllegalArgumentException(
                    "Maximum active rounds must be at least 1");
        }

        this.clock = clock;
        this.ttl = ttl;
        this.maxActiveRounds = maxActiveRounds;
    }

    // Capacity counts only rounds that are neither expired nor answered. The count is derived
    // from the map during the same sweep that drops expired rounds, so there is no counter to
    // keep in step with it. Answered rounds stay stored until their TTL so that a repeated
    // answer is still a 409. Only register() adds entries, and under this monitor; answer()
    // can only flip a round to answered, which lowers the count, so a concurrent answer can
    // make this sweep over-count (a spurious 503 at the boundary) but never exceed the limit.
    public synchronized String register(WhoSaidItRound round) {
        Instant now = clock.instant();
        int unanswered = 0;

        for (Iterator<ActiveRound> iterator = rounds.values().iterator(); iterator.hasNext();) {
            ActiveRound activeRound = iterator.next();

            if (isExpired(activeRound, now)) {
                iterator.remove();
            } else if (!activeRound.answered().get()) {
                unanswered++;
            }
        }

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

        String roundId = UUID.randomUUID().toString();

        ActiveRound activeRound = new ActiveRound(
                round.correctCharacterId(),
                Set.copyOf(optionIds),
                now,
                new AtomicBoolean(false));

        rounds.put(roundId, activeRound);

        return roundId;
    }

    public WhoSaidItAnswerResponse answer(String roundId, String characterId) {
        ActiveRound round = rounds.get(roundId);

        if (round == null || isExpired(round, clock.instant())) {
            throw new WhoSaidItRoundNotFoundException();
        }

        if (!round.optionIds().contains(characterId)) {
            throw new WhoSaidItInvalidCharacterOptionException();
        }

        if (!round.answered().compareAndSet(false, true)) {
            throw new WhoSaidItRoundAlreadyAnsweredException();
        }

        boolean correct = round.correctCharacterId()
                .equals(characterId);

        return new WhoSaidItAnswerResponse(
                roundId,
                characterId,
                correct,
                round.correctCharacterId());
    }

    private boolean isExpired(ActiveRound round, Instant now) {
        return !now.isBefore(round.createdAt().plus(ttl));
    }

    private record ActiveRound(
            String correctCharacterId,
            Set<String> optionIds,
            Instant createdAt,
            AtomicBoolean answered) {
    }
}
