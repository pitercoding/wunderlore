package com.wunderlore.game.whichcharacter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class WhichCharacterScoringServiceTest {

    private final WhichCharacterScoringService service = new WhichCharacterScoringService();

    // Helpers

    private static QuizAnswer answer(String optionId) {
        return new QuizAnswer(optionId.substring(0, 3), optionId);
    }

    private static List<QuizAnswer> answers(String... optionIds) {
        return Arrays.stream(optionIds)
                .map(WhichCharacterScoringServiceTest::answer)
                .toList();
    }

    private static List<QuizAnswer> validAnswers() {
        return answers("q01_a", "q02_a", "q03_a", "q04_a", "q05_a",
                "q06_a", "q07_a", "q08_a", "q09_a", "q10_a");
    }

    private static List<QuizAnswer> withReplacedAt(int index, QuizAnswer replacement) {
        List<QuizAnswer> list = new ArrayList<>(validAnswers());
        list.set(index, replacement);
        return list;
    }

    /**
     * Builds an answer set from the real catalog that favors the target:
     * for each question, picks the option giving the target the most points.
     */
    private static List<QuizAnswer> answersFavoring(CharacterId target) {
        List<QuizAnswer> result = new ArrayList<>();

        for (QuizQuestion question : WhichCharacterQuiz.QUESTIONS) {
            QuizOption best = question.options().get(0);
            int bestPoints = -1;

            for (QuizOption option : question.options()) {
                int points = (option.primaryCharacter() == target ? 2 : 0)
                        + (option.secondaryCharacter() == target ? 1 : 0);

                if (points > bestPoints) {
                    best = option;
                    bestPoints = points;
                }
            }

            result.add(new QuizAnswer(question.id(), best.id()));
        }

        return result;
    }

    private void assertRejected(List<QuizAnswer> input) {
        assertThrows(IllegalArgumentException.class, () -> service.calculateResult(input));
    }

    // Valid results

    @Test
    void validAnswersReturnNonNullResult() {
        assertNotNull(service.calculateResult(validAnswers()));
    }

    @Test
    void resultBelongsToCharacterCatalog() {
        CharacterResult result = service.calculateResult(validAnswers());

        assertTrue(WhichCharacterQuiz.CHARACTERS.containsValue(result));
        assertEquals(WhichCharacterQuiz.CHARACTERS.get(result.id()), result);
    }

    // Scoring

    @Test
    void awardsTwoPointsToPrimaryAndOnePointToSecondary() {
        // Scores: LEGOLAS 5 (2 primary), FRODO/SAM/GANDALF/SAURON 4.
        // Legolas only wins when primary = 2 and secondary = 1
        // (1/1, 1/2, 2/0 and 0/1 weightings all produce a different winner).
        List<QuizAnswer> input = answers("q01_a", "q02_c", "q03_a", "q04_c", "q05_a",
                "q06_b", "q07_b", "q08_c", "q09_c", "q10_b");

        assertEquals(CharacterId.LEGOLAS, service.calculateResult(input).id());
    }

    @Test
    void highestTotalScoreWins() {
        // ARAGORN has the highest total (6) against LEGOLAS/GALADRIEL (5).
        assertEquals(CharacterId.ARAGORN, service.calculateResult(validAnswers()).id());
    }

    @Test
    void tiedTotalScoreIsResolvedByMorePrimarySelections() {
        // GANDALF, GALADRIEL and BOROMIR all total 5.
        // GANDALF has 1 primary and comes first in canonical order,
        // but GALADRIEL and BOROMIR have 2 primaries, so GALADRIEL (earlier of the two) wins.
        List<QuizAnswer> input = answers("q01_a", "q02_a", "q03_a", "q04_a", "q05_d",
                "q06_b", "q07_d", "q08_a", "q09_b", "q10_d");

        assertEquals(CharacterId.GALADRIEL, service.calculateResult(input).id());
    }

    @Test
    void exactTieUsesCanonicalOrder() {
        // SAM and GANDALF both have 5 points and 2 primary selections.
        // SAM precedes GANDALF in canonical order.
        List<QuizAnswer> input = answers("q01_b", "q02_a", "q03_c", "q04_a", "q05_d",
                "q06_d", "q07_d", "q08_d", "q09_b", "q10_a");

        assertEquals(CharacterId.SAM, service.calculateResult(input).id());
    }

    @Test
    void exactTieAmongManyCharactersPicksFirstInCanonicalOrder() {
        // FRODO, GANDALF, LEGOLAS and BOROMIR are tied on score and primary selections.
        List<QuizAnswer> input = answers("q01_a", "q02_c", "q03_b", "q04_b", "q05_b",
                "q06_a", "q07_c", "q08_b", "q09_b", "q10_c");

        assertEquals(CharacterId.FRODO, service.calculateResult(input).id());
    }

    @Test
    void answerOrderDoesNotAffectResult() {
        List<QuizAnswer> reversed = new ArrayList<>(validAnswers());
        java.util.Collections.reverse(reversed);

        assertEquals(service.calculateResult(validAnswers()), service.calculateResult(reversed));
    }

    @Test
    void allTenCharactersCanBeObtained() {
        Set<CharacterId> obtained = EnumSet.noneOf(CharacterId.class);

        for (CharacterId target : CharacterId.values()) {
            CharacterResult result = service.calculateResult(answersFavoring(target));

            assertEquals(target, result.id(),
                    "Answers favoring " + target + " should produce " + target);
            obtained.add(result.id());
        }

        assertEquals(EnumSet.allOf(CharacterId.class), obtained);
    }

    // Validation

    @Test
    void rejectsNullAnswerList() {
        assertRejected(null);
    }

    @Test
    void rejectsFewerThanTenAnswers() {
        assertRejected(validAnswers().subList(0, 9));
        assertRejected(List.of());
    }

    @Test
    void rejectsMoreThanTenAnswers() {
        List<QuizAnswer> input = new ArrayList<>(validAnswers());
        input.add(answer("q01_a"));

        assertRejected(input);
    }

    @Test
    void rejectsDuplicateQuestionIds() {
        assertRejected(withReplacedAt(9, answer("q01_b")));
    }

    @Test
    void rejectsUnknownQuestionId() {
        assertRejected(withReplacedAt(0, new QuizAnswer("q99", "q99_a")));
    }

    @Test
    void rejectsAnswerIdBelongingToAnotherQuestion() {
        assertRejected(withReplacedAt(0, new QuizAnswer("q01", "q02_a")));
    }

    @Test
    void rejectsUnknownAnswerIdForKnownQuestion() {
        assertRejected(withReplacedAt(0, new QuizAnswer("q01", "q01_z")));
    }

    @Test
    void rejectsNullAnswerEntry() {
        assertRejected(withReplacedAt(0, null));
    }

    @Test
    void rejectsNullQuestionId() {
        assertRejected(withReplacedAt(0, new QuizAnswer(null, "q01_a")));
    }

    @Test
    void rejectsBlankQuestionId() {
        assertRejected(withReplacedAt(0, new QuizAnswer("", "q01_a")));
        assertRejected(withReplacedAt(0, new QuizAnswer("   ", "q01_a")));
    }

    @Test
    void rejectsNullAnswerId() {
        assertRejected(withReplacedAt(0, new QuizAnswer("q01", null)));
    }

    @Test
    void rejectsBlankAnswerId() {
        assertRejected(withReplacedAt(0, new QuizAnswer("q01", "")));
        assertRejected(withReplacedAt(0, new QuizAnswer("q01", "   ")));
    }

    // QuizAnswer

    @Test
    void quizAnswerExposesItsComponentsAndHasValueEquality() {
        QuizAnswer first = new QuizAnswer("q01", "q01_a");

        assertEquals("q01", first.questionId());
        assertEquals("q01_a", first.answerId());
        assertEquals(new QuizAnswer("q01", "q01_a"), first);
        assertEquals(new QuizAnswer("q01", "q01_a").hashCode(), first.hashCode());
    }
}
