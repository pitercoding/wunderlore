package com.wunderlore.game.whosaidit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.wunderlore.client.theone.TheOneApiClient;
import com.wunderlore.client.theone.dto.ExternalCharacter;
import com.wunderlore.client.theone.dto.ExternalCharacterResponse;
import com.wunderlore.client.theone.dto.ExternalQuote;
import com.wunderlore.client.theone.dto.ExternalQuoteResponse;
import com.wunderlore.lore.LoreDataService;

@ExtendWith(MockitoExtension.class)
class WhoSaidItMatchServiceTest {

    private static final int PAGE_SIZE = 100;

    @Mock
    private TheOneApiClient theOneApiClient;

    private void stub(List<ExternalQuote> quotes, List<ExternalCharacter> characters) {
        when(theOneApiClient.getQuotes(1, PAGE_SIZE))
                .thenReturn(new ExternalQuoteResponse(quotes, quotes.size(), PAGE_SIZE, null, 1, 1));
        when(theOneApiClient.getCharacters(1, PAGE_SIZE))
                .thenReturn(new ExternalCharacterResponse(characters, characters.size(), PAGE_SIZE, null, 1, 1));
    }

    private WhoSaidItMatchService service(long seed) {
        LoreDataService lore = new LoreDataService(theOneApiClient, Clock.systemUTC(), Duration.ZERO, 100, Duration.ofSeconds(30));

        return new WhoSaidItMatchService(lore, new Random(seed), Clock.systemUTC());
    }

    private static List<ExternalCharacter> characters(int count) {
        List<ExternalCharacter> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            list.add(new ExternalCharacter("char-" + i, "Character " + i));
        }

