package com.wunderlore.game.whosaidit;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.wunderlore.game.whosaidit.WhoSaidItMatchException.Reason;

// The state of one competitive match: ten quotes fixed at creation, the answers received so far
// and the score. The score and the correctness of every answer are only decided here, from the
// character ids the match receives, so nothing a client sends can set them.
//
// What the player may see is limited by construction: a QuestionView carries the quote and its
// four options, never which one is right. The correct character appears only in the result of a
// finished match.
//
// Not thread-safe by itself: WhoSaidItMatchRegistry only touches a match while holding its monitor.
public final class WhoSaidItMatch {

    public static final int TOTAL_QUESTIONS = 10;

    public static final int OPTIONS_PER_QUESTION = 4;

    public static final int POINTS_PER_CORRECT_ANSWER = 10;

    public static final int MAX_SCORE = TOTAL_QUESTIONS * POINTS_PER_CORRECT_ANSWER;

    // One quote with its four options (the correct one among them). Never leaves the backend.
    public record Question(
            String quote,
            List<WhoSaidItRound.CharacterOption> options,
            String correctCharacterId) {

        public Question {
            options = List.copyOf(options);
        }
    }

    public record QuestionView(
            int questionNumber,
            int totalQuestions,
            String quote,
            List<WhoSaidItRound.CharacterOption> options) {
    }

    // next is set while the match goes on; result when this answer was the last one.
    public record AnswerOutcome(
            int questionNumber,
            String submittedCharacterId,
            boolean matchFinished,
            QuestionView next,
            Result result) {
    }

    public record QuestionSummary(
            int questionNumber,
            String quote,
            String submittedCharacterName,
            String correctCharacterName,
            boolean correct) {
    }

    public record Result(
            int totalQuestions,
            int correctAnswers,
            int incorrectAnswers,
            int score,
            int maxScore,
            int percentage,
            List<QuestionSummary> questions) {
    }

    private final List<Question> questions;
    private final List<String> submitted = new ArrayList<>();
    private final Instant createdAt;

    private int correctAnswers;

    WhoSaidItMatch(List<Question> questions, Instant createdAt) {
        if (questions.size() != TOTAL_QUESTIONS) {
            throw new IllegalArgumentException("A match needs exactly " + TOTAL_QUESTIONS + " questions");
        }

        for (Question question : questions) {
            boolean correctOffered = question.options().stream()
                    .anyMatch(option -> option.id().equals(question.correctCharacterId()));
            long distinctOptions = question.options().stream()
                    .map(WhoSaidItRound.CharacterOption::id)
                    .distinct()
                    .count();

            if (!correctOffered || distinctOptions != OPTIONS_PER_QUESTION
                    || question.options().size() != OPTIONS_PER_QUESTION) {
                throw new IllegalArgumentException(
                        "Each question needs four distinct options, one of them correct");
            }
        }

        this.questions = List.copyOf(questions);
        this.createdAt = createdAt;
    }

    Instant createdAt() {
        return createdAt;
    }

    boolean isFinished() {
        return submitted.size() == questions.size();
    }

    int score() {
        return correctAnswers * POINTS_PER_CORRECT_ANSWER;
    }

    QuestionView currentQuestion() {
        if (isFinished()) {
            throw new WhoSaidItMatchException(Reason.MATCH_FINISHED);
        }

        return view(submitted.size());
    }

    // questionNumber says what the client believes it is answering. Only the question awaiting an
    // answer is accepted. A question already answered is accepted again, without counting, only
    // when the same character is sent: that is a retry after a lost response. A different
    // character for an answered question, or any later question, is refused, so an answer cannot
    // be changed or counted twice, and a future question cannot be answered early.
    AnswerOutcome answer(int questionNumber, String characterId) {
        if (questionNumber < 1 || questionNumber > questions.size()) {
            throw new WhoSaidItMatchException(Reason.STALE_SUBMISSION);
        }

        Question question = questions.get(questionNumber - 1);

        if (question.options().stream().noneMatch(option -> option.id().equals(characterId))) {
            throw new WhoSaidItMatchException(Reason.INVALID_OPTION);
        }

        int awaiting = submitted.size() + 1;

        if (questionNumber < awaiting) {
            if (!submitted.get(questionNumber - 1).equals(characterId)) {
                throw new WhoSaidItMatchException(Reason.STALE_SUBMISSION);
            }

            return outcome(questionNumber, characterId);
        }

        if (questionNumber > awaiting) {
            throw new WhoSaidItMatchException(Reason.STALE_SUBMISSION);
        }

        submitted.add(characterId);

        if (question.correctCharacterId().equals(characterId)) {
            correctAnswers++;
        }

        return outcome(questionNumber, characterId);
    }

    Result result() {
        if (!isFinished()) {
            throw new WhoSaidItMatchException(Reason.MATCH_NOT_FINISHED);
        }

        List<QuestionSummary> summaries = new ArrayList<>();

        for (int i = 0; i < questions.size(); i++) {
            Question question = questions.get(i);
            String chosen = submitted.get(i);

            summaries.add(new QuestionSummary(
                    i + 1,
                    question.quote(),
                    nameOf(question, chosen),
                    nameOf(question, question.correctCharacterId()),
                    question.correctCharacterId().equals(chosen)));
        }

        return new Result(
                questions.size(),
                correctAnswers,
                questions.size() - correctAnswers,
                score(),
                MAX_SCORE,
                (int) Math.round(score() * 100.0 / MAX_SCORE),
                List.copyOf(summaries));
    }

    private AnswerOutcome outcome(int questionNumber, String characterId) {
        boolean finished = isFinished();

        // A replay of an earlier answer reports where the match stands now.
        return new AnswerOutcome(
                questionNumber,
                characterId,
                finished,
                finished ? null : view(submitted.size()),
                finished ? result() : null);
    }

    private QuestionView view(int index) {
        Question question = questions.get(index);

        return new QuestionView(index + 1, questions.size(), question.quote(), question.options());
    }

    private static String nameOf(Question question, String characterId) {
        return question.options().stream()
                .filter(option -> option.id().equals(characterId))
                .findFirst()
                .orElseThrow()
                .name();
    }
}
