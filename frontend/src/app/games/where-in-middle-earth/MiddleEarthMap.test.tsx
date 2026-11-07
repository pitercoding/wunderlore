import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import type { MapPosition } from "@/lib/api/whereInMiddleEarth";
import MiddleEarthMap, { type MapFeedback } from "./MiddleEarthMap";

const LABELS = (
    JSON.parse(
        readFileSync(
            resolve(process.cwd(), "..", "contracts", "where-in-middle-earth", "map-geometry.json"),
            "utf-8",
        ),
    ) as { labels: string[] }
).labels;

function setup(props: Partial<React.ComponentProps<typeof MiddleEarthMap>> = {}) {
    const onSelect = vi.fn<(position: MapPosition) => void>();
    const onClear = vi.fn();
    const view = render(
        <MiddleEarthMap selection={null} onSelect={onSelect} onClear={onClear} {...props} />,
    );
    const svg = view.container.querySelector("svg") as SVGSVGElement;

    return { ...view, svg, onSelect, onClear };
}

// jsdom lays nothing out: give the SVG the box a browser would.
function layOut(svg: SVGSVGElement, left: number, top: number, width: number, height: number) {
    svg.getBoundingClientRect = () =>
        ({ left, top, width, height, right: left + width, bottom: top + height, x: left, y: top }) as DOMRect;
}

const FEEDBACK: MapFeedback = {
    guess: { x: 300, y: 200 },
    answer: { x: 385, y: 226 },
    answerLabel: "Rivendell",
    distance: 88.9,
};

describe("MiddleEarthMap: the picture", () => {
    it("is a vector map with a stable coordinate system that keeps its proportion", () => {
        const { svg } = setup();

        expect(svg).toHaveAttribute("viewBox", "0 0 1000 700");
        expect(svg).toHaveAttribute("preserveAspectRatio", "xMidYMid meet");
        expect(svg.querySelector("image")).toBeNull();
        expect(document.querySelector("img")).toBeNull();
    });

    it("has an accessible name and description", () => {
        const { svg } = setup();

        expect(svg).toHaveAttribute("role", "img");
        expect(screen.getByRole("img", { name: /Map of Middle-earth/ })).toBe(svg);
        expect(svg.querySelector("title")).toHaveTextContent("Map of Middle-earth");
        expect(svg.querySelector("desc")).toHaveTextContent(/unlabelled map/);
        expect(svg.getAttribute("aria-labelledby")).toContain(svg.querySelector("title")!.id);
    });

    it("draws mountains, forests, rivers, lakes and a coast", () => {
        const { svg } = setup();

        expect(svg.querySelectorAll("path").length).toBeGreaterThan(300);
        expect(svg.querySelectorAll("ellipse")).toHaveLength(5);
    });

    it("writes only the labels of the contract: large regions, seas, ranges and a river", () => {
        const { svg } = setup();
        const written = [...svg.querySelectorAll("text")].map((text) => text.textContent);

        expect(written.sort()).toEqual([...LABELS].sort());
    });

    it("never writes the name of a place the game asks about", () => {
        const { svg } = setup();
        // A sample of the places, from every difficulty; the backend test checks all 45 against
        // the same list of labels.
        const places = [
            "Hobbiton", "Bree", "Rivendell", "Moria", "Lothlórien", "Edoras", "Isengard",
            "Mount Doom", "Erebor", "Fangorn", "Osgiliath", "Dol Guldur", "Fornost", "Tharbad",
            "Umbar", "Weathertop", "Gundabad", "Pelargir", "Minas Tirith",
        ];

        for (const place of places) {
            expect(svg.textContent).not.toContain(place);
        }
    });

    it("has unique ids per map, so two maps on a page do not share a filter", () => {
        const { container } = render(
            <>
                <MiddleEarthMap selection={null} onSelect={() => {}} onClear={() => {}} />
                <MiddleEarthMap selection={null} onSelect={() => {}} onClear={() => {}} />
            </>,
        );
        const ids = [...container.querySelectorAll("[id]")].map((element) => element.id);

        expect(new Set(ids).size).toBe(ids.length);
    });
});

