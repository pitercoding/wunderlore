package com.wunderlore.game.whoami;

import java.util.List;

// One curated character: five clues from the most indirect to the clearest, the facts they rest
// on, and what is needed to pick plausible wrong answers. Immutable and shared by every game.
//
// - aliases: other names for the character. They may not appear in its clues and may not be the
//   name or alias of another character, so no option can be read as the correct one under
//   another name.
// - group: a loose kind ("elf", "wizard"); wrong answers of the same kind are preferred, so the
//   options are plausible.
// - incompatibleWith: ids of characters that must never be offered together with this one (for
//   example because a clue of one could also describe the other).
public record WhoAmICharacter(
        String id,
        String name,
        WhoAmIDifficulty difficulty,
        String group,
        List<String> aliases,
        List<String> clues,
        List<Reference> references,
        List<String> incompatibleWith) {

    // supports: what the source is used to confirm. Verification status is kept in the
    // REFERENCES.md next to the data, not here.
    public record Reference(String source, String url, String supports) {
    }

    public WhoAmICharacter {
        aliases = aliases == null ? List.of() : List.copyOf(aliases);
        clues = clues == null ? List.of() : List.copyOf(clues);
        references = references == null ? List.of() : List.copyOf(references);
        incompatibleWith = incompatibleWith == null ? List.of() : List.copyOf(incompatibleWith);
    }
}
