package com.wunderlore.game.lorechallenge;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

// Editorial guards for the curated content. LoreChallengeQuestionBankTest checks the shape of the
// bank; these tests check what the questions say, so that a future edit cannot quietly bring back
// the problems found in the editorial review:
// - one question giving away the answer of another one that can be in the same match;
// - the same answer twice in a difficulty, which makes a match predictable;
// - malformed ids, or texts with stray whitespace.
//
// They cannot judge facts or meaning: that stays an editorial job (sources are in the review
// notes, not here). The leak check is deliberately mechanical: it looks for the answer of one
// question, as a whole word, in the prompt or the explanation of another question of the same
// difficulty, because difficulty is fixed per match and the "All" match mixes categories.
class LoreChallengeContentTest {

    private static final Pattern ID_PATTERN =
            Pattern.compile("(characters|places|peoples|history|events)-(easy|medium|hard)-(0[1-9]|1[0-9]|20)");

    // Answers that are the vocabulary of the whole story rather than a clue: the Ring, its maker,
    // the three most visible protagonists and the names of the peoples. They are named in so many
    // prompts that checking them would force every question to avoid the saga's own subject, and
    // none of them can be removed from a Tolkien quiz. Everything else is checked.
    private static final Set<String> PERVASIVE_ANSWERS = Set.of(
            "one ring", "sauron", "gandalf", "aragorn", "frodo baggins", "bilbo baggins",
            "elves", "dwarves", "hobbits", "men", "ents", "orcs");

    // Leading words that say nothing on their own, so a single one is never taken as the short
    // form of an answer ("Mount Doom" is not found through "Mount").
    private static final Set<String> NOT_A_SHORT_FORM = Set.of(
            "the", "mount", "minas", "tar", "ar", "battle", "council", "last", "white", "grey",
            "silvan", "house", "first", "second", "third", "fourth", "amon", "cirith", "dol",
            "tol", "ered", "ephel", "tom", "great", "black");

    // Other names of an answer (normalized, without a leading "the"). A text that uses the
    // alias gives the answer away just as much as the answer itself does, e.g. "the Golden Wood"
    // for Lothlórien. Only aliases that are a clue to the answer belong here: the name used as
    // the question's own subject ("Bridge of Khazad-dûm" in a question about Moria) is the
    // asked-for answer of a different question and is listed under that one.
    private static final Map<String, List<String>> ALIASES = Map.ofEntries(
            Map.entry("lothlórien", List.of("golden wood", "lórien")),
            Map.entry("erebor", List.of("lonely mountain")),
            Map.entry("moria", List.of("khazad-dûm", "dwarrowdelf")),
            Map.entry("weathertop", List.of("amon sûl")),
            Map.entry("mount doom", List.of("orodruin")),
            Map.entry("rivendell", List.of("imladris")),
            Map.entry("grey havens", List.of("mithlond")),
            Map.entry("nazgûl", List.of("ringwraith", "ringwraiths")),
            Map.entry("mûmakil", List.of("oliphaunt", "oliphaunts")),
            Map.entry("eregion", List.of("hollin")),
            Map.entry("hollin", List.of("eregion")),
            Map.entry("mithrandir", List.of("grey pilgrim")),
            Map.entry("bregalad", List.of("quickbeam")),
            Map.entry("dol guldur", List.of("necromancer")),
            Map.entry("necromancer", List.of("dol guldur")));

    // Pairs reviewed one by one and kept on purpose: "source->target", with the reason. A source
    // text naming the answer of a target is acceptable only when the mention gives nothing away.
    // Every entry must still be a real match (see shouldKeepTheLeakExceptionsCurrent), so the
    // list cannot outlive the wording it excuses.
    private static final Map<String, String> ACCEPTED_LEAKS = Map.of(
            "events-easy-02->characters-easy-04",
            "The correct option of events-easy-02 is itself 'held by Gollum', so the name cannot be "
                    + "left out of its explanation, and characters-easy-04 describes the creature "
                    + "('my precious') rather than asking for a clue the explanation could supply.");

    // Same answer, twice in one difficulty, kept on purpose: "difficulty|answer", with the reason.
    private static final Map<String, String> ACCEPTED_REPEATED_ANSWERS = Map.of(
            "EASY|aragorn",
            "characters-easy-08 asks who the Ranger Strider is; events-easy-08 asks who is crowned "
                    + "after the war. Different facts, and Aragorn is the protagonist of both.");

    private final LoreChallengeQuestionBank bank = new LoreChallengeQuestionBank();

