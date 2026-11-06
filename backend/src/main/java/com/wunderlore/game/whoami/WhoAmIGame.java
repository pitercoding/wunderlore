package com.wunderlore.game.whoami;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.wunderlore.game.whoami.WhoAmIException.Reason;

// The state of one game: ten rounds fixed at creation, how far the player is, and the score. The
// score, the clue and the correctness of every answer are only ever decided here, from the
// answers the game receives, so nothing a client sends can set them.
//
// What the player may see is limited by construction: views carry the clues revealed so far and
// the current options, never the character, the unrevealed clues or which option is correct. The
// name and all five clues come out only in the reveal of a finished round.
//
// Not thread-safe by itself: WhoAmIGameRegistry only touches a game while holding its monitor.
public final class WhoAmIGame {

    public static final int TOTAL_ROUNDS = 10;

    public static final int TOTAL_CLUES = WhoAmICharacterBank.CLUES_PER_CHARACTER;

    // Points for identifying the character at clue 1, 2, 3, 4 and 5. A round not identified is 0.
    private static final int[] POINTS_BY_CLUE = {100, 80, 60, 40, 20};

    public static final int MAX_SCORE = TOTAL_ROUNDS * POINTS_BY_CLUE[0];

    public record Option(String id, String text) {
    }

    // The four options of one round, the same on every clue. characterIdByOption stays inside the
    // backend: it maps an option id to the character it stands for.
    public record Options(
            List<Option> options,
            String correctOptionId,
            Map<String, String> characterIdByOption) {

        public Options {
            options = List.copyOf(options);
            characterIdByOption = Map.copyOf(characterIdByOption);
        }
    }

    // Chooses the options of one round, once, when the game is created. Provided by the service,
    // which owns the randomness. The game always passes an empty set of excluded ids: the options
    // are never redrawn during a round.
    @FunctionalInterface
    public interface OptionDrawer {

        Options draw(WhoAmICharacter correct, Set<String> excludedCharacterIds);
    }

    // What the player sees of the round that is waiting for an answer.
    public record RoundView(
            WhoAmIDifficulty difficulty,
            int roundNumber,
            int totalRounds,
            int clueNumber,
            int totalClues,
            List<String> clues,
            List<Option> options,
            int score,
            int maxScore) {
    }

    // The next clue of the same round, after a wrong answer.
    public record NextClue(int clueNumber, List<String> clues, List<Option> options) {
    }

    // What a finished round discloses: who it was and every clue.
    public record Reveal(String characterName, List<String> clues) {
    }

    public record RoundSummary(
            int roundNumber,
            String characterName,
            int cluesUsed,
            boolean identified,
            int points,
            int wrongAttempts) {
    }

    public record Result(
            WhoAmIDifficulty difficulty,
            int totalRounds,
            int score,
            int maxScore,
            int percentage,
            int identified,
            int incorrectAnswers,
            int unidentified,
            List<RoundSummary> rounds) {
    }

    // next is set while the round goes on; reveal when it just ended; result when the game did.
    public record AnswerOutcome(
            int roundNumber,
            int clueNumber,
            String submittedOptionId,
            boolean correct,
            boolean roundFinished,
            int pointsAwarded,
            int score,
            boolean gameFinished,
            NextClue next,
            Reveal reveal,
            Result result) {
    }

    private static final class Round {

        final WhoAmICharacter character;

        int clueNumber = 1;
        // Drawn once, when the game is created, and never replaced.
        final Options options;
        int wrongAttempts;
        boolean finished;
        boolean identified;
        int points;
        int cluesUsed;

        Round(WhoAmICharacter character, Options options) {
            this.character = character;
            this.options = options;
        }
    }

    private final WhoAmIDifficulty difficulty;
    private final List<Round> rounds = new ArrayList<>();
    private final Instant createdAt;

    private int current;
    private int score;

