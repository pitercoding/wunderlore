package com.wunderlore.game.whereinmiddleearth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.wunderlore.game.whereinmiddleearth.WhereInMiddleEarthException.Reason;

// The state of one game: ten places fixed at creation, how far the player is, and the score. The
// distance, the accuracy and the points of every answer are only ever decided here, from the
// position the game receives, so nothing a client sends can set them.
//
// What the player may see is limited by construction: the round view carries the name and the
// description of the place being asked, never its coordinates. The place, its coordinates and its
// explanation come out only in the reveal of an answered round.
//
// Not thread-safe by itself: WhereInMiddleEarthGameRegistry only touches a game while holding its
// monitor.
public final class WhereInMiddleEarthGame {

    public static final int TOTAL_ROUNDS = 10;

    public static final int MAX_SCORE = TOTAL_ROUNDS * WhereInMiddleEarthAccuracy.MAX_POINTS;

    // What the player sees of the round that is waiting for an answer.
    public record RoundView(
            WhereInMiddleEarthDifficulty difficulty,
            int roundNumber,
            int totalRounds,
            String locationName,
            String description,
            int score,
            int maxScore) {
    }

    public record Position(double x, double y) {
    }

    // What an answered round discloses: the real place and where it is.
    public record Reveal(
            String locationName,
            String regionName,
            String period,
            String explanation,
            double x,
            double y) {
    }

    public record RoundSummary(
            int roundNumber,
            String locationName,
            WhereInMiddleEarthAccuracy accuracy,
            double distance,
            int points) {
    }

    public record Result(
            WhereInMiddleEarthDifficulty difficulty,
            int totalRounds,
            int score,
            int maxScore,
            int percentage,
            int closeHits,
            double averageDistance,
            List<RoundSummary> rounds) {
    }

    // result is set only when this answer ended the last round.
    public record AnswerOutcome(
            int roundNumber,
            Position submitted,
            double distance,
            WhereInMiddleEarthAccuracy accuracy,
            int pointsAwarded,
            int score,
            Reveal reveal,
            boolean gameFinished,
            Result result) {
    }

    private static final class Round {

        final WhereInMiddleEarthLocation location;

        double distance;
        WhereInMiddleEarthAccuracy accuracy;
        int points;

        Round(WhereInMiddleEarthLocation location) {
            this.location = location;
        }
    }

    private final WhereInMiddleEarthDifficulty difficulty;
    private final List<Round> rounds = new ArrayList<>();
    private final Function<WhereInMiddleEarthLocation, String> regionNames;
    private final Instant createdAt;

    private int current;
    private int score;

    WhereInMiddleEarthGame(
            WhereInMiddleEarthDifficulty difficulty,
            List<WhereInMiddleEarthLocation> locations,
            Function<WhereInMiddleEarthLocation, String> regionNames,
            Instant createdAt) {
        if (locations.size() != TOTAL_ROUNDS) {
            throw new IllegalArgumentException("A game needs exactly " + TOTAL_ROUNDS + " places");
        }

        this.difficulty = difficulty;
        this.regionNames = regionNames;
        this.createdAt = createdAt;

        for (WhereInMiddleEarthLocation location : locations) {
            rounds.add(new Round(location));
        }
    }

    Instant createdAt() {
        return createdAt;
    }

    boolean isFinished() {
        return current == rounds.size();
    }

    RoundView currentRound() {
        if (isFinished()) {
            throw new WhereInMiddleEarthException(Reason.GAME_FINISHED);
        }

        WhereInMiddleEarthLocation location = rounds.get(current).location;

        return new RoundView(
                difficulty,
                current + 1,
                rounds.size(),
                location.name(),
                location.description(),
                score,
                MAX_SCORE);
    }

    // roundNumber says what the client believes it is answering. Anything but the round that is
    // actually waiting is refused, which is also what stops an answer sent twice (or from two
    // tabs) from counting twice: the second one finds the game already moved on.
    AnswerOutcome answer(int roundNumber, double x, double y) {
        if (isFinished()) {
            throw new WhereInMiddleEarthException(Reason.GAME_FINISHED);
        }

        if (roundNumber != current + 1) {
            throw new WhereInMiddleEarthException(Reason.STALE_SUBMISSION);
        }

        if (!WhereInMiddleEarthLocationBank.isInsideMap(x, y)) {
            throw new WhereInMiddleEarthException(Reason.INVALID_COORDINATES);
        }

        Round round = rounds.get(current);
        WhereInMiddleEarthLocation location = round.location;

        round.distance = WhereInMiddleEarthLocationBank.distance(x, y, location.x(), location.y());
        round.accuracy = WhereInMiddleEarthAccuracy.forDistance(round.distance);
        round.points = round.accuracy.points();

        score += round.points;
        current++;

        return new AnswerOutcome(
                roundNumber,
                new Position(x, y),
                round1(round.distance),
                round.accuracy,
                round.points,
                score,
                new Reveal(
                        location.name(),
                        regionNames.apply(location),
                        location.period(),
                        location.explanation(),
                        location.x(),
                        location.y()),
                isFinished(),
                isFinished() ? result() : null);
    }

    Result result() {
        if (!isFinished()) {
            throw new WhereInMiddleEarthException(Reason.GAME_NOT_FINISHED);
        }

        List<RoundSummary> summaries = new ArrayList<>();
        int closeHits = 0;
        double totalDistance = 0;

        for (int i = 0; i < rounds.size(); i++) {
            Round round = rounds.get(i);

            summaries.add(new RoundSummary(
                    i + 1,
                    round.location.name(),
                    round.accuracy,
                    round1(round.distance),
                    round.points));

            if (round.accuracy != WhereInMiddleEarthAccuracy.FAR) {
                closeHits++;
            }

            totalDistance += round.distance;
        }

        return new Result(
                difficulty,
                rounds.size(),
                score,
                MAX_SCORE,
                (int) Math.round(score * 100.0 / MAX_SCORE),
                closeHits,
                round1(totalDistance / rounds.size()),
                List.copyOf(summaries));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
