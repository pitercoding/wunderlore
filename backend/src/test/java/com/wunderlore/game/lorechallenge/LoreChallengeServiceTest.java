package com.wunderlore.game.lorechallenge;

import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.START;
import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.wunderlore.game.lorechallenge.LoreChallengeException.Reason;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.AnswerOutcome;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.QuestionView;

class LoreChallengeServiceTest {

    private static final Clock CLOCK = Clock.fixed(START, ZoneOffset.UTC);

    private final LoreChallengeQuestionBank realBank = new LoreChallengeQuestionBank();

    private LoreChallengeService service(LoreChallengeQuestionBank bank, long seed) {
        return new LoreChallengeService(bank, new Random(seed), CLOCK);
    }

    // The ids of the ten questions served, in order. The match is played with wrong answers,
    // which is the only way to read the sequence through the public API.
    private List<QuestionView> serve(LoreChallengeMatch match) {
        List<QuestionView> served = new ArrayList<>();

        while (!match.isFinished()) {
            QuestionView view = match.currentQuestion();

            served.add(view);

            // "a" to "d": whichever it is, the answer is registered and the sequence advances.
            AnswerOutcome outcome = match.answer(view.questionId(), "a", START);

            assertThat(outcome.answered()).isEqualTo(view.number());
        }

        return served;
    }

    @ParameterizedTest
    @EnumSource(LoreChallengeCategory.class)
    void shouldSelectOnlyTheChosenCategory(LoreChallengeCategory category) {
        LoreChallengeMatch match = service(realBank, 1).createMatch(category, LoreChallengeDifficulty.MEDIUM);

        List<QuestionView> served = serve(match);

        assertThat(served).hasSize(10);
        assertThat(served).allSatisfy(view -> {
            assertThat(view.questionCategory()).isEqualTo(category);
            assertThat(view.category()).isEqualTo(category.name());
        });
    }

    @ParameterizedTest
    @EnumSource(LoreChallengeDifficulty.class)
    void shouldSelectOnlyTheChosenDifficulty(LoreChallengeDifficulty difficulty) {
        LoreChallengeMatch match = service(realBank, 2).createMatch(null, difficulty);

        for (QuestionView view : serve(match)) {
            assertThat(view.difficulty()).isEqualTo(difficulty);
            // The id encodes the difficulty it is filed under.
            assertThat(view.questionId()).contains("-" + difficulty.name().toLowerCase() + "-");
        }
    }

    @Test
    void shouldSpreadAllCategoriesEvenly() {
        for (long seed = 0; seed < 25; seed++) {
            LoreChallengeMatch match = service(realBank, seed).createMatch(null, LoreChallengeDifficulty.EASY);
            Map<LoreChallengeCategory, Integer> perCategory = new EnumMap<>(LoreChallengeCategory.class);

            for (QuestionView view : serve(match)) {
                perCategory.merge(view.questionCategory(), 1, Integer::sum);
            }

            assertThat(perCategory).hasSize(5);
            assertThat(perCategory.values()).containsOnly(2);
            assertThat(match.result().category()).isEqualTo("ALL");
        }
    }

    @Test
    void shouldNeverRepeatAQuestionWithinAMatch() {
        for (long seed = 0; seed < 25; seed++) {
            for (LoreChallengeCategory category : new LoreChallengeCategory[] {null, LoreChallengeCategory.PLACES}) {
                LoreChallengeMatch match = service(realBank, seed).createMatch(category, LoreChallengeDifficulty.HARD);
                Set<String> ids = new HashSet<>();

                for (QuestionView view : serve(match)) {
                    assertThat(ids.add(view.questionId())).as(view.questionId()).isTrue();
                }

                assertThat(ids).hasSize(10);
            }
        }
    }

    @Test
    void shouldOfferFourDistinctOptionsWithStableIds() {
        LoreChallengeMatch match = service(realBank, 3).createMatch(null, LoreChallengeDifficulty.EASY);

        for (QuestionView view : serve(match)) {
            assertThat(view.options()).extracting(LoreChallengeMatch.Option::id)
                    .containsExactly("a", "b", "c", "d");
            assertThat(view.options()).extracting(LoreChallengeMatch.Option::text).doesNotHaveDuplicates();
        }
    }

    @Test
    void shouldMakeTheCorrectOptionTheOneHoldingTheAnswerText() {
        LoreChallengeMatch match = service(realBank, 4).createMatch(null, LoreChallengeDifficulty.MEDIUM);

        while (!match.isFinished()) {
            QuestionView view = match.currentQuestion();
            LoreChallengeQuestion curated = findInBank(view.questionId());
            String correctOptionId = view.options().stream()
                    .filter(option -> option.text().equals(curated.answer()))
                    .findFirst().orElseThrow().id();

            AnswerOutcome outcome = match.answer(view.questionId(), correctOptionId, START);

            assertThat(outcome.correct()).isTrue();
            assertThat(outcome.correctOptionId()).isEqualTo(correctOptionId);
        }

        assertThat(match.result().percentage()).isEqualTo(100);
        assertThat(match.result().score()).isEqualTo(200);
    }

