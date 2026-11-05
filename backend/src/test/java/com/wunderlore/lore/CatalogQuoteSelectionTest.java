package com.wunderlore.lore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class CatalogQuoteSelectionTest {

        @Test
        void shouldUseTheDocumentedLimits() {
                assertThat(CatalogQuoteSelection.MAX_QUOTES).isEqualTo(100);
                assertThat(CatalogQuoteSelection.MAX_DIALOG_LENGTH).isEqualTo(60);
        }

        @Test
        void shouldNeverSelectMoreThanTheMaximum() {
                assertThat(CatalogQuoteSelection.select(quotes(2383))).hasSize(100);
                assertThat(CatalogQuoteSelection.select(quotes(101))).hasSize(100);
                assertThat(CatalogQuoteSelection.select(quotes(100))).hasSize(100);
        }

        @Test
        void shouldKeepEveryEligibleQuoteWhenThereAreFewerThanTheMaximum() {
                List<LoreQuote> input = quotes(37);

                // Fewer than 100 eligible: nothing is dropped and the criterion is not relaxed.
                assertThat(CatalogQuoteSelection.select(input)).containsExactlyElementsOf(input);
        }

        @Test
        void shouldAcceptOnlyDialogsWithinTheLengthLimit() {
                LoreQuote atLimit = quote("a", "x".repeat(60), "frodo");
                LoreQuote overLimit = quote("b", "x".repeat(61) , "frodo");
                LoreQuote shortOne = quote("c", "No!", "gandalf");

                assertThat(CatalogQuoteSelection.select(List.of(atLimit, overLimit, shortOne)))
                                .containsExactly(atLimit, shortOne);
        }

        @Test
        void shouldOnlySelectDialogsAtOrBelowTheLimitFromALargeMixedInput() {
                List<LoreQuote> input = new ArrayList<>();

                for (int i = 0; i < 3000; i++) {
                        // Lengths cycle between 1 and 120 characters.
                        input.add(quote("q" + i, "x".repeat(1 + i % 120) + i, "frodo"));
                }

                List<LoreQuote> selected = CatalogQuoteSelection.select(input);

                assertThat(selected).hasSize(100);
                assertThat(selected).allSatisfy(quote ->
                                assertThat(quote.dialog().length()).isBetween(1, CatalogQuoteSelection.MAX_DIALOG_LENGTH));
        }

        @Test
        void shouldSkipBlankAndMissingDialogs() {
                LoreQuote valid = quote("a", "Valid", "frodo");

                assertThat(CatalogQuoteSelection.select(List.of(
                                quote("b", null, "frodo"),
                                quote("c", "", "frodo"),
                                quote("d", "   ", "frodo"),
                                valid))).containsExactly(valid);
        }

        @Test
        void shouldDropExactDuplicatesKeepingTheFirst() {
                LoreQuote first = quote("a", "You shall not pass!", "gandalf");
                LoreQuote duplicate = quote("b", "You shall not pass!", "gandalf");
                LoreQuote otherCase = quote("c", "you shall not pass!", "gandalf");

                assertThat(CatalogQuoteSelection.select(List.of(first, duplicate, otherCase)))
                                .containsExactly(first, otherCase);
        }

        @Test
        void shouldNotContainDuplicateDialogsWhenInputRepeatsThem() {
                List<LoreQuote> input = new ArrayList<>();

                for (int i = 0; i < 2000; i++) {
                        // Every dialog appears four times.
                        input.add(quote("q" + i, "Line " + (i % 500), "frodo"));
                }

                List<LoreQuote> selected = CatalogQuoteSelection.select(input);

                assertThat(selected).hasSize(100);
                assertThat(selected).extracting(LoreQuote::dialog).doesNotHaveDuplicates();
        }

        @Test
        void shouldBeDeterministic() {
                List<LoreQuote> input = quotes(2383);

                assertThat(CatalogQuoteSelection.select(input))
                                .isEqualTo(CatalogQuoteSelection.select(input))
                                .isEqualTo(CatalogQuoteSelection.select(new ArrayList<>(input)));
        }

        @Test
        void shouldKeepUpstreamOrderAndSpreadTheSelectionAcrossTheInput() {
                List<LoreQuote> input = quotes(1000);

                List<LoreQuote> selected = CatalogQuoteSelection.select(input);

                assertThat(selected.get(0)).isEqualTo(input.get(0));
                // Evenly spaced picks reach well past the first hundred quotes.
                assertThat(input.indexOf(selected.get(99))).isGreaterThan(900);
                assertThat(selected).isSortedAccordingTo(
                                (a, b) -> Integer.compare(input.indexOf(a), input.indexOf(b)));
        }

        @Test
        void shouldReturnTheOriginalQuoteObjectsUnchanged() {
                List<LoreQuote> input = quotes(500);

                // Same text, same character, same ids: selected quotes are members of the input.
                assertThat(CatalogQuoteSelection.select(input)).allSatisfy(selected ->
                                assertThat(input).contains(selected));
        }

        @Test
        void shouldPreserveTheCharacterOfEachSelectedQuote() {
                List<LoreQuote> input = quotes(500);

                for (LoreQuote selected : CatalogQuoteSelection.select(input)) {
                        LoreQuote original = input.stream()
                                        .filter(quote -> quote.id().equals(selected.id()))
                                        .findFirst().orElseThrow();

                        assertThat(selected.characterId()).isEqualTo(original.characterId());
                        assertThat(selected.dialog()).isEqualTo(original.dialog());
                }
        }

        @Test
        void shouldReturnAnImmutableList() {
                List<LoreQuote> selected = CatalogQuoteSelection.select(quotes(500));

                assertThatThrownBy(selected::clear).isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void shouldReturnAnEmptyListForNoQuotes() {
                assertThat(CatalogQuoteSelection.select(List.of())).isEmpty();
        }

        @Test
        void shouldDeriveTheCatalogInLoreDataFromTheAttributedQuotes() {
                List<LoreQuote> attributed = quotes(2383);
                Map<String, LoreCharacter> characters = Map.of("character-1", new LoreCharacter("character-1", "Frodo"));

                LoreData data = new LoreData(attributed, characters, attributed);

                assertThat(data.catalogQuotes()).isEqualTo(CatalogQuoteSelection.select(attributed));
                assertThat(data.catalogQuotes()).hasSize(100);
                // The reduction touches only the catalog, never the other quote lists.
                assertThat(data.quotes()).hasSize(2383);
                assertThat(data.attributedQuotes()).hasSize(2383);
        }

        @Test
        void shouldRefuseACatalogLargerThanTheMaximum() {
                List<LoreQuote> tooMany = quotes(101);

                assertThatThrownBy(() -> new LoreData(tooMany, Map.of(), tooMany, tooMany))
                                .isInstanceOf(IllegalArgumentException.class);
        }

        private static LoreQuote quote(String id, String dialog, String characterId) {
                return new LoreQuote(id, dialog, "movie-1", characterId, id);
        }

        private static List<LoreQuote> quotes(int count) {
                return IntStream.range(0, count)
                                .mapToObj(i -> quote("q" + i, "Dialog " + i, "character-1"))
                                .toList();
        }
}
