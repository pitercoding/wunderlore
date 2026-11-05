package com.wunderlore.lore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Chooses the small set of quotes the public catalog may show. The rule lives here, in
// the lore layer, so no controller parameter can widen it.
//
// A quote is eligible when its dialog is nonblank and at most MAX_DIALOG_LENGTH characters
// long. Dialogs are never edited, shortened or rewritten, and only the first quote with a
// given exact dialog is kept. When more than MAX_QUOTES quotes are eligible, MAX_QUOTES of
// them are taken at evenly spaced positions of the upstream order. That is deterministic
// (the same input always gives the same catalog), involves no judgment about content, and
// spreads the catalog across the films instead of taking only the first lines.
public final class CatalogQuoteSelection {

    // Upper bound on how many quotes the public catalog can ever hold.
    public static final int MAX_QUOTES = 100;

    // Longest dialog, in characters, the public catalog accepts.
    public static final int MAX_DIALOG_LENGTH = 60;

    private CatalogQuoteSelection() {
    }

    // quotes: attributed quotes in upstream order. Returns at most MAX_QUOTES of them,
    // in the same relative order, as an immutable list.
    public static List<LoreQuote> select(List<LoreQuote> quotes) {
        Set<String> seenDialogs = new HashSet<>();
        List<LoreQuote> eligible = new ArrayList<>();

        for (LoreQuote quote : quotes) {
            String dialog = quote.dialog();

            if (dialog == null || dialog.isBlank() || dialog.length() > MAX_DIALOG_LENGTH) {
                continue;
            }

            if (seenDialogs.add(dialog)) {
                eligible.add(quote);
            }
        }

        if (eligible.size() <= MAX_QUOTES) {
            return List.copyOf(eligible);
        }

        List<LoreQuote> selected = new ArrayList<>(MAX_QUOTES);

        for (int i = 0; i < MAX_QUOTES; i++) {
            // long avoids overflow; i * size / MAX_QUOTES is strictly increasing because
            // size > MAX_QUOTES, so no quote is picked twice.
            selected.add(eligible.get((int) ((long) i * eligible.size() / MAX_QUOTES)));
        }

        return List.copyOf(selected);
    }
}
