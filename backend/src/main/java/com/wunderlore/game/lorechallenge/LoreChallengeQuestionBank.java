package com.wunderlore.game.lorechallenge;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

// The curated questions, versioned with the code: one JSON file per category under
// src/main/resources/lore-challenge/. They are read once at startup and kept immutable, so a
// malformed question stops the application from starting instead of failing during a match.
// Adding questions means appending entries to those files; nothing else needs to change.
@Component
public class LoreChallengeQuestionBank {

    static final String RESOURCE_DIRECTORY = "lore-challenge/";

    private record QuestionFile(LoreChallengeCategory category, List<QuestionEntry> questions) {
    }

    private record QuestionEntry(
            String id,
            LoreChallengeDifficulty difficulty,
            String prompt,
            String answer,
            List<String> distractors,
            String explanation) {
    }

    private final Map<LoreChallengeCategory, Map<LoreChallengeDifficulty, List<LoreChallengeQuestion>>> questions =
            new EnumMap<>(LoreChallengeCategory.class);

    @Autowired
    public LoreChallengeQuestionBank() {
        this(loadFromClasspath());
    }

    LoreChallengeQuestionBank(List<LoreChallengeQuestion> all) {
        Set<String> ids = new HashSet<>();
        Set<String> prompts = new HashSet<>();

        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            Map<LoreChallengeDifficulty, List<LoreChallengeQuestion>> byDifficulty =
                    new EnumMap<>(LoreChallengeDifficulty.class);

            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                byDifficulty.put(difficulty, new ArrayList<>());
            }

            questions.put(category, byDifficulty);
        }

        for (LoreChallengeQuestion question : all) {
            if (!ids.add(question.id())) {
                throw new IllegalArgumentException("Duplicate question id: " + question.id());
            }

            if (!prompts.add(question.prompt().strip().toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException("Duplicate question prompt in: " + question.id());
            }

            questions.get(question.category()).get(question.difficulty()).add(question);
        }
    }

    // A copy; callers shuffle it freely.
    public List<LoreChallengeQuestion> find(
            LoreChallengeCategory category, LoreChallengeDifficulty difficulty) {
        return List.copyOf(questions.get(category).get(difficulty));
    }

    public int count(LoreChallengeCategory category, LoreChallengeDifficulty difficulty) {
        return questions.get(category).get(difficulty).size();
    }

    private static List<LoreChallengeQuestion> loadFromClasspath() {
        JsonMapper mapper = JsonMapper.builder().build();
        List<LoreChallengeQuestion> all = new ArrayList<>();

        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            String path = RESOURCE_DIRECTORY + category.name().toLowerCase(Locale.ROOT) + ".json";

            try (InputStream stream = new ClassPathResource(path).getInputStream()) {
                QuestionFile file = mapper.readValue(stream, QuestionFile.class);

                if (file.category() != category) {
                    throw new IllegalStateException(path + " declares category " + file.category());
                }

                for (QuestionEntry entry : file.questions()) {
                    all.add(new LoreChallengeQuestion(
                            entry.id(),
                            category,
                            entry.difficulty(),
                            entry.prompt(),
                            entry.answer(),
                            entry.distractors(),
                            entry.explanation()));
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read " + path, exception);
            }
        }

        return all;
    }
}
