package com.wunderlore.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.wunderlore.ranking.RankingException.Reason;

class NicknamePolicyTest {

    private static Reason reasonOf(String nickname) {
        try {
            NicknamePolicy.validate(nickname);
        } catch (RankingException exception) {
            return exception.reason();
        }

        return null;
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bilbo", "abc", "Frodo_Baggins", "Sam-wise", "Elrond 2", "1234567890123456",
            "João Ninguém", "Éowyn", "Gimli the Dwarf"})
    void shouldAcceptValidNicknames(String nickname) {
        assertThat(NicknamePolicy.validate(nickname)).isEqualTo(nickname);
    }

    @Test
    void shouldTrimAndCollapseSpaces() {
        assertThat(NicknamePolicy.validate("   Frodo    Baggins  ")).isEqualTo("Frodo Baggins");
    }

    @Test
    void shouldCountTheLengthAfterNormalization() {
        assertThat(NicknamePolicy.validate("  abc  ")).isEqualTo("abc");
        assertThat(reasonOf("  ab  ")).isEqualTo(Reason.INVALID_NICKNAME);
        // Runs of spaces count once: "a      b" is "a b" (3 characters), "a      " is just "a".
        assertThat(NicknamePolicy.validate("a      b")).isEqualTo("a b");
        assertThat(reasonOf("a      ")).isEqualTo(Reason.INVALID_NICKNAME);
    }

    @Test
    void shouldEnforceTheLengthLimits() {
        assertThat(reasonOf("ab")).isEqualTo(Reason.INVALID_NICKNAME);
        assertThat(NicknamePolicy.validate("abc")).isEqualTo("abc");
        assertThat(NicknamePolicy.validate("a".repeat(16))).hasSize(16);
        assertThat(reasonOf("a".repeat(17))).isEqualTo(Reason.INVALID_NICKNAME);
    }

    @Test
    void shouldNormalizeUnicodeSoComposedAndDecomposedLettersCountOnce() {
        // "é" as e + combining acute is two code points; it must count as one letter after NFC.
        assertThat(NicknamePolicy.validate("Éowyn")).isEqualTo("Éowyn");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Frodo!", "Sam@Gamgee", "a.b.c", "Hobbit😀", "😀😀😀",
            "tab\there", "new\nline", "nul\u0000l", "zero​width", "nbsp here", "<b>bold</b>",
            "a/b/c", "Frodo;DROP"})
    void shouldRejectCharactersOutsideTheAllowedSet(String nickname) {
        assertThat(reasonOf(nickname)).isEqualTo(Reason.INVALID_NICKNAME);
    }

    @Test
    void shouldRejectBlankAndMissingNicknames() {
        assertThat(reasonOf(null)).isEqualTo(Reason.INVALID_NICKNAME);
        assertThat(reasonOf("")).isEqualTo(Reason.INVALID_NICKNAME);
        assertThat(reasonOf("     ")).isEqualTo(Reason.INVALID_NICKNAME);
    }

    @ParameterizedTest
    @ValueSource(strings = {"admin", "ADMIN", "Admin", "AdMiN", "administrator", "a d m i n", "a_d-m i n",
            "admin2", "Admin_123", "moderator", "Moderador", "system", "SYSTEM", "Wunderlore", "wunder lore",
            "staff", "root", "Suporte"})
    void shouldRejectReservedNamesIgnoringCaseSeparatorsAndTrailingDigits(String nickname) {
        assertThat(reasonOf(nickname)).isEqualTo(Reason.RESERVED_NICKNAME);
    }

    @Test
    void shouldAllowNamesThatMerelyContainAReservedWord() {
        assertThat(NicknamePolicy.validate("Adminton")).isEqualTo("Adminton");
        assertThat(NicknamePolicy.validate("Systematic")).isEqualTo("Systematic");
    }

    @Test
    void shouldNotEchoTheNicknameInTheError() {
        assertThatThrownBy(() -> NicknamePolicy.validate("bad<script>"))
                .hasMessageNotContaining("script");
    }
}
