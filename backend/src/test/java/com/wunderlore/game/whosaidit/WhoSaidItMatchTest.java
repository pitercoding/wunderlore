package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wunderlore.game.whosaidit.WhoSaidItMatch.AnswerOutcome;
import com.wunderlore.game.whosaidit.WhoSaidItMatch.Question;
import com.wunderlore.game.whosaidit.WhoSaidItMatchException.Reason;

class WhoSaidItMatchTest {

    // Question n: the correct character is "c<n>" and the wrong ones are "w<n>a".."w<n>c".
    private static Question question(int n) {
        return new Question(
                "Quote " + n,
                List.of(
                        new WhoSaidItRound.CharacterOption("w" + n + "a", "Wrong A " + n),
                        new WhoSaidItRound.CharacterOption("c" + n, "Correct " + n),
                        new WhoSaidItRound.CharacterOption("w" + n + "b", "Wrong B " + n),
                        new WhoSaidItRound.CharacterOption("w" + n + "c", "Wrong C " + n)),
                "c" + n);
    }

    private static WhoSaidItMatch newMatch() {
        List<Question> questions = new ArrayList<>();

        for (int n = 1; n <= WhoSaidItMatch.TOTAL_QUESTIONS; n++) {
            questions.add(question(n));
        }

        return new WhoSaidItMatch(questions, Instant.parse("2026-01-01T00:00:00Z"));
    }

    private static void answerAll(WhoSaidItMatch match, int correctCount) {
        for (int n = 1; n <= WhoSaidItMatch.TOTAL_QUESTIONS; n++) {
            match.answer(n, n <= correctCount ? "c" + n : "w" + n + "a");
        }
    }

    @Test
    void shouldHaveTenQuestionsWorthTenPointsEach() {
        assertThat(WhoSaidItMatch.TOTAL_QUESTIONS).isEqualTo(10);
        assertThat(WhoSaidItMatch.POINTS_PER_CORRECT_ANSWER).isEqualTo(10);
        assertThat(WhoSaidItMatch.MAX_SCORE).isEqualTo(100);
    }

