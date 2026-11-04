package com.wunderlore.game.lorechallenge;

import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.START;
import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.match;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.wunderlore.game.lorechallenge.LoreChallengeException.Reason;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.AnswerOutcome;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Result;

class LoreChallengeMatchTest {

    private static void assertReason(Runnable action, Reason reason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LoreChallengeException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(reason));
    }

    // Answers the first n questions: the first `correct` of them rightly, the rest wrongly.
    private static void play(LoreChallengeMatch match, int n, int correct) {
        for (int i = 1; i <= n; i++) {
            match.answer("q" + i, i <= correct ? "a" : "b", START.plusSeconds(i));
        }
    }

    @ParameterizedTest
    @EnumSource(LoreChallengeDifficulty.class)
    void shouldAwardThePointsOfTheDifficultyForACorrectAnswer(LoreChallengeDifficulty difficulty) {
        LoreChallengeMatch match = match(difficulty, 10, START);

        AnswerOutcome outcome = match.answer("q1", "a", START);

        assertThat(outcome.correct()).isTrue();
        assertThat(outcome.correctOptionId()).isEqualTo("a");
        assertThat(outcome.pointsAwarded()).isEqualTo(difficulty.pointsPerCorrectAnswer());
        assertThat(outcome.score()).isEqualTo(difficulty.pointsPerCorrectAnswer());
        assertThat(outcome.explanation()).isEqualTo("Explanation of q1.");
    }

    @Test
    void shouldUseTenTwentyAndThirtyPoints() {
        assertThat(LoreChallengeDifficulty.EASY.pointsPerCorrectAnswer()).isEqualTo(10);
        assertThat(LoreChallengeDifficulty.MEDIUM.pointsPerCorrectAnswer()).isEqualTo(20);
        assertThat(LoreChallengeDifficulty.HARD.pointsPerCorrectAnswer()).isEqualTo(30);
    }

    @Test
    void shouldAwardNothingForAnIncorrectAnswerAndRevealTheCorrectOne() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.HARD, 10, START);

        AnswerOutcome outcome = match.answer("q1", "c", START);

        assertThat(outcome.correct()).isFalse();
        assertThat(outcome.submittedOptionId()).isEqualTo("c");
        assertThat(outcome.correctOptionId()).isEqualTo("a");
        assertThat(outcome.pointsAwarded()).isZero();
        assertThat(outcome.score()).isZero();
        assertThat(outcome.answered()).isEqualTo(1);
        assertThat(outcome.finished()).isFalse();
    }

    @Test
    void shouldServeQuestionsOneAtATimeInOrder() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        assertThat(match.currentQuestion().questionId()).isEqualTo("q1");
        assertThat(match.currentQuestion().number()).isEqualTo(1);
        assertThat(match.currentQuestion().total()).isEqualTo(10);

        match.answer("q1", "a", START);

        assertThat(match.currentQuestion().questionId()).isEqualTo("q2");
        assertThat(match.currentQuestion().number()).isEqualTo(2);
    }

    @Test
    void shouldCalculateTheResultFromTheAnswersReceived() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.MEDIUM, 10, START);

        play(match, 10, 7);

        Result result = match.result();

        assertThat(result.totalQuestions()).isEqualTo(10);
        assertThat(result.correctAnswers()).isEqualTo(7);
        assertThat(result.incorrectAnswers()).isEqualTo(3);
        assertThat(result.score()).isEqualTo(140);
        assertThat(result.maxScore()).isEqualTo(200);
        assertThat(result.percentage()).isEqualTo(70);
        assertThat(result.difficulty()).isEqualTo(LoreChallengeDifficulty.MEDIUM);
        assertThat(result.category()).isEqualTo("ALL");
        assertThat(result.rank()).isEqualTo("Scholar of Rivendell");
    }

    @Test
    void shouldMeasureTheDurationUntilTheLastAnswer() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        play(match, 10, 10);

        // The tenth answer arrived ten seconds after the match was created.
        assertThat(match.result().durationSeconds()).isEqualTo(10);
        assertThat(Duration.ofSeconds(match.result().durationSeconds())).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldRejectAnAnswerSentTwice() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        match.answer("q1", "a", START);

        assertReason(() -> match.answer("q1", "a", START), Reason.NOT_CURRENT_QUESTION);
        assertThat(match.currentQuestion().questionId()).isEqualTo("q2");
        assertThat(match.answer("q2", "b", START).score()).isEqualTo(10);
    }

    @Test
    void shouldRejectAQuestionThatIsNotTheCurrentOne() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        assertReason(() -> match.answer("q5", "a", START), Reason.NOT_CURRENT_QUESTION);
    }

    @Test
    void shouldRejectAQuestionThatDoesNotExistInTheMatch() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        assertReason(() -> match.answer("not-in-this-match", "a", START), Reason.QUESTION_NOT_IN_MATCH);
    }

    @Test
    void shouldRejectAnInvalidOptionWithoutChangingTheMatch() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        assertReason(() -> match.answer("q1", "z", START), Reason.INVALID_OPTION);
        assertReason(() -> match.answer("q1", "A", START), Reason.INVALID_OPTION);

        assertThat(match.currentQuestion().questionId()).isEqualTo("q1");
        assertThat(match.answer("q1", "a", START).score()).isEqualTo(10);
    }

    @Test
    void shouldRejectAnswersAndQuestionsAfterTheMatchEnded() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        play(match, 10, 10);

        assertThat(match.isFinished()).isTrue();
        assertReason(() -> match.answer("q10", "a", START), Reason.MATCH_FINISHED);
        assertReason(() -> match.answer("q1", "a", START), Reason.MATCH_FINISHED);
        assertReason(match::currentQuestion, Reason.MATCH_FINISHED);
        // The late answers changed nothing.
        assertThat(match.result().score()).isEqualTo(100);
    }

    @Test
    void shouldNotGiveAResultBeforeTheMatchEnds() {
        LoreChallengeMatch match = match(LoreChallengeDifficulty.EASY, 10, START);

        play(match, 9, 9);

        assertReason(match::result, Reason.MATCH_NOT_FINISHED);
    }

    @ParameterizedTest
    @CsvSource({
            "0,Wanderer in the Mist",
            "19,Wanderer in the Mist",
            "20,Traveler of the Shire",
            "39,Traveler of the Shire",
            "40,Ranger of the North",
            "59,Ranger of the North",
            "60,Scholar of Rivendell",
            "79,Scholar of Rivendell",
            "80,Keeper of the Archives",
            "99,Keeper of the Archives",
            "100,Loremaster of Middle-earth"
    })
    void shouldRankByPercentage(int percentage, String rank) {
        assertThat(LoreChallengeMatch.rankFor(percentage)).isEqualTo(rank);
    }
}