describe("MiddleEarthMap: choosing a point", () => {
    it("turns a click into a point of the map, scaled to the viewBox", () => {
        const { svg, onSelect } = setup();

        layOut(svg, 0, 0, 500, 350);
        fireEvent.click(svg, { clientX: 250, clientY: 175 });

        expect(onSelect).toHaveBeenCalledWith({ x: 500, y: 350 });
    });

    it("gives the same point for the same spot on screens of different size", () => {
        for (const width of [328, 358, 768, 1024]) {
            const { svg, onSelect, unmount } = setup();
            const height = (width * 700) / 1000;

            layOut(svg, 12, 80, width, height);
            fireEvent.click(svg, { clientX: 12 + width * 0.25, clientY: 80 + height * 0.5 });

            const [[point]] = onSelect.mock.calls;

            expect(point.x).toBeCloseTo(250, 0);
            expect(point.y).toBeCloseTo(350, 0);
            unmount();
        }
    });

    it("accounts for the empty bands of an element wider or taller than the map", () => {
        const wide = setup();

        layOut(wide.svg, 0, 0, 1000, 350);
        // The picture is 500 wide and starts at 250: a click at its left edge is x = 0.
        fireEvent.click(wide.svg, { clientX: 250, clientY: 175 });
        expect(wide.onSelect).toHaveBeenLastCalledWith({ x: 0, y: 350 });
        wide.unmount();

        const tall = setup();

        layOut(tall.svg, 0, 0, 500, 700);
        // The picture is 350 high and starts at 175.
        fireEvent.click(tall.svg, { clientX: 250, clientY: 175 });
        expect(tall.onSelect).toHaveBeenLastCalledWith({ x: 500, y: 0 });
    });

    it("ignores a click on the empty band", () => {
        const { svg, onSelect } = setup();

        layOut(svg, 0, 0, 1000, 350);
        fireEvent.click(svg, { clientX: 100, clientY: 175 });

        expect(onSelect).not.toHaveBeenCalled();
    });

    it("rounds the point to a tenth", () => {
        const { svg, onSelect } = setup();

        layOut(svg, 0, 0, 1000, 700);
        fireEvent.click(svg, { clientX: 380.46, clientY: 230.04 });

        expect(onSelect).toHaveBeenCalledWith({ x: 380.5, y: 230 });
    });

    it("ignores clicks while the map is locked", () => {
        const { svg, onSelect } = setup({ disabled: true });

        layOut(svg, 0, 0, 1000, 700);
        fireEvent.click(svg, { clientX: 100, clientY: 100 });

        expect(onSelect).not.toHaveBeenCalled();
    });
});

describe("MiddleEarthMap: the marker", () => {
    it("shows the selection where it is, as a share of the map", () => {
        const { container } = setup({ selection: { x: 250, y: 350 } });
        const marker = container.querySelector('[data-marker="selection"]') as HTMLElement;

        expect(marker.style.left).toBe("25%");
        expect(marker.style.top).toBe("50%");
    });

    it("shows no marker without a selection", () => {
        const { container } = setup();

        expect(container.querySelector("[data-marker]")).toBeNull();
        expect(screen.queryByRole("button", { name: "Clear marker" })).not.toBeInTheDocument();
    });

    it("offers to clear the marker, and does", async () => {
        const user = userEvent.setup();
        const { onClear } = setup({ selection: { x: 10, y: 10 } });

        await user.click(screen.getByRole("button", { name: "Clear marker" }));

        expect(onClear).toHaveBeenCalledTimes(1);
    });

    it("announces the marker to assistive technology", () => {
        setup({ selection: { x: 250, y: 350 } });

        expect(screen.getByRole("status")).toHaveTextContent(
            "Marker placed: 25% of the way from the west edge, 50% of the way down from the north edge.",
        );
    });

    it("keeps the markers out of the accessibility tree: the text says the same", () => {
        const { container } = setup({ selection: { x: 10, y: 10 } });

        expect(container.querySelector('[data-marker="selection"]')!.closest("[aria-hidden]")).toHaveAttribute(
            "aria-hidden",
            "true",
        );
    });
});

describe("MiddleEarthMap: keyboard", () => {
    it("is a focusable group with instructions", () => {
        setup();

        const group = screen.getByRole("group", { name: /Map of Middle-earth/ });

        expect(group).toHaveAttribute("tabindex", "0");
        expect(group.getAttribute("aria-describedby")).toBeTruthy();
        expect(document.getElementById(group.getAttribute("aria-describedby")!)).toHaveTextContent(
            /arrow keys/,
        );
    });

    it("places the marker in the middle on the first arrow key", async () => {
        const user = userEvent.setup();
        const { onSelect } = setup();

        screen.getByRole("group").focus();
        await user.keyboard("{ArrowRight}");

        expect(onSelect).toHaveBeenCalledWith({ x: 500, y: 350 });
    });

    it("moves the marker by ten units, and by forty with Shift", async () => {
        const user = userEvent.setup();
        const { onSelect } = setup({ selection: { x: 500, y: 350 } });

        screen.getByRole("group").focus();
        await user.keyboard("{ArrowRight}{ArrowDown}{ArrowLeft}{ArrowUp}");
        await user.keyboard("{Shift>}{ArrowLeft}{ArrowUp}{/Shift}");

        expect(onSelect.mock.calls.map(([point]) => point)).toEqual([
            { x: 510, y: 350 },
            { x: 500, y: 360 },
            { x: 490, y: 350 },
            { x: 500, y: 340 },
            { x: 460, y: 350 },
            { x: 500, y: 310 },
        ]);
    });

    it("does not leave the map", async () => {
        const user = userEvent.setup();
        const { onSelect } = setup({ selection: { x: 995, y: 3 } });

        screen.getByRole("group").focus();
        await user.keyboard("{ArrowRight}{ArrowUp}");

        expect(onSelect.mock.calls.map(([point]) => point)).toEqual([
            { x: 1000, y: 3 },
            { x: 995, y: 0 },
        ]);
    });

    it("clears the marker with Escape", async () => {
        const user = userEvent.setup();
        const { onClear } = setup({ selection: { x: 10, y: 10 } });

        screen.getByRole("group").focus();
        await user.keyboard("{Escape}");

        expect(onClear).toHaveBeenCalledTimes(1);
    });

    it("does nothing while the map is locked", async () => {
        const user = userEvent.setup();
        const { onSelect, onClear } = setup({ selection: { x: 10, y: 10 }, disabled: true });

        screen.getByRole("group").focus();
        await user.keyboard("{ArrowRight}{Escape}");

        expect(onSelect).not.toHaveBeenCalled();
        expect(onClear).not.toHaveBeenCalled();
    });
});

