import { describe, expect, it } from "vitest";
import { isActiveLink } from "./activeLink";

describe("isActiveLink", () => {
    it("matches the exact path", () => {
        expect(isActiveLink("/quotes", "/quotes")).toBe(true);
    });

    it("matches pages beneath the link", () => {
        expect(isActiveLink("/games/who-said-it/anything", "/games/who-said-it")).toBe(true);
    });

    it("does not match a sibling that shares a prefix", () => {
        expect(isActiveLink("/quotes-archive", "/quotes")).toBe(false);
        expect(isActiveLink("/games/who-am-i", "/games/who-said-it")).toBe(false);
    });

    it("matches nothing on the home page or without a path", () => {
        expect(isActiveLink("/", "/quotes")).toBe(false);
        expect(isActiveLink(null, "/quotes")).toBe(false);
    });
});
