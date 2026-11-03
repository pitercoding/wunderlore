import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import CharacterSymbol, { CHARACTER_SYMBOLS, getCharacterSymbol } from "./characterSymbols";

// Every CharacterId in the backend enum.
const BACKEND_CHARACTER_IDS = [
    "FRODO",
    "SAM",
    "ARAGORN",
    "GANDALF",
    "SAURON",
    "GOLLUM",
    "LEGOLAS",
    "GIMLI",
    "GALADRIEL",
    "BOROMIR",
];

describe("characterSymbols", () => {
    it("has a symbol for every character the backend can return, and no other", () => {
        expect(Object.keys(CHARACTER_SYMBOLS).sort()).toEqual([...BACKEND_CHARACTER_IDS].sort());
    });

    it.each(BACKEND_CHARACTER_IDS)("renders %s as a labelled image", (id) => {
        render(<CharacterSymbol id={id} name={id} />);

        expect(screen.getByRole("img", { name: new RegExp(`^${id}: `) })).toBeInTheDocument();
    });

    it("draws a different symbol for every character", () => {
        const markup = BACKEND_CHARACTER_IDS.map((id) => {
            const { container, unmount } = render(<CharacterSymbol id={id} />);
            // The generated pattern id differs per render; compare the drawing itself.
            const html = container.innerHTML.replace(/[:\w-]*-h\b/g, "h");
            unmount();
            return html;
        });

        expect(new Set(markup).size).toBe(BACKEND_CHARACTER_IDS.length);
    });

    it("hides a decorative plate from assistive technology", () => {
        const { container } = render(<CharacterSymbol id="FRODO" decorative />);

        expect(screen.queryByRole("img")).not.toBeInTheDocument();
        expect(container.querySelector("svg")).toHaveAttribute("aria-hidden", "true");
    });

    it("ignores identifier case and returns nothing for unknown identifiers", () => {
        expect(getCharacterSymbol("gandalf")).toBe(CHARACTER_SYMBOLS.GANDALF);
        expect(getCharacterSymbol("SARUMAN")).toBeNull();
        expect(getCharacterSymbol("")).toBeNull();
        expect(getCharacterSymbol("toString")).toBeNull();

        const { container } = render(<CharacterSymbol id="SARUMAN" />);
        expect(container).toBeEmptyDOMElement();
    });
});
