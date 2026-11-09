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
            list.add(new ExternalQuote("q" + i, "Dialog number " + i, "movie", "char-" + (i % characterCount), "q" + i));
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
        // quote must always be possible: Dialog number i is by char-(i % 12).
        for (long seed = 1; seed <= 20; seed++) {
            WhoSaidItMatch match = service(seed).createMatch();

            for (int n = 1; n <= 10; n++) {
                WhoSaidItMatch.QuestionView view = match.currentQuestion();
                int index = Integer.parseInt(view.quote().substring("Dialog number ".length()));
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
            list.add(new ExternalQuote("q" + i, "Dialog " + i, "movie", "char-" + i, "q" + i));
        }

        // Repeats of an existing dialog (any case) and quotes by unknown characters do not count.
        list.add(new ExternalQuote("dup1", "Dialog 0", "movie", "char-1", "dup1"));
        list.add(new ExternalQuote("dup2", "  dialog 3 ", "movie", "char-2", "dup2"));
        list.add(new ExternalQuote("ghost", "Dialog ghost", "movie", "unknown", "ghost"));
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
            GENERIC_MINOR_CHARACTER, "5cdbe47d7ed9587226e7949f", "5cdbdecb6dc0baeae48cfa43");

    // The three unidentifiable records, as The One API returns them.
    private static List<ExternalCharacter> withExcludedCharacters(List<ExternalCharacter> base) {
        List<ExternalCharacter> list = new ArrayList<>(base);

        list.add(new ExternalCharacter(GENERIC_MINOR_CHARACTER, "MINOR_CHARACTER"));
        list.add(new ExternalCharacter("5cdbe47d7ed9587226e7949f", "MINOR_CHARACTER "));
        list.add(new ExternalCharacter("5cdbdecb6dc0baeae48cfa43", "Seers"));

        return list;
    }

    private static List<ExternalQuote> genericQuotes(int count) {
        List<ExternalQuote> list = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            list.add(new ExternalQuote("g" + i, "Generic dialog " + i, "movie", GENERIC_MINOR_CHARACTER, "g" + i));
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
                int index = Integer.parseInt(view.quote().substring("Dialog number ".length()));

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