describe("MiddleEarthMap: zoom", () => {
    it("starts showing the whole map and zooms in and out in steps", async () => {
        const user = userEvent.setup();
        const { container } = setup();
        const canvas = container.querySelector("[data-zoom]") as HTMLElement;

        expect(canvas.style.width).toBe("100%");
        expect(screen.getByRole("button", { name: "Zoom out" })).toBeDisabled();

        await user.click(screen.getByRole("button", { name: "Zoom in" }));
        expect(canvas.style.width).toBe("150%");
        expect(screen.getByText("Zoom 1.5×")).toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Zoom in" }));
        await user.click(screen.getByRole("button", { name: "Zoom in" }));
        expect(canvas.style.width).toBe("300%");
        expect(screen.getByRole("button", { name: "Zoom in" })).toBeDisabled();

        await user.click(screen.getByRole("button", { name: "Zoom out" }));
        expect(canvas.style.width).toBe("200%");
    });

    it("keeps a marker at the same place of the map when zoomed", async () => {
        const user = userEvent.setup();
        const { container } = setup({ selection: { x: 250, y: 350 } });

        await user.click(screen.getByRole("button", { name: "Zoom in" }));

        const marker = container.querySelector('[data-marker="selection"]') as HTMLElement;

        expect(marker.style.left).toBe("25%");
        expect(marker.style.top).toBe("50%");
    });
});

describe("MiddleEarthMap: feedback", () => {
    it("shows both points, the real place named and the guess", () => {
        const { container } = setup({ selection: { x: 300, y: 200 }, disabled: true, feedback: FEEDBACK });
        const guess = container.querySelector('[data-marker="guess"]') as HTMLElement;
        const answer = container.querySelector('[data-marker="answer"]') as HTMLElement;

        expect(guess.style.left).toBe("30%");
        expect(guess.style.top).toBe(`${(200 / 700) * 100}%`);
        expect(answer.style.left).toBe("38.5%");
        expect(answer.style.top).toBe(`${(226 / 700) * 100}%`);
        expect(guess).toHaveTextContent("Your guess");
        expect(answer).toHaveTextContent("Rivendell");
        expect(container.querySelector('[data-marker="selection"]')).toBeNull();
    });

    it("draws the distance between them in the picture, as a dashed line", () => {
        const { svg } = setup({ disabled: true, feedback: FEEDBACK });
        const lines = svg.querySelectorAll("g[pointer-events='none'] line");

        expect(lines).toHaveLength(2);

        for (const line of lines) {
            expect(line).toHaveAttribute("x1", "300");
            expect(line).toHaveAttribute("y1", "200");
            expect(line).toHaveAttribute("x2", "385");
            expect(line).toHaveAttribute("y2", "226");
        }

        // The top line is dashed, so the distance does not rest on color alone.
        expect(lines[1].getAttribute("class")).toMatch(/line/);
    });

    it("circles the real place with the zone of the best band", () => {
        const { svg } = setup({ disabled: true, feedback: FEEDBACK });
        const zone = svg.querySelector("g[pointer-events='none'] circle")!;

        expect(zone).toHaveAttribute("cx", "385");
        expect(zone).toHaveAttribute("cy", "226");
        expect(zone).toHaveAttribute("r", "15");
    });

    it("says the round is over and offers no way to clear", () => {
        setup({ selection: { x: 300, y: 200 }, disabled: true, feedback: FEEDBACK });

        expect(screen.getByText("The round is over.")).toBeInTheDocument();
        expect(screen.queryByRole("button", { name: "Clear marker" })).not.toBeInTheDocument();
    });
});