    @Test
    void shouldVaryTheQuestionsAndTheirOrderBetweenMatches() {
        List<String> first = serve(service(realBank, 10).createMatch(null, LoreChallengeDifficulty.EASY))
                .stream().map(QuestionView::questionId).toList();
        List<String> second = serve(service(realBank, 11).createMatch(null, LoreChallengeDifficulty.EASY))
                .stream().map(QuestionView::questionId).toList();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void shouldMakeUpTheDifferenceFromOtherCategoriesWhenOneRunsShort() {
        // Characters has 1 question, Places 2, Peoples 7: exactly ten, so all of them are used.
        List<LoreChallengeQuestion> questions = new ArrayList<>();

        questions.add(question("c-1", LoreChallengeCategory.CHARACTERS, LoreChallengeDifficulty.EASY));
        for (int i = 1; i <= 2; i++) {
            questions.add(question("pl-" + i, LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY));
        }
        for (int i = 1; i <= 7; i++) {
            questions.add(question("pe-" + i, LoreChallengeCategory.PEOPLES, LoreChallengeDifficulty.EASY));
        }

        LoreChallengeMatch match = service(new LoreChallengeQuestionBank(questions), 5)
                .createMatch(null, LoreChallengeDifficulty.EASY);
        List<QuestionView> served = serve(match);

        assertThat(served).hasSize(10);
        assertThat(served).extracting(QuestionView::questionId).doesNotHaveDuplicates();
    }

    @Test
    void shouldRefuseAMatchWhenThereAreNotTenQuestions() {
        List<LoreChallengeQuestion> questions = new ArrayList<>();

        for (int i = 1; i <= 9; i++) {
            questions.add(question("q-" + i, LoreChallengeCategory.EVENTS, LoreChallengeDifficulty.HARD));
        }

        LoreChallengeService service = service(new LoreChallengeQuestionBank(questions), 6);

        assertThatThrownBy(() -> service.createMatch(LoreChallengeCategory.EVENTS, LoreChallengeDifficulty.HARD))
                .isInstanceOfSatisfying(LoreChallengeException.class,
                        exception -> assertThat(exception.reason()).isEqualTo(Reason.NOT_ENOUGH_QUESTIONS));
        // Nothing for another difficulty or category either: never a silently shorter match.
        assertThatThrownBy(() -> service.createMatch(null, LoreChallengeDifficulty.EASY))
                .isInstanceOf(LoreChallengeException.class);
    }

    @Test
    void shouldDrawTenOfTheTwentyQuestionsOfACategoryAndDifficulty() {
        Set<String> bankIds = new HashSet<>();
        Set<String> seen = new HashSet<>();

        realBank.find(LoreChallengeCategory.PLACES, LoreChallengeDifficulty.HARD)
                .forEach(question -> bankIds.add(question.id()));
        assertThat(bankIds).hasSize(20);

        for (long seed = 0; seed < 40; seed++) {
            List<QuestionView> served = serve(service(realBank, seed)
                    .createMatch(LoreChallengeCategory.PLACES, LoreChallengeDifficulty.HARD));

            assertThat(served).hasSize(10);
            served.forEach(view -> seen.add(view.questionId()));
        }

        // Every question served belongs to the pool, and the draw is not always the same ten.
        assertThat(bankIds).containsAll(seen);
        assertThat(seen.size()).isGreaterThan(10);
    }

    @Test
    void shouldDrawDifferentQuestionsOfEachCategoryAcrossAllMatches() {
        Map<LoreChallengeCategory, Set<String>> seen = new EnumMap<>(LoreChallengeCategory.class);

        for (long seed = 0; seed < 40; seed++) {
            for (QuestionView view : serve(service(realBank, seed).createMatch(null, LoreChallengeDifficulty.MEDIUM))) {
                seen.computeIfAbsent(view.questionCategory(), key -> new HashSet<>()).add(view.questionId());
            }
        }

        assertThat(seen).hasSize(5);
        assertThat(seen.values()).allSatisfy(ids -> assertThat(ids.size()).isGreaterThan(2));
    }

    @Test
    void shouldStampTheMatchWithTheClockTime() {
        LoreChallengeMatch match = service(realBank, 7).createMatch(null, LoreChallengeDifficulty.EASY);

        assertThat(match.createdAt()).isEqualTo(START);
    }

    private LoreChallengeQuestion findInBank(String id) {
        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                for (LoreChallengeQuestion question : realBank.find(category, difficulty)) {
                    if (question.id().equals(id)) {
                        return question;
                    }
                }
            }
        }

        throw new AssertionError("Unknown question " + id);
    }
}