        return list;
    }

    private static List<ExternalQuote> quotes(int count, int characterCount) {
        List<ExternalQuote> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            list.add(new ExternalQuote("q" + i, "Dialog line number " + i, "movie", "char-" + (i % characterCount), "q" + i));
        }

        return list;
    }

    // Reads the private questions through the public views and the final result.
    private static List<WhoSaidItMatch.QuestionView> views(WhoSaidItMatch match) {
        List<WhoSaidItMatch.QuestionView> views = new ArrayList<>();

        for (int n = 1; n <= WhoSaidItMatch.TOTAL_QUESTIONS; n++) {
            views.add(match.currentQuestion());
            match.answer(n, match.currentQuestion().options().get(0).id());
        }

        return views;
    }

    @Test
    void shouldBuildAMatchOfTenDistinctQuotesWithFourDistinctOptions() {
        stub(quotes(30, 12), characters(12));

        WhoSaidItMatch match = service(1).createMatch();
        List<WhoSaidItMatch.QuestionView> views = views(match);

        assertThat(views).hasSize(10);
        assertThat(views).extracting(WhoSaidItMatch.QuestionView::quote).doesNotHaveDuplicates();
        assertThat(views).extracting(WhoSaidItMatch.QuestionView::questionNumber)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        for (WhoSaidItMatch.QuestionView view : views) {
            assertThat(view.totalQuestions()).isEqualTo(10);
            assertThat(view.options()).hasSize(4);
            assertThat(view.options()).extracting(WhoSaidItRound.CharacterOption::id).doesNotHaveDuplicates();
            assertThat(view.options()).extracting(WhoSaidItRound.CharacterOption::name).doesNotHaveDuplicates();
        }
    }

    @Test
    void shouldAlwaysOfferTheCorrectCharacterAmongTheOptions() {
        stub(quotes(30, 12), characters(12));

        // Answering with the first option of each question never has to be right; what matters is
        // that the correct character is offered, so picking the character who really said each
        // quote must always be possible: Dialog line number i is by char-(i % 12).
        for (long seed = 1; seed <= 20; seed++) {
            WhoSaidItMatch match = service(seed).createMatch();

            for (int n = 1; n <= 10; n++) {
                WhoSaidItMatch.QuestionView view = match.currentQuestion();
                int index = Integer.parseInt(view.quote().substring("Dialog line number ".length()));
                String correct = "char-" + (index % 12);

                assertThat(view.options()).extracting(WhoSaidItRound.CharacterOption::id).contains(correct);
                match.answer(n, correct);
            }

            assertThat(match.result().score()).isEqualTo(100);
        }
    }

    @Test
    void shouldSkipRepeatedDialogsAndQuotesWithoutAKnownCharacter() {
        List<ExternalQuote> list = new ArrayList<>();

        for (int i = 0; i < 9; i++) {
            list.add(new ExternalQuote("q" + i, "Dialog line number " + i, "movie", "char-" + i, "q" + i));
        }

        // Repeats of an existing dialog (any case) and quotes by unknown characters do not count.
        list.add(new ExternalQuote("dup1", "Dialog line number 0", "movie", "char-1", "dup1"));
        list.add(new ExternalQuote("dup2", "  dialog line number 3 ", "movie", "char-2", "dup2"));
        list.add(new ExternalQuote("ghost", "Dialog line number ghost", "movie", "unknown", "ghost"));
        list.add(new ExternalQuote("blank", "   ", "movie", "char-1", "blank"));
        stub(list, characters(9));

        assertThatThrownBy(() -> service(1).createMatch())
                .isInstanceOf(WhoSaidItInsufficientDataException.class);
    }

    @Test
    void shouldNotBuildAShorterMatchWhenThereAreFewerThanTenQuotes() {
        stub(quotes(9, 9), characters(9));

        assertThatThrownBy(() -> service(1).createMatch())
                .isInstanceOf(WhoSaidItInsufficientDataException.class);
    }

    @Test
    void shouldNotBuildAMatchWithFewerThanFourDistinctCharacters() {
        stub(quotes(20, 3), characters(3));

        assertThatThrownBy(() -> service(1).createMatch())
                .isInstanceOf(WhoSaidItInsufficientDataException.class);
    }

    @Test
    void shouldNotOfferTwoCharactersWithTheSameName() {
        List<ExternalCharacter> list = new ArrayList<>(characters(4));

        // Four characters, but two share a name: only three distinct names, no valid question.
        list.set(3, new ExternalCharacter("char-3", "character 0"));
        stub(quotes(20, 4), list);

        assertThatThrownBy(() -> service(1).createMatch())
                .isInstanceOf(WhoSaidItInsufficientDataException.class);
    }

    private static final String GENERIC_MINOR_CHARACTER = "5cdbe49b7ed9587226e794a0";
    private static final Set<String> EXCLUDED_IDS = Set.of(
            GENERIC_MINOR_CHARACTER, "5cdbe47d7ed9587226e7949f", "5cdbdecb6dc0baeae48cfa43",
            "5cd99d4bde30eff6ebccfc96", "5cd99d4bde30eff6ebccfe10",
            "5cdbdecb6dc0baeae48cfac2", "5cdbdecb6dc0baeae48cfa8d");

    private static final String BARROW_WIGHT_QUOTE_ID = "5cd96e05de30eff6ebccedde";
    private static final String CORRUPTED_QUOTE_ID = "5cd96e05de30eff6ebccec49";
    private static final String DANGLING_PAREN_QUOTE_ID = "5cd96e05de30eff6ebcceead";

    // The five records that are not characters, as The One API returns them.
    private static List<ExternalCharacter> withExcludedCharacters(List<ExternalCharacter> base) {
        List<ExternalCharacter> list = new ArrayList<>(base);

        list.add(new ExternalCharacter(GENERIC_MINOR_CHARACTER, "MINOR_CHARACTER"));
        list.add(new ExternalCharacter("5cdbe47d7ed9587226e7949f", "MINOR_CHARACTER "));
        list.add(new ExternalCharacter("5cdbdecb6dc0baeae48cfa43", "Seers"));
        list.add(new ExternalCharacter("5cd99d4bde30eff6ebccfc96", "User:Deathshriek"));
        list.add(new ExternalCharacter("5cd99d4bde30eff6ebccfe10", "User:Technobliterator/Showcase"));
        list.add(new ExternalCharacter("5cdbdecb6dc0baeae48cfac2", "Letter 211"));
        list.add(new ExternalCharacter("5cdbdecb6dc0baeae48cfa8d", "Men"));

        return list;
    }

    @Test
    void shouldNeverUseAnyExcludedRecordAsAnOptionOrAnswerEvenWhenTheyOwnQuotes() {
        List<ExternalQuote> list = new ArrayList<>(quotes(20, 12));
        int n = 0;

        // Quotes filed under each excluded record, as if the upstream had some.
        for (String id : EXCLUDED_IDS) {
            list.add(new ExternalQuote("x" + n, "Excluded owner dialog " + n, "movie", id, "x" + n));
            n++;
        }

        stub(list, withExcludedCharacters(characters(12)));

        for (long seed = 1; seed <= 40; seed++) {
            WhoSaidItMatch match = service(seed).createMatch();

            for (int q = 1; q <= 10; q++) {
                WhoSaidItMatch.QuestionView view = match.currentQuestion();

                assertThat(view.quote()).doesNotStartWith("Excluded owner");
                assertThat(view.options()).extracting(WhoSaidItRound.CharacterOption::id)
                        .doesNotContainAnyElementsOf(EXCLUDED_IDS);

                match.answer(q, view.options().get(0).id());
            }
        }
    }

    @Test
    void shouldDropOnlyTheExcludedQuotesAndKeepGollumAndAragorn() {
        List<ExternalCharacter> chars = new ArrayList<>(characters(12));

        chars.add(new ExternalCharacter("gollum", "Gollum"));
        chars.add(new ExternalCharacter("aragorn", "Aragorn"));
        chars.add(new ExternalCharacter("5cdbdecb6dc0baeae48cfa59", "Éowyn"));

        List<ExternalQuote> list = new ArrayList<>(quotes(12, 12));

        list.add(new ExternalQuote(BARROW_WIGHT_QUOTE_ID, "Barrow song", "movie", "gollum", "b"));
        list.add(new ExternalQuote(CORRUPTED_QUOTE_ID, "Corrupted line", "movie", "aragorn", "c"));
        list.add(new ExternalQuote(DANGLING_PAREN_QUOTE_ID, "Password line (", "movie", "gollum", "d"));
        list.add(new ExternalQuote("legit-gollum", "Legit Gollum line", "movie", "gollum", "lg"));
        list.add(new ExternalQuote("legit-aragorn", "Legit Aragorn line", "movie", "aragorn", "la"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccec24", "I'm sorry. Please, eat.", "movie",
                "5cdbdecb6dc0baeae48cfa59", "le"));
        stub(list, chars);

        Set<String> seen = new HashSet<>();
        Set<String> optionIds = new HashSet<>();

        for (long seed = 1; seed <= 60; seed++) {
            WhoSaidItMatch match = service(seed).createMatch();

            for (int q = 1; q <= 10; q++) {
                WhoSaidItMatch.QuestionView view = match.currentQuestion();

                seen.add(view.quote());
                view.options().forEach(option -> optionIds.add(option.id()));
                match.answer(q, view.options().get(0).id());
            }
        }

        assertThat(seen).doesNotContain("Barrow song", "Corrupted line", "Password line (");
        assertThat(seen).contains("Legit Gollum line", "Legit Aragorn line", "I'm sorry. Please, eat.");
        assertThat(optionIds).contains("gollum", "aragorn", "5cdbdecb6dc0baeae48cfa59");
    }

    @Test
    void shouldKeepHomonymsOfExcludedRecordsThatHaveDifferentIds() {
        List<ExternalCharacter> chars = new ArrayList<>(characters(8));

        chars.add(new ExternalCharacter("homonym-men", "Men"));
        chars.add(new ExternalCharacter("homonym-letter", "Letter 211"));

        List<ExternalQuote> list = new ArrayList<>(quotes(20, 8));

        list.add(new ExternalQuote("hm", "Homonym Men line", "movie", "homonym-men", "hm"));
        list.add(new ExternalQuote("hl", "Homonym Letter line", "movie", "homonym-letter", "hl"));
        stub(list, withExcludedCharacters(chars));

        Set<String> optionIds = new HashSet<>();

        for (long seed = 1; seed <= 60; seed++) {
            WhoSaidItMatch match = service(seed).createMatch();

            for (int q = 1; q <= 10; q++) {
                WhoSaidItMatch.QuestionView view = match.currentQuestion();

                view.options().forEach(option -> optionIds.add(option.id()));
                match.answer(q, view.options().get(0).id());
            }
        }

        assertThat(optionIds).contains("homonym-men", "homonym-letter");
        assertThat(optionIds).doesNotContainAnyElementsOf(EXCLUDED_IDS);
    }

    @Test
    void shouldNotUseVeryShortQuotesInTheCompetitiveMode() {
        List<ExternalQuote> list = new ArrayList<>(quotes(10, 12));

        list.add(new ExternalQuote("s1", "No!", "movie", "char-1", "s1"));
        list.add(new ExternalQuote("s2", "Pippin!", "movie", "char-2", "s2"));
        list.add(new ExternalQuote("s3", "Gimli?", "movie", "char-3", "s3"));
        stub(list, characters(12));

        // Exactly ten quotes have three words or more, so every match is made of those ten.
        for (long seed = 1; seed <= 20; seed++) {
            Set<String> seen = new HashSet<>(views(service(seed).createMatch()).stream()
                    .map(WhoSaidItMatch.QuestionView::quote).toList());

            assertThat(seen).hasSize(10).doesNotContain("No!", "Pippin!", "Gimli?");
        }
    }

    @Test
    void shouldApplyTheCompetitiveCurationWhenBuildingMatches() {
        List<ExternalQuote> list = new ArrayList<>(quotes(10, 12));

        // Real audit records: a text shared by two characters, a glued line, the Sauron line that
        // belongs to a Saruman scene, and two quotes that must stay.
        list.add(new ExternalQuote("5cd96e05de30eff6ebccea09", "What are you doing?", "movie", "char-1", "a"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccea5c", "What are you doing?", "movie", "char-2", "b"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebcceb61",
                "I will not leave my daughter hereto die.", "movie", "char-3", "c"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccedf3",
                "Build me an army worthy of Mordor.", "movie", "char-4", "d"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccf0ed", "I see you!", "movie", "char-5", "e"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccee15", "Merin le telim.", "movie", "char-6", "f"));
        stub(list, characters(12));

        Set<String> seen = new HashSet<>();

        for (long seed = 1; seed <= 80; seed++) {
            views(service(seed).createMatch()).forEach(view -> seen.add(view.quote()));
        }

        assertThat(seen).doesNotContain("What are you doing?", "I will not leave my daughter hereto die.",
                "Build me an army worthy of Mordor.");
        assertThat(seen).contains("I see you!", "Merin le telim.");
    }

    @Test
    void shouldLeaveThePublicCatalogUnchanged() {
        List<ExternalQuote> list = new ArrayList<>(quotes(5, 5));

        list.add(new ExternalQuote(BARROW_WIGHT_QUOTE_ID, "Barrow song", "movie", "char-1", "b"));
        list.add(new ExternalQuote("g1", "Generic dialog", "movie", GENERIC_MINOR_CHARACTER, "g1"));
        // The competitive curation (short and glued quotes) does not reach the catalog either. The catalog
        // already drops repeated texts on its own, so a colliding pair is not part of this check.
        list.add(new ExternalQuote("5cd96e05de30eff6ebcceb61", "I will not leave my daughter hereto die.", "movie", "char-1", "ed"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccec1c", "Sam, no!", "movie", "char-2", "sh"));
        list.add(new ExternalQuote("5cd96e05de30eff6ebccea09", "What are you doing?", "movie", "char-3", "c1"));
        stub(list, withExcludedCharacters(characters(5)));

        LoreDataService lore = new LoreDataService(
                theOneApiClient, Clock.systemUTC(), Duration.ZERO, 100, Duration.ofSeconds(30));

        // The game-only exclusions do not reach the lore data the catalog is built from.
        assertThat(lore.getData().catalogQuotes()).extracting(q -> q.id())
                .contains(BARROW_WIGHT_QUOTE_ID, "g1", "5cd96e05de30eff6ebcceb61", "5cd96e05de30eff6ebccec1c",
                        "5cd96e05de30eff6ebccea09");
        assertThat(lore.getData().charactersById()).containsKeys(EXCLUDED_IDS.toArray(String[]::new));
    }

    private static List<ExternalQuote> genericQuotes(int count) {
        List<ExternalQuote> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            list.add(new ExternalQuote("g" + i, "Generic dialog line " + i, "movie", GENERIC_MINOR_CHARACTER, "g" + i));
        }

        return list;
    }

    @Test
    void shouldNeverUseTheGenericMinorCharacterAsTheCorrectAnswerOrAnyExcludedIdAsAnOption() {
        List<ExternalQuote> list = new ArrayList<>(genericQuotes(60));

        list.addAll(quotes(20, 12));
        stub(list, withExcludedCharacters(characters(12)));

        for (long seed = 1; seed <= 30; seed++) {
            WhoSaidItMatch match = service(seed).createMatch();

            for (int n = 1; n <= 10; n++) {
                WhoSaidItMatch.QuestionView view = match.currentQuestion();
                int index = Integer.parseInt(view.quote().substring("Dialog line number ".length()));

                assertThat(view.quote()).doesNotStartWith("Generic dialog");
                assertThat(view.options()).extracting(WhoSaidItRound.CharacterOption::id)
                        .doesNotContainAnyElementsOf(EXCLUDED_IDS);

                match.answer(n, "char-" + (index % 12));
            }

            assertThat(match.result().score()).isEqualTo(100);
        }
    }

    @Test
    void shouldStillBuildTenQuestionsWithFourDistinctOptionsWhenExcludedRecordsArePresent() {
        List<ExternalQuote> list = new ArrayList<>(genericQuotes(40));

        list.addAll(quotes(25, 12));
        stub(list, withExcludedCharacters(characters(12)));

        List<WhoSaidItMatch.QuestionView> views = views(service(7).createMatch());

        assertThat(views).hasSize(10);

        for (WhoSaidItMatch.QuestionView view : views) {
            assertThat(view.options()).hasSize(4);
            assertThat(view.options()).extracting(WhoSaidItRound.CharacterOption::id).doesNotHaveDuplicates();
        }
    }

    @Test
    void shouldNotBuildAMatchWhenFewerThanTenQuotesRemainAfterTheExclusion() {
        List<ExternalQuote> list = new ArrayList<>(genericQuotes(50));

        list.addAll(quotes(9, 9));
        stub(list, withExcludedCharacters(characters(9)));

        assertThatThrownBy(() -> service(1).createMatch())
                .isInstanceOf(WhoSaidItInsufficientDataException.class);
    }

    @Test
    void shouldNotUseExcludedRecordsToFillTheFourOptions() {
        // Three identifiable characters plus the excluded records: still only three real ones.
        stub(quotes(20, 3), withExcludedCharacters(characters(3)));

        assertThatThrownBy(() -> service(1).createMatch())
                .isInstanceOf(WhoSaidItInsufficientDataException.class);
    }

    @Test
    void shouldVaryTheSelectionBetweenMatches() {
        stub(quotes(60, 12), characters(12));

        Set<String> firstQuotes = new HashSet<>();

        for (long seed = 1; seed <= 15; seed++) {
            firstQuotes.add(service(seed).createMatch().currentQuestion().quote());
        }

        assertThat(firstQuotes.size()).isGreaterThan(1);
    }
}