    @Test
    void shouldNumberEveryQuestionInItsOwnCategoryAndDifficulty() {
        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                List<String> expected = new ArrayList<>();

                for (int number = 1; number <= 20; number++) {
                    expected.add("%s-%s-%02d".formatted(
                            category.name().toLowerCase(Locale.ROOT),
                            difficulty.name().toLowerCase(Locale.ROOT),
                            number));
                }

                assertThat(bank.find(category, difficulty).stream().map(LoreChallengeQuestion::id))
                        .as("%s %s", category, difficulty)
                        .containsExactlyInAnyOrderElementsOf(expected);
            }
        }

        assertThat(all()).allSatisfy(question ->
                assertThat(question.id()).matches(ID_PATTERN));
    }

    @Test
    void shouldHaveCleanTexts() {
        for (LoreChallengeQuestion question : all()) {
            List<String> texts = new ArrayList<>(question.distractors());

            texts.add(question.prompt());
            texts.add(question.answer());
            texts.add(question.explanation());

            for (String text : texts) {
                assertThat(text).as("%s: %s", question.id(), text).isEqualTo(text.strip());
                assertThat(text).as(question.id()).doesNotContain("  ");
            }

            assertThat(question.prompt()).as(question.id()).endsWith("?");
            assertThat(question.explanation()).as(question.id()).matches(".*[.!)]");
        }
    }

    @Test
    void shouldNotRepeatAnAnswerWithinADifficulty() {
        Map<String, List<String>> idsByAnswer = new HashMap<>();

        for (LoreChallengeQuestion question : all()) {
            idsByAnswer
                    .computeIfAbsent(
                            question.difficulty() + "|" + normalize(question.answer()),
                            key -> new ArrayList<>())
                    .add(question.id());
        }

        idsByAnswer.forEach((key, ids) -> {
            if (ids.size() > 1) {
                assertThat(ACCEPTED_REPEATED_ANSWERS)
                        .as("Answer repeated in one difficulty: %s %s", key, ids)
                        .containsKey(key);
            }
        });
    }

    @Test
    void shouldNotRevealTheAnswerOfAnotherQuestionOfTheSameDifficulty() {
        List<String> leaks = new ArrayList<>();

        for (Leak leak : findLeaks()) {
            if (!ACCEPTED_LEAKS.containsKey(leak.key())) {
                leaks.add(leak.describe());
            }
        }

        assertThat(leaks)
                .as("A question names the answer of another one of the same difficulty. Reword the "
                        + "text that gives it away, or, if the mention reveals nothing, add the "
                        + "pair to ACCEPTED_LEAKS with the reason.")
                .isEmpty();
    }

    @Test
    void shouldKeepTheLeakExceptionsCurrent() {
        Set<String> found = new HashSet<>();

        findLeaks().forEach(leak -> found.add(leak.key()));

        assertThat(found).containsAll(ACCEPTED_LEAKS.keySet());

        Set<String> repeated = new TreeSet<>();
        Map<String, Integer> count = new HashMap<>();

        for (LoreChallengeQuestion question : all()) {
            count.merge(question.difficulty() + "|" + normalize(question.answer()), 1, Integer::sum);
        }

        count.forEach((key, times) -> {
            if (times > 1) {
                repeated.add(key);
            }
        });

        assertThat(repeated).containsAll(ACCEPTED_REPEATED_ANSWERS.keySet());

        Set<String> answers = new HashSet<>();

        all().forEach(question -> answers.add(normalize(question.answer())));

        assertThat(answers).containsAll(PERVASIVE_ANSWERS);
    }

    // An alias entry that names no answer of the bank would silently check nothing.
    @Test
    void shouldOnlyListAliasesOfRealAnswers() {
        Set<String> answers = new HashSet<>();

        all().forEach(question -> answers.add(normalize(question.answer())));

        assertThat(answers).containsAll(ALIASES.keySet());
    }

    // The pairs the editorial review singled out. Each one used to reveal the answer of the other
    // question, so a regression here means the old wording came back.
    @Test
    void shouldNotBringBackTheLeaksFoundInTheReview() {
        List<String[]> reviewed = List.of(
                new String[] {"characters-easy-09", "places-easy-08"},
                new String[] {"places-easy-08", "characters-easy-09"},
                new String[] {"characters-easy-01", "places-easy-04"},
                new String[] {"places-hard-06", "characters-hard-04"},
                new String[] {"places-hard-08", "places-hard-09"},
                new String[] {"history-hard-05", "places-hard-09"},
                new String[] {"places-hard-09", "history-hard-05"},
                new String[] {"characters-medium-06", "events-medium-02"},
                new String[] {"events-medium-02", "characters-medium-06"},
                new String[] {"characters-medium-07", "places-medium-02"},
                new String[] {"places-medium-02", "characters-medium-07"},
                new String[] {"characters-hard-08", "history-hard-07"},
                new String[] {"characters-easy-08", "history-easy-04"},
                new String[] {"characters-easy-08", "history-easy-06"},
                new String[] {"events-easy-08", "places-easy-03"},
                new String[] {"events-easy-07", "places-easy-03"});

        for (String[] pair : reviewed) {
            assertThat(leaks(question(pair[0]), question(pair[1])))
                    .as("%s -> %s", pair[0], pair[1])
                    .isFalse();
        }
    }

    // Wordings corrected after checking them against Tolkien's texts. They are asserted here so a
    // later edit does not restore a claim the review found unsupported.
    @Test
    void shouldKeepTheCorrectedWordings() {
        // Saruman was the head of the White Council, not Gandalf and Elrond.
        assertThat(question("history-hard-05").explanation())
                .contains("Saruman was chosen as head")
                .doesNotContain("led by");

        // Erkenbrand is a leader of the Westfold men; "marshal" is not a title the review could support.
        assertThat(question("events-hard-04").prompt()).doesNotContainIgnoringCase("marshal");

        // Isildur and Anárion ruled Gondor jointly under their father, not after him.
        assertThat(question("history-medium-03").prompt()).doesNotContain("after him");

        // Tolkien never settled how the Orcs came to be, and they are older than the First Age.
        assertThat(question("peoples-easy-04").explanation())
                .doesNotContain("First Age")
                .contains("never settled");

        // Not every Beorning is said to change shape.
        assertThat(question("peoples-medium-05").prompt()).doesNotContain("can take the shape");

        // "Elendili" is the name asked for; the Faithful are what they are called in English.
        assertThat(question("peoples-hard-04").prompt()).contains("Elf-friends");

        // The Hornburg is the fortress; Helm's Deep is the ravine behind it.
        assertThat(question("places-medium-03").explanation()).contains("behind the Hornburg");

        // Ephel Dúath is the Sindarin name of the Mountains of Shadow.
        assertThat(question("places-hard-05").prompt()).contains("Sindarin");

        // Ar-Pharazôn took the Sceptre from Tar-Míriel; the prompt names her.
        assertThat(question("characters-hard-10").prompt()).contains("Tar-Míriel");
    }

    private List<LoreChallengeQuestion> all() {
        List<LoreChallengeQuestion> all = new ArrayList<>();

        for (LoreChallengeCategory category : LoreChallengeCategory.values()) {
            for (LoreChallengeDifficulty difficulty : LoreChallengeDifficulty.values()) {
                all.addAll(bank.find(category, difficulty));
            }
        }

        return all;
    }

    private LoreChallengeQuestion question(String id) {
        return all().stream()
                .filter(question -> question.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No question " + id));
    }

    private record Leak(LoreChallengeQuestion source, LoreChallengeQuestion target, String where) {

        String key() {
            return source.id() + "->" + target.id();
        }

        String describe() {
            return "%s (%s) names the answer of %s: '%s'".formatted(
                    source.id(), where, target.id(), target.answer());
        }
    }

    private List<Leak> findLeaks() {
        List<LoreChallengeQuestion> all = all();
        List<Leak> leaks = new ArrayList<>();

        for (LoreChallengeQuestion source : all) {
            for (LoreChallengeQuestion target : all) {
                if (!source.id().equals(target.id()) && source.difficulty() == target.difficulty()) {
                    String where = whereItNames(source, target);

                    if (where != null) {
                        leaks.add(new Leak(source, target, where));
                    }
                }
            }
        }

        return leaks;
    }

    private boolean leaks(LoreChallengeQuestion source, LoreChallengeQuestion target) {
        return source.difficulty() == target.difficulty() && whereItNames(source, target) != null;
    }

    // "prompt", "explanation" or null when the source never names the answer of the target.
    private String whereItNames(LoreChallengeQuestion source, LoreChallengeQuestion target) {
        String answer = normalize(target.answer());

        if (PERVASIVE_ANSWERS.contains(answer)) {
            return null;
        }

        List<String> terms = new ArrayList<>(termsOf(answer));

        terms.addAll(ALIASES.getOrDefault(answer, List.of()));

        for (String term : terms) {
            if (containsWord(source.prompt(), term)) {
                return "prompt";
            }

            if (containsWord(source.explanation(), term)) {
                return "explanation";
            }
        }

        return null;
    }

    // The answer itself and, for a short name such as "Thorin Oakenshield", the first word.
    // A long sentence-like answer is only matched whole.
    private static List<String> termsOf(String answer) {
        List<String> terms = new ArrayList<>();

        if (answer.length() >= 4) {
            terms.add(answer);
        }

        String[] words = answer.split("\\s+");

        if (words.length > 1 && words.length <= 3
                && words[0].length() >= 4 && !NOT_A_SHORT_FORM.contains(words[0])) {
            terms.add(words[0]);
        }

        return terms;
    }

    private static boolean containsWord(String text, String term) {
        return Pattern.compile(
                        "(?<!\\p{L})" + Pattern.quote(term) + "(?!\\p{L})",
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                .matcher(text)
                .find();
    }

    private static String normalize(String answer) {
        String lower = answer.strip().toLowerCase(Locale.ROOT);

        return lower.startsWith("the ") ? lower.substring(4) : lower;
    }
}
