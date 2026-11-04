package com.wunderlore.game.lorechallenge;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import com.wunderlore.game.lorechallenge.LoreChallengeException.Reason;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.Option;
import com.wunderlore.game.lorechallenge.LoreChallengeMatch.PlayedQuestion;

// Builds a match from the curated bank. Pure selection logic: it keeps no state of its own.
@Service
public class LoreChallengeService {

    public static final int QUESTIONS_PER_MATCH = 10;

    private static final String[] OPTION_IDS = {"a", "b", "c", "d"};

    private final LoreChallengeQuestionBank bank;
    private final RandomGenerator random;
    private final Clock clock;

    public LoreChallengeService(
            LoreChallengeQuestionBank bank,
            RandomGenerator random,
            Clock clock) {
        this.bank = bank;
        this.random = random;
        this.clock = clock;
    }

    // category null means every category. Throws NOT_ENOUGH_QUESTIONS rather than building a
    // shorter match.
    public LoreChallengeMatch createMatch(
            LoreChallengeCategory category,
            LoreChallengeDifficulty difficulty) {
        List<LoreChallengeCategory> categories = category == null
                ? List.of(LoreChallengeCategory.values())
                : List.of(category);

        Map<LoreChallengeCategory, Deque<LoreChallengeQuestion>> pools =
                new EnumMap<>(LoreChallengeCategory.class);
        int available = 0;

        for (LoreChallengeCategory candidate : categories) {
            List<LoreChallengeQuestion> questions = new ArrayList<>(bank.find(candidate, difficulty));

            if (questions.isEmpty()) {
                continue;
            }

            shuffle(questions);
            pools.put(candidate, new ArrayDeque<>(questions));
            available += questions.size();
        }

        if (available < QUESTIONS_PER_MATCH) {
            throw new LoreChallengeException(Reason.NOT_ENOUGH_QUESTIONS);
        }

        List<LoreChallengeQuestion> selected = pickBalanced(pools);

        shuffle(selected);

        List<PlayedQuestion> played = new ArrayList<>();

        for (LoreChallengeQuestion question : selected) {
            played.add(play(question));
        }

        return new LoreChallengeMatch(
                category == null ? LoreChallengeMatch.ALL_CATEGORIES : category.name(),
                difficulty,
                played,
                clock.instant());
    }

    // One question per category in turn, in a random category order, until the match is full:
    // with five categories that is two each; with fewer questions in some of them, the others
    // make up the difference. A question is taken once, so none repeats.
    private List<LoreChallengeQuestion> pickBalanced(
            Map<LoreChallengeCategory, Deque<LoreChallengeQuestion>> pools) {
        List<LoreChallengeCategory> order = new ArrayList<>(pools.keySet());
        List<LoreChallengeQuestion> selected = new ArrayList<>();

        shuffle(order);

        while (selected.size() < QUESTIONS_PER_MATCH) {
            for (LoreChallengeCategory category : order) {
                Deque<LoreChallengeQuestion> pool = pools.get(category);

                if (selected.size() < QUESTIONS_PER_MATCH && !pool.isEmpty()) {
                    selected.add(pool.poll());
                }
            }
        }

        return selected;
    }

    private PlayedQuestion play(LoreChallengeQuestion question) {
        List<String> texts = new ArrayList<>(question.distractors());

        texts.add(question.answer());
        shuffle(texts);

        List<Option> options = new ArrayList<>();
        String correctOptionId = null;

        for (int i = 0; i < texts.size(); i++) {
            options.add(new Option(OPTION_IDS[i], texts.get(i)));

            if (texts.get(i).equals(question.answer())) {
                correctOptionId = OPTION_IDS[i];
            }
        }

        return new PlayedQuestion(question, List.copyOf(options), correctOptionId);
    }

    private <T> void shuffle(List<T> items) {
        for (int i = items.size() - 1; i > 0; i--) {
            Collections.swap(items, i, random.nextInt(i + 1));
        }
    }
}
