package com.wunderlore.game.lorechallenge;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// One curated question. The correct answer is stored apart from the three distractors, so the
// order of the four options is decided per match and never by the order of the content files.
// The record refuses to exist unless it is a well-formed question: four distinct options.
public record LoreChallengeQuestion(
        String id,
        LoreChallengeCategory category,
        LoreChallengeDifficulty difficulty,
        String prompt,
        String answer,
        List<String> distractors,
        String explanation) {

    public static final int DISTRACTOR_COUNT = 3;

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9-]{1,64}");

    public LoreChallengeQuestion {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Question id must match " + ID_PATTERN.pattern());
        }

        if (category == null || difficulty == null) {
            throw new IllegalArgumentException("Question " + id + " needs a category and a difficulty");
        }

        requireText(prompt, "prompt", id);
        requireText(answer, "answer", id);
        requireText(explanation, "explanation", id);

        if (distractors == null || distractors.size() != DISTRACTOR_COUNT) {
            throw new IllegalArgumentException(
                    "Question " + id + " needs exactly " + DISTRACTOR_COUNT + " distractors");
        }

        Set<String> seen = new HashSet<>();

        seen.add(normalize(answer));

        for (String distractor : distractors) {
            requireText(distractor, "distractor", id);

            if (!seen.add(normalize(distractor))) {
                throw new IllegalArgumentException("Question " + id + " has duplicate options");
            }
        }

        distractors = List.copyOf(distractors);
    }

    private static void requireText(String value, String field, String id) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Question " + id + " has a blank " + field);
        }
    }

    private static String normalize(String text) {
        return text.strip().toLowerCase(Locale.ROOT);
    }
}