    @Test
    void shouldRejectAMatchWithoutTenQuestions() {
        assertThatThrownBy(() -> new WhoSaidItMatch(List.of(question(1)), Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAQuestionWhoseCorrectCharacterIsNotAnOption() {
        Question broken = new Question("Quote", question(1).options(), "missing");
        List<Question> questions = new ArrayList<>(List.of(broken));

        for (int n = 2; n <= 10; n++) {
            questions.add(question(n));
        }

        assertThatThrownBy(() -> new WhoSaidItMatch(questions, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectAQuestionWithRepeatedOptions() {
        Question repeated = new Question(
                "Quote",
                List.of(
                        new WhoSaidItRound.CharacterOption("c1", "A"),
                        new WhoSaidItRound.CharacterOption("c1", "A"),
                        new WhoSaidItRound.CharacterOption("x", "X"),
                        new WhoSaidItRound.CharacterOption("y", "Y")),
                "c1");
        List<Question> questions = new ArrayList<>(List.of(repeated));

        for (int n = 2; n <= 10; n++) {
            questions.add(question(n));
        }

        assertThatThrownBy(() -> new WhoSaidItMatch(questions, Instant.EPOCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldStartAtTheFirstQuestionWithFourOptions() {
        WhoSaidItMatch.QuestionView view = newMatch().currentQuestion();

        assertThat(view.questionNumber()).isEqualTo(1);
        assertThat(view.totalQuestions()).isEqualTo(10);
        assertThat(view.quote()).isEqualTo("Quote 1");
        assertThat(view.options()).hasSize(4);
    }

    @Test
    void shouldScoreTenPointsPerCorrectAnswerAndNothingForWrongOnes() {
        WhoSaidItMatch match = newMatch();

        answerAll(match, 7);

        WhoSaidItMatch.Result result = match.result();

        assertThat(result.correctAnswers()).isEqualTo(7);
        assertThat(result.incorrectAnswers()).isEqualTo(3);
        assertThat(result.score()).isEqualTo(70);
        assertThat(result.maxScore()).isEqualTo(100);
        assertThat(result.percentage()).isEqualTo(70);
    }

    @Test
    void shouldScoreAPerfectMatchAs100() {
        WhoSaidItMatch match = newMatch();

        answerAll(match, 10);

        assertThat(match.result().score()).isEqualTo(100);
        assertThat(match.result().percentage()).isEqualTo(100);
    }

    @Test
    void shouldScoreAMatchOfWrongAnswersAsZero() {
        WhoSaidItMatch match = newMatch();

        answerAll(match, 0);

        assertThat(match.result().score()).isZero();
        assertThat(match.result().percentage()).isZero();
    }

    @Test
    void shouldAdvanceOneQuestionAtATimeAndFinishAfterTheTenth() {
        WhoSaidItMatch match = newMatch();

        for (int n = 1; n < 10; n++) {
            AnswerOutcome outcome = match.answer(n, "c" + n);

            assertThat(outcome.matchFinished()).isFalse();
            assertThat(outcome.next().questionNumber()).isEqualTo(n + 1);
            assertThat(outcome.result()).isNull();
        }

        AnswerOutcome last = match.answer(10, "c10");

        assertThat(last.matchFinished()).isTrue();
        assertThat(last.next()).isNull();
        assertThat(last.result().score()).isEqualTo(100);
        assertThat(match.isFinished()).isTrue();
    }

    @Test
    void shouldNotAcceptAnAnswerToAFutureQuestion() {
        WhoSaidItMatch match = newMatch();

        assertThatThrownBy(() -> match.answer(2, "c2"))
                .isInstanceOfSatisfying(WhoSaidItMatchException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.STALE_SUBMISSION));
        assertThat(match.currentQuestion().questionNumber()).isEqualTo(1);
    }

    @Test
    void shouldNotAcceptAQuestionNumberOutsideTheMatch() {
        WhoSaidItMatch match = newMatch();

        assertThatThrownBy(() -> match.answer(0, "c1")).isInstanceOf(WhoSaidItMatchException.class);
        assertThatThrownBy(() -> match.answer(11, "c1")).isInstanceOf(WhoSaidItMatchException.class);
    }

    @Test
    void shouldNotAcceptACharacterThatIsNotAnOptionOfTheQuestion() {
        WhoSaidItMatch match = newMatch();

        // c2 is a valid character, but not an option of question 1.
        assertThatThrownBy(() -> match.answer(1, "c2"))
                .isInstanceOfSatisfying(WhoSaidItMatchException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.INVALID_OPTION));
        assertThat(match.currentQuestion().questionNumber()).isEqualTo(1);
    }

    @Test
    void shouldNotChangeARecordedAnswer() {
        WhoSaidItMatch match = newMatch();

        match.answer(1, "w1a");

        assertThatThrownBy(() -> match.answer(1, "c1"))
                .isInstanceOfSatisfying(WhoSaidItMatchException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.STALE_SUBMISSION));

        answerAllFrom(match, 2);

        assertThat(match.result().score()).isEqualTo(90);
    }

    @Test
    void shouldAcceptARetryOfTheRecordedAnswerWithoutCountingItTwice() {
        WhoSaidItMatch match = newMatch();

        AnswerOutcome first = match.answer(1, "c1");
        AnswerOutcome retry = match.answer(1, "c1");

        assertThat(retry.next()).isEqualTo(first.next());
        assertThat(match.score()).isEqualTo(10);

        // Even a retry of the last answer after the match finished does not add points.
        answerAllFrom(match, 2);
        AnswerOutcome lastRetry = match.answer(10, "c10");

        assertThat(lastRetry.matchFinished()).isTrue();
        assertThat(lastRetry.result().score()).isEqualTo(100);
        assertThat(match.score()).isEqualTo(100);
    }

    @Test
    void shouldNotAcceptNewAnswersAfterTheMatchFinished() {
        WhoSaidItMatch match = newMatch();

        answerAll(match, 10);

        assertThatThrownBy(match::currentQuestion)
                .isInstanceOfSatisfying(WhoSaidItMatchException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MATCH_FINISHED));
        // Trying to rewrite an answer to raise the score is refused.
        assertThatThrownBy(() -> match.answer(3, "w3a")).isInstanceOf(WhoSaidItMatchException.class);
        assertThat(match.result().score()).isEqualTo(100);
    }

    @Test
    void shouldRefuseTheResultOfAnUnfinishedMatch() {
        WhoSaidItMatch match = newMatch();

        match.answer(1, "c1");

        assertThatThrownBy(match::result)
                .isInstanceOfSatisfying(WhoSaidItMatchException.class,
                        e -> assertThat(e.reason()).isEqualTo(Reason.MATCH_NOT_FINISHED));
    }

    @Test
    void shouldNotRevealTheCorrectAnswerBeforeTheMatchFinishes() {
        WhoSaidItMatch match = newMatch();

        AnswerOutcome outcome = match.answer(1, "w1a");

        // The outcome of an answer has no correctness flag nor correct character.
        assertThat(outcome.getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .doesNotContain("correct", "correctCharacterId", "score");
        assertThat(outcome.result()).isNull();
    }

    @Test
    void shouldListTheCorrectCharacterOfEveryQuestionInTheFinalResult() {
        WhoSaidItMatch match = newMatch();

        answerAll(match, 1);

        List<WhoSaidItMatch.QuestionSummary> summaries = match.result().questions();

        assertThat(summaries).hasSize(10);
        assertThat(summaries.get(0).correct()).isTrue();
        assertThat(summaries.get(1).correct()).isFalse();
        assertThat(summaries.get(1).correctCharacterName()).isEqualTo("Correct 2");
        assertThat(summaries.get(1).submittedCharacterName()).isEqualTo("Wrong A 2");
    }

    private static void answerAllFrom(WhoSaidItMatch match, int from) {
        for (int n = from; n <= WhoSaidItMatch.TOTAL_QUESTIONS; n++) {
            match.answer(n, "c" + n);
        }
    }
}
