package com.wunderlore.game.whichcharacter;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;

@Service
public class WhichCharacterScoringService {

    /*
     * This order is the final tie-break rule:
     * it is used only when both total score and primary selections are equal.
     */
    private static final List<CharacterId> TIE_BREAK_ORDER = List.of(
            CharacterId.FRODO,
            CharacterId.SAM,
            CharacterId.ARAGORN,
            CharacterId.GANDALF,
            CharacterId.SAURON,
            CharacterId.GOLLUM,
            CharacterId.LEGOLAS,
            CharacterId.GIMLI,
            CharacterId.GALADRIEL,
            CharacterId.BOROMIR);

    public CharacterResult calculateResult(List<QuizAnswer> answers) {
        validateAnswers(answers);

        Map<CharacterId, Integer> scores = new EnumMap<>(CharacterId.class);
        Map<CharacterId, Integer> primarySelections = new EnumMap<>(CharacterId.class);

        for (CharacterId characterId : CharacterId.values()) {
            scores.put(characterId, 0);
            primarySelections.put(characterId, 0);
        }

        Map<String, QuizQuestion> questionsById = WhichCharacterQuiz.QUESTIONS
                .stream()
                .collect(java.util.stream.Collectors.toMap(
                        QuizQuestion::id,
                        question -> question));

        for (QuizAnswer answer : answers) {
            QuizQuestion question = questionsById.get(answer.questionId());

            QuizOption selectedOption = question.options()
                    .stream()
                    .filter(option -> option.id().equals(answer.answerId()))
                    .findFirst()
                    .orElseThrow(() -> new WhichCharacterInvalidAnswersException(
                            "Answer does not belong to the specified question."));

            CharacterId primary = selectedOption.primaryCharacter();
            CharacterId secondary = selectedOption.secondaryCharacter();

            scores.merge(primary, 2, Integer::sum);
            scores.merge(secondary, 1, Integer::sum);

            primarySelections.merge(primary, 1, Integer::sum);
        }

        CharacterId winner = determineWinner(scores, primarySelections);

        return WhichCharacterQuiz.CHARACTERS.get(winner);
    }

    private void validateAnswers(List<QuizAnswer> answers) {
        if (answers == null || answers.size() != WhichCharacterQuiz.QUESTIONS.size()) {
            throw new WhichCharacterInvalidAnswersException(
                    "Exactly " + WhichCharacterQuiz.QUESTIONS.size()
                            + " answers are required.");
        }

        Set<String> answeredQuestionIds = new HashSet<>();

        Set<String> validQuestionIds = WhichCharacterQuiz.QUESTIONS
                .stream()
                .map(QuizQuestion::id)
                .collect(java.util.stream.Collectors.toSet());

        for (QuizAnswer answer : answers) {
            if (answer == null
                    || answer.questionId() == null
                    || answer.questionId().isBlank()
                    || answer.answerId() == null
                    || answer.answerId().isBlank()) {
                throw new WhichCharacterInvalidAnswersException(
                        "Every answer must contain a valid question ID and answer ID.");
            }

            if (!validQuestionIds.contains(answer.questionId())) {
                throw new WhichCharacterInvalidAnswersException("Unknown question ID.");
            }

            if (!answeredQuestionIds.add(answer.questionId())) {
                throw new WhichCharacterInvalidAnswersException(
                        "Each question must be answered exactly once.");
            }
        }
    }

    private CharacterId determineWinner(
            Map<CharacterId, Integer> scores,
            Map<CharacterId, Integer> primarySelections) {
        CharacterId winner = null;
        int highestScore = -1;
        int highestPrimarySelections = -1;

        for (CharacterId characterId : TIE_BREAK_ORDER) {
            int score = scores.get(characterId);
            int primaryCount = primarySelections.get(characterId);

            if (score > highestScore
                    || (score == highestScore
                            && primaryCount > highestPrimarySelections)) {

                winner = characterId;
                highestScore = score;
                highestPrimarySelections = primaryCount;
            }
        }

        return winner;
    }
}