package com.wunderlore.game.lorechallenge.dto;

import com.wunderlore.game.lorechallenge.LoreChallengeCategory;
import com.wunderlore.game.lorechallenge.LoreChallengeDifficulty;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

// category is ALL or the name of one category. An unknown difficulty fails while the body is
// read, and is answered as a malformed request like any other bad body.
public record CreateMatchRequest(
        @NotNull @Pattern(regexp = "ALL|CHARACTERS|PLACES|PEOPLES|HISTORY|EVENTS") String category,
        @NotNull LoreChallengeDifficulty difficulty) {

    // null means every category.
    public LoreChallengeCategory selectedCategory() {
        return "ALL".equals(category) ? null : LoreChallengeCategory.valueOf(category);
    }
}
