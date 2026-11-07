package com.wunderlore.ranking;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import com.wunderlore.ranking.RankingException.Reason;

// The one authority on what a nickname may be (the frontend repeats the rules only to answer
// sooner). A nickname is a display name, not an identity: duplicates are fine.
//
// Normalization: Unicode NFC, trim, then runs of spaces become one space. Rules on the result:
// 3 to 16 characters, only letters, numbers, spaces, underscores and hyphens. Letters and numbers
// are Unicode ones, so accented names work; emojis, symbols, control and invisible characters do
// not match. The nickname is stored as typed (after normalization); only the reserved-name check
// ignores case.
//
// Reserved names are compared after lower-casing and dropping spaces, underscores, hyphens and
// trailing digits, so "Admin", "a d m i n" and "admin_2" all count as "admin".
public final class NicknamePolicy {

    public static final int MIN_LENGTH = 3;

    public static final int MAX_LENGTH = 16;

    private static final Pattern ALLOWED = Pattern.compile("[\\p{L}\\p{N}_ -]+");

    private static final Pattern SPACE_RUNS = Pattern.compile(" {2,}");

    private static final Pattern IGNORED_IN_RESERVED_CHECK = Pattern.compile("[ _-]");

    private static final Pattern TRAILING_DIGITS = Pattern.compile("\\p{N}+$");

    private static final Set<String> RESERVED = Set.of(
            "admin", "administrator", "administrador", "adm", "moderator", "moderador", "mod",
            "system", "sistema", "root", "staff", "support", "suporte", "owner", "official",
            "oficial", "wunderlore", "wunderloreteam", "server", "servidor", "null", "undefined");

    private NicknamePolicy() {
    }

    // Returns the normalized nickname, or throws INVALID_NICKNAME / RESERVED_NICKNAME.
    public static String validate(String nickname) {
        if (nickname == null) {
            throw new RankingException(Reason.INVALID_NICKNAME);
        }

        String normalized = SPACE_RUNS.matcher(
                Normalizer.normalize(nickname, Normalizer.Form.NFC).trim()).replaceAll(" ");
        int length = normalized.codePointCount(0, normalized.length());

        if (length < MIN_LENGTH || length > MAX_LENGTH || !ALLOWED.matcher(normalized).matches()) {
            throw new RankingException(Reason.INVALID_NICKNAME);
        }

        if (RESERVED.contains(reservedKey(normalized))) {
            throw new RankingException(Reason.RESERVED_NICKNAME);
        }

        return normalized;
    }

    private static String reservedKey(String normalized) {
        String key = IGNORED_IN_RESERVED_CHECK.matcher(normalized.toLowerCase(Locale.ROOT)).replaceAll("");

        return TRAILING_DIGITS.matcher(key).replaceAll("");
    }
}
