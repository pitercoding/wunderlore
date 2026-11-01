package com.wunderlore.game.whichcharacter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class WhichCharacterQuizTest {

    private static final int EXPECTED_QUESTIONS = 10;
    private static final int EXPECTED_OPTIONS_PER_QUESTION = 4;
    private static final int EXPECTED_TOTAL_OPTIONS = 40;
    private static final int EXPECTED_PRIMARY_APPEARANCES = 4;

    private static List<QuizOption> allOptions() {
        return WhichCharacterQuiz.QUESTIONS.stream()
                .flatMap(question -> question.options().stream())
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    @Test
    void catalogHasExactlyTenQuestions() {
        assertEquals(EXPECTED_QUESTIONS, WhichCharacterQuiz.QUESTIONS.size(),
                "The quiz must contain exactly 10 questions");
    }

    @Test
    void everyQuestionHasNonBlankIdAndText() {
        for (QuizQuestion question : WhichCharacterQuiz.QUESTIONS) {
            assertTrue(!isBlank(question.id()),
                    "Question has a blank ID: " + question);
            assertTrue(!isBlank(question.text()),
                    "Question " + question.id() + " has blank text");
        }
    }

    @Test
    void questionIdsAreUnique() {
        Set<String> uniqueIds = WhichCharacterQuiz.QUESTIONS.stream()
                .map(QuizQuestion::id)
                .collect(Collectors.toSet());

        assertEquals(WhichCharacterQuiz.QUESTIONS.size(), uniqueIds.size(),
                "Question IDs must be unique");
    }

    @Test
    void everyQuestionHasExactlyFourAlternatives() {
        for (QuizQuestion question : WhichCharacterQuiz.QUESTIONS) {
            assertEquals(EXPECTED_OPTIONS_PER_QUESTION, question.options().size(),
                    "Question " + question.id() + " must have exactly 4 alternatives");
        }
    }

    @Test
    void everyAlternativeHasNonBlankIdAndText() {
        for (QuizQuestion question : WhichCharacterQuiz.QUESTIONS) {
            for (QuizOption option : question.options()) {
                assertTrue(!isBlank(option.id()),
                        "Question " + question.id() + " has an alternative with a blank ID: " + option);
                assertTrue(!isBlank(option.text()),
                        "Alternative " + option.id() + " has blank text");
            }
        }
    }

    @Test
    void alternativeIdsAreUniqueAcrossTheEntireQuiz() {
        List<QuizOption> options = allOptions();
        Set<String> uniqueIds = options.stream()
                .map(QuizOption::id)
                .collect(Collectors.toSet());

        assertEquals(options.size(), uniqueIds.size(),
                "Alternative IDs must be unique across the entire quiz");
    }

    @Test
    void everyAlternativeHasAPrimaryCharacter() {
        for (QuizOption option : allOptions()) {
            assertNotNull(option.primaryCharacter(),
                    "Alternative " + option.id() + " has no primary character");
        }
    }

    @Test
    void everyAlternativeHasASecondaryCharacter() {
        for (QuizOption option : allOptions()) {
            assertNotNull(option.secondaryCharacter(),
                    "Alternative " + option.id() + " has no secondary character");
        }
    }

    @Test
    void primaryAndSecondaryCharactersAreNeverTheSame() {
        for (QuizOption option : allOptions()) {
            assertTrue(option.primaryCharacter() != option.secondaryCharacter(),
                    "Alternative " + option.id() + " uses " + option.primaryCharacter()
                            + " as both primary and secondary character");
        }
    }

    @Test
    void everyCharacterIdHasACorrespondingResult() {
        for (CharacterId id : CharacterId.values()) {
            assertTrue(WhichCharacterQuiz.CHARACTERS.containsKey(id),
                    "No CharacterResult defined for " + id);
        }
        assertEquals(CharacterId.values().length, WhichCharacterQuiz.CHARACTERS.size(),
                "CHARACTERS must contain exactly one result per CharacterId");
    }

    @Test
    void thereAreExactlyTenPossibleCharacters() {
        assertEquals(10, CharacterId.values().length,
                "The quiz must have exactly 10 possible characters");
    }

    @Test
    void everyCharacterResultHasNonBlankNameAndDescription() {
        WhichCharacterQuiz.CHARACTERS.forEach((id, result) -> {
            assertNotNull(result, "CharacterResult for " + id + " is null");
            assertTrue(!isBlank(result.name()),
                    "CharacterResult for " + id + " has a blank name");
            assertTrue(!isBlank(result.description()),
                    "CharacterResult for " + id + " has a blank description");
        });
    }

    @Test
    void everyCharacterAppearsExactlyFourTimesAsPrimary() {
        Map<CharacterId, Long> counts = countBy(QuizOption::primaryCharacter);

        for (CharacterId id : CharacterId.values()) {
            assertEquals(EXPECTED_PRIMARY_APPEARANCES, counts.get(id).longValue(),
                    id + " must appear exactly 4 times as a primary character");
        }
    }

    @Test
    void everyCharacterAppearsAtLeastOnceAsSecondary() {
        Map<CharacterId, Long> counts = countBy(QuizOption::secondaryCharacter);

        for (CharacterId id : CharacterId.values()) {
            assertTrue(counts.get(id) >= 1,
                    id + " must appear at least once as a secondary character");
        }
    }

    @Test
    void quizHasExactlyFortyAlternativesOverall() {
        assertEquals(EXPECTED_TOTAL_OPTIONS, allOptions().size(),
                "The quiz must contain exactly 40 alternatives overall");
    }

    private static Map<CharacterId, Long> countBy(
            java.util.function.Function<QuizOption, CharacterId> extractor) {
        Map<CharacterId, Long> counts = new EnumMap<>(CharacterId.class);
        Arrays.stream(CharacterId.values()).forEach(id -> counts.put(id, 0L));
        allOptions().forEach(option -> counts.merge(extractor.apply(option), 1L, Long::sum));
        return counts;
    }
}
