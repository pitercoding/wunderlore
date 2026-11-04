package com.wunderlore.game.lorechallenge;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.wunderlore.game.lorechallenge.LoreChallengeException.Reason;

// The state of one match: its fixed list of questions (with the options already shuffled), how
// far the player is, and the score. The score is only ever changed here, from the answers the
// match receives, so nothing a client sends can set it.
//
// Not thread-safe by itself: LoreChallengeMatchRegistry only touches a match while holding its
// monitor.
public final class LoreChallengeMatch {

    public static final String ALL_CATEGORIES = "ALL";

    public record Option(String id, String text) {
    }

    // The correct option is identified by its id among the shuffled options; it never leaves
    // the backend before the question is answered.
    public record PlayedQuestion(
            LoreChallengeQuestion question,
            List<Option> options,
            String correctOptionId) {
    }

    // What the player may see of a question that is waiting for an answer.
    public record QuestionView(
            String category,
            LoreChallengeDifficulty difficulty,
            int number,
            int total,
            String questionId,
            LoreChallengeCategory questionCategory,
            String prompt,
            List<Option> options) {
    }

    // What the player learns once a question is answered.
    public record AnswerOutcome(
            String questionId,
            String submittedOptionId,
            boolean correct,
            String correctOptionId,
            String explanation,
            int pointsAwarded,
            int score,
            int answered,
            int total,
            boolean finished) {
    }

    public record Result(
            String category,
            LoreChallengeDifficulty difficulty,
            int totalQuestions,
            int correctAnswers,
            int incorrectAnswers,
            int score,
            int maxScore,
            int percentage,
            long durationSeconds,
            String rank) {
    }

    private final String category;
    private final LoreChallengeDifficulty difficulty;
    private final List<PlayedQuestion> questions;
    private final Instant createdAt;

    private int answered;
    private int correctAnswers;
    private int score;
    private Instant finishedAt;

    // category: ALL_CATEGORIES or the name of a LoreChallengeCategory.
    LoreChallengeMatch(
            String category,
            LoreChallengeDifficulty difficulty,
            List<PlayedQuestion> questions,
            Instant createdAt) {
        if (questions.isEmpty()) {
            throw new IllegalArgumentException("A match needs at least one question");
        }

        this.category = category;
        this.difficulty = difficulty;
        this.questions = List.copyOf(questions);
        this.createdAt = createdAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    boolean isFinished() {
        return answered == questions.size();
    }

    QuestionView currentQuestion() {
        if (isFinished()) {
            throw new LoreChallengeException(Reason.MATCH_FINISHED);
        }

        PlayedQuestion current = questions.get(answered);

        return new QuestionView(
                category,
                difficulty,
                answered + 1,
                questions.size(),
                current.question().id(),
                current.question().category(),
                current.question().prompt(),
                current.options());
    }

    AnswerOutcome answer(String questionId, String optionId, Instant now) {
        if (isFinished()) {
            throw new LoreChallengeException(Reason.MATCH_FINISHED);
        }

        PlayedQuestion current = questions.get(answered);

        if (!current.question().id().equals(questionId)) {
            boolean belongsToMatch = questions.stream()
                    .anyMatch(played -> played.question().id().equals(questionId));

            throw new LoreChallengeException(belongsToMatch
                    ? Reason.NOT_CURRENT_QUESTION
                    : Reason.QUESTION_NOT_IN_MATCH);
        }

        if (current.options().stream().noneMatch(option -> option.id().equals(optionId))) {
            throw new LoreChallengeException(Reason.INVALID_OPTION);
        }

        boolean correct = current.correctOptionId().equals(optionId);
        int points = correct ? difficulty.pointsPerCorrectAnswer() : 0;

        answered++;
        score += points;

        if (correct) {
            correctAnswers++;
        }

        if (isFinished()) {
            finishedAt = now;
        }

        return new AnswerOutcome(
                questionId,
                optionId,
                correct,
                current.correctOptionId(),
                current.question().explanation(),
                points,
                score,
                answered,
                questions.size(),
                isFinished());
    }

    Result result() {
        if (!isFinished()) {
            throw new LoreChallengeException(Reason.MATCH_NOT_FINISHED);
        }

        int total = questions.size();
        int percentage = (int) Math.round(correctAnswers * 100.0 / total);
        // The time from creating the match to its last answer: it includes reading time.
        long seconds = Math.max(0, Duration.between(createdAt, finishedAt).toSeconds());

        return new Result(
                category,
                difficulty,
                total,
                correctAnswers,
                total - correctAnswers,
                score,
                total * difficulty.pointsPerCorrectAnswer(),
                percentage,
                seconds,
                rankFor(percentage));
    }

    static String rankFor(int percentage) {
        if (percentage >= 100) {
            return "Loremaster of Middle-earth";
        }

        if (percentage >= 80) {
            return "Keeper of the Archives";
        }

        if (percentage >= 60) {
            return "Scholar of Rivendell";
        }

        if (percentage >= 40) {
            return "Ranger of the North";
        }

        if (percentage >= 20) {
            return "Traveler of the Shire";
        }

        return "Wanderer in the Mist";
    }
}
