import { render } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import GameFilterList, { scrollLeftToReveal } from "./GameFilterList";

afterEach(() => {
    vi.restoreAllMocks();
});

const box = { left: 0, right: 300 };

describe("scrollLeftToReveal", () => {
    it("leaves the scroll alone when the item is already inside", () => {
        expect(scrollLeftToReveal(40, box, { left: 50, right: 200 })).toBe(40);
    });

    it("scrolls right just enough to show an item cut off on the right", () => {
        expect(scrollLeftToReveal(0, box, { left: 250, right: 420 })).toBe(128);
    });

    it("scrolls back left for an item cut off on the left, never below zero", () => {
        expect(scrollLeftToReveal(100, box, { left: -20, right: 90 })).toBe(72);
        expect(scrollLeftToReveal(5, box, { left: -20, right: 90 })).toBe(0);
    });
});

// jsdom has no layout: positions are supplied per element through getBoundingClientRect.
function stubLayout(offsetOf: (el: Element, scrollLeft: number) => { left: number; right: number }) {
    vi.spyOn(Element.prototype, "getBoundingClientRect").mockImplementation(function (this: Element) {
        const r = offsetOf(this, (this.closest("ul") as HTMLUListElement | null)?.scrollLeft ?? 0);

        return { ...r, top: 0, bottom: 0, width: r.right - r.left, height: 0, x: r.left, y: 0, toJSON() {} };
    });
}

function renderList(activeKey: string) {
    return render(
        <GameFilterList className="list" activeKey={activeKey}>
            <li><a href="/a">A</a></li>
            <li><a href="/b" aria-current="page">B</a></li>
        </GameFilterList>,
    );
}

describe("GameFilterList", () => {
    it("brings the selected link into view when it starts out of view", () => {
        stubLayout((el, scroll) =>
            el.tagName === "UL"
                ? { left: 0, right: 300 }
                : el.getAttribute("aria-current")
                  ? { left: 250 - scroll, right: 420 - scroll }
                  : { left: 0, right: 100 },
        );

        const { container } = renderList("b");

        expect(container.querySelector("ul")!.scrollLeft).toBe(128);
    });

    it("does not scroll when the selected link is already visible", () => {
        stubLayout((el) =>
            el.tagName === "UL" ? { left: 0, right: 300 } : { left: 20, right: 150 },
        );

        const { container } = renderList("b");

        expect(container.querySelector("ul")!.scrollLeft).toBe(0);
    });

    it("scrolls again only when the selected game changes, and keeps the links' attributes", () => {
        stubLayout((el, scroll) =>
            el.tagName === "UL"
                ? { left: 0, right: 300 }
                : el.getAttribute("aria-current")
                  ? { left: 250 - scroll, right: 420 - scroll }
                  : { left: 0, right: 100 },
        );

        const { container, rerender } = renderList("b");
        const list = container.querySelector("ul")!;

        list.scrollLeft = 10; // the visitor scrolled by hand
        rerender(
            <GameFilterList className="list" activeKey="b">
                <li><a href="/a">A</a></li>
                <li><a href="/b" aria-current="page">B</a></li>
            </GameFilterList>,
        );
        expect(list.scrollLeft).toBe(10);

        rerender(
            <GameFilterList className="list" activeKey="c">
                <li><a href="/a">A</a></li>
                <li><a href="/b" aria-current="page">B</a></li>
            </GameFilterList>,
        );
        expect(list.scrollLeft).toBe(128);
        expect(list.querySelector("a[aria-current]")).toHaveAttribute("href", "/b");
        expect(list).toHaveClass("list");
    });
});
