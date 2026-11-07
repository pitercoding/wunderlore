package com.wunderlore.ranking;

import java.util.Optional;

// The four games whose results can be published. The slug is the public name used in URLs and
// JSON (the same as the frontend routes); the constant name is what the database stores.
public enum GameType {

    LORE_CHALLENGE("lore-challenge"),
    WHO_AM_I("who-am-i"),
    WHERE_IN_MIDDLE_EARTH("where-in-middle-earth"),
    WHO_SAID_IT("who-said-it");

    private final String slug;

    GameType(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
    }

    public static Optional<GameType> fromSlug(String slug) {
        for (GameType type : values()) {
            if (type.slug.equals(slug)) {
                return Optional.of(type);
            }
        }

        return Optional.empty();
    }
}