    WhoAmIGame(
            WhoAmIDifficulty difficulty,
            List<WhoAmICharacter> characters,
            OptionDrawer drawer,
            Instant createdAt) {
        if (characters.size() != TOTAL_ROUNDS) {
            throw new IllegalArgumentException("A game needs exactly " + TOTAL_ROUNDS + " characters");
        }

        this.difficulty = difficulty;
        this.createdAt = createdAt;

        for (WhoAmICharacter character : characters) {
            rounds.add(new Round(character, drawer.draw(character, Set.of())));
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
            throw new WhoAmIException(Reason.GAME_FINISHED);
        }

        Round round = rounds.get(current);

        return new RoundView(
                difficulty,
                current + 1,
                rounds.size(),
                round.clueNumber,
                TOTAL_CLUES,
                revealedClues(round),
                round.options.options(),
                score,
                MAX_SCORE);
    }

    // roundNumber and clueNumber say what the client believes it is answering. Anything but the
    // clue that is actually waiting is refused, which is also what stops an answer sent twice (or
    // from two tabs) from counting twice: the second one finds the game already moved on.
    AnswerOutcome answer(int roundNumber, int clueNumber, String optionId) {
        if (isFinished()) {
            throw new WhoAmIException(Reason.GAME_FINISHED);
        }

        Round round = rounds.get(current);

        if (roundNumber != current + 1 || clueNumber != round.clueNumber) {
            throw new WhoAmIException(Reason.STALE_SUBMISSION);
        }

        String chosenCharacterId = round.options.characterIdByOption().get(optionId);

        if (chosenCharacterId == null) {
            throw new WhoAmIException(Reason.INVALID_OPTION);
        }

        boolean correct = optionId.equals(round.options.correctOptionId());
        int answeredClue = round.clueNumber;

        if (correct) {
            finish(round, true, POINTS_BY_CLUE[answeredClue - 1], answeredClue);
        } else {
            round.wrongAttempts++;

            if (answeredClue == TOTAL_CLUES) {
                finish(round, false, 0, TOTAL_CLUES);
            } else {
                // The same four options stay for the whole round. Drawing new wrong ones here
                // would let a player find the right one as the only option present in every
                // set, without reading a single clue.
                round.clueNumber++;
            }
        }

        if (!round.finished) {
            return new AnswerOutcome(
                    roundNumber,
                    answeredClue,
                    optionId,
                    false,
                    false,
                    0,
                    score,
                    false,
                    new NextClue(round.clueNumber, revealedClues(round), round.options.options()),
                    null,
                    null);
        }

        score += round.points;
        current++;

        return new AnswerOutcome(
                roundNumber,
                answeredClue,
                optionId,
                correct,
                true,
                round.points,
                score,
                isFinished(),
                null,
                new Reveal(round.character.name(), round.character.clues()),
                isFinished() ? result() : null);
    }

    Result result() {
        if (!isFinished()) {
            throw new WhoAmIException(Reason.GAME_NOT_FINISHED);
        }

        List<RoundSummary> summaries = new ArrayList<>();
        int identified = 0;
        int wrong = 0;

        for (int i = 0; i < rounds.size(); i++) {
            Round round = rounds.get(i);

            summaries.add(new RoundSummary(
                    i + 1,
                    round.character.name(),
                    round.cluesUsed,
                    round.identified,
                    round.points,
                    round.wrongAttempts));

            if (round.identified) {
                identified++;
            }

            wrong += round.wrongAttempts;
        }

        return new Result(
                difficulty,
                rounds.size(),
                score,
                MAX_SCORE,
                (int) Math.round(score * 100.0 / MAX_SCORE),
                identified,
                wrong,
                rounds.size() - identified,
                List.copyOf(summaries));
    }

    private static void finish(Round round, boolean identified, int points, int cluesUsed) {
        round.finished = true;
        round.identified = identified;
        round.points = points;
        round.cluesUsed = cluesUsed;
    }

    private static List<String> revealedClues(Round round) {
        return List.copyOf(round.character.clues().subList(0, round.clueNumber));
    }
}
