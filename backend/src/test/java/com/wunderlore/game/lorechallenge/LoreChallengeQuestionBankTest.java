package com.wunderlore.game.lorechallenge;

import static com.wunderlore.game.lorechallenge.LoreChallengeTestData.question;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LoreChallengeQuestionBankTest {

    private final LoreChallengeQuestionBank bank = new LoreChallengeQuestionBank();

    @Test
    void shouldHoldTenQuestionsForEveryCategoryAndDifficulty() {
        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                assertThat(bank.count(category, difficulty))
                        .as("%s %s", category, difficulty)
                        .isEqualTo(10);
            }
        }
    }

    @Test
    void shouldFileEveryQuestionUnderItsOwnCategoryAndDifficulty() {
        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                for (LoreChallengeQuestion question : bank.find(category, difficulty)) {
                    assertThat(question.category()).isEqualTo(category);
                    assertThat(question.difficulty()).isEqualTo(difficulty);
                    assertThat(question.id()).startsWith(
                            category.name().toLowerCase() + "-" + difficulty.name().toLowerCase() + "-");
                }
            }
        }
    }

    @Test
    void shouldHaveUniqueIdsPromptsAndFourDistinctOptions() {
        Set<String> ids = new HashSet<>();
        Set<String> prompts = new HashSet<>();

        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                for (LoreChallengeQuestion question : bank.find(category, difficulty)) {
                    assertThat(ids.add(question.id())).as(question.id()).isTrue();
                    assertThat(prompts.add(question.prompt())).as(question.id()).isTrue();

                    Set<String> options = new HashSet<>(question.distractors());

                    options.add(question.answer());
                    assertThat(options).as(question.id()).hasSize(4);
                    assertThat(question.explanation()).isNotBlank();
                }
            }
        }

        assertThat(ids).hasSize(150);
    }

    @Test
    void shouldRejectDuplicateIds() {
        List<LoreChallengeQuestion> questions = new ArrayList<>();

        questions.add(question("same", LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY));
        questions.add(question("same", LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY));

        assertThatThrownBy(() -> new LoreChallengeQuestionBank(questions))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate question id");
    }

    @Test
    void shouldRejectMalformedQuestions() {
        assertThatThrownBy(() -> new LoreChallengeQuestion(
                "q", LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY,
                "Prompt?", "A", List.of("A", "B", "C"), "Because."))
                .hasMessageContaining("duplicate options");

        assertThatThrownBy(() -> new LoreChallengeQuestion(
                "q", LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY,
                "Prompt?", "A", List.of("B", "C"), "Because."))
                .hasMessageContaining("exactly 3 distractors");

        assertThatThrownBy(() -> new LoreChallengeQuestion(
                "q", LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY,
                " ", "A", List.of("B", "C", "D"), "Because."))
                .hasMessageContaining("blank prompt");

        assertThatThrownBy(() -> new LoreChallengeQuestion(
                "Bad Id", LoreChallengeCategory.PLACES, LoreChallengeDifficulty.EASY,
                "Prompt?", "A", List.of("B", "C", "D"), "Because."))
                .hasMessageContaining("Question id must match");
    }
}
