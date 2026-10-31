package com.wunderlore.game.whosaidit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
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

    public synchronized String register(WhoSaidItRound round) {
        Instant now = clock.instant();

        rounds.values().removeIf(activeRound -> isExpired(activeRound, now));

        if (rounds.size() >= maxActiveRounds) {
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
