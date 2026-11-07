import { describe, expect, it } from "vitest";
import { normalizeNickname, validateNickname } from "./nickname";

describe("normalizeNickname", () => {
    it("trims and collapses runs of spaces", () => {
        expect(normalizeNickname("   Frodo    Baggins  ")).toBe("Frodo Baggins");
    });

    it("composes decomposed letters (NFC)", () => {
        expect(normalizeNickname("Éowyn")).toBe("Éowyn");
    });
});

describe("validateNickname", () => {
    it.each(["Bilbo", "abc", "Frodo_Baggins", "Sam-wise", "Elrond 2", "João Ninguém", "1234567890123456"])(
        "accepts %s",
        (nickname) => {
            expect(validateNickname(nickname)).toBeNull();
        },
    );

    it("checks the length after normalization", () => {
        expect(validateNickname("  ab  ")).not.toBeNull();
        expect(validateNickname("  abc  ")).toBeNull();
        expect(validateNickname("a".repeat(17))).not.toBeNull();
        expect(validateNickname("a      b")).toBeNull();
        expect(validateNickname("a      ")).not.toBeNull();
    });

    it.each(["Frodo!", "a.b", "Hobbit😀", "tab\there", "new\nline", "zero​width", "<b>x</b>", ""])(
        "rejects characters outside the allowed set: %j",
        (nickname) => {
            expect(validateNickname(nickname)).not.toBeNull();
        },
    );

    it.each(["admin", "ADMIN", "a d m i n", "Admin_123", "Moderator", "Wunderlore", "system"])(
        "rejects the reserved name %s",
        (nickname) => {
            expect(validateNickname(nickname)).toMatch(/not available/);
        },
    );

    it("allows names that only contain a reserved word", () => {
        expect(validateNickname("Adminton")).toBeNull();
    });
});
