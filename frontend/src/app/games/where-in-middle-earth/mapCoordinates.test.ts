import { describe, expect, it } from "vitest";
import {
    clientToMapPoint,
    describePosition,
    isOnMap,
    normalizePosition,
    projectClientPoint,
} from "./mapCoordinates";

// preserveAspectRatio="xMidYMid meet" scales the 1000 x 700 viewBox by one factor and centres it:
// on an element of another shape there are empty bands, and the click has to be measured from
// the picture, not from the element.

const rect = (left: number, top: number, width: number, height: number) => ({
    left,
    top,
    width,
    height,
});

describe("projectClientPoint", () => {
    it("is the plain proportion when the element has the shape of the map", () => {
        // 500 x 350: a scale of 0.5.
        expect(projectClientPoint(rect(0, 0, 500, 350), 250, 175)).toEqual({ x: 500, y: 350 });
        expect(projectClientPoint(rect(0, 0, 500, 350), 0, 0)).toEqual({ x: 0, y: 0 });
        expect(projectClientPoint(rect(0, 0, 500, 350), 500, 350)).toEqual({ x: 1000, y: 700 });
    });

    it("removes the position of the element on the page", () => {
        expect(projectClientPoint(rect(100, 40, 500, 350), 350, 215)).toEqual({ x: 500, y: 350 });
    });

    it("measures from the picture when the element is wider than the map", () => {
        // 1000 x 350: the height decides (scale 0.5), the picture is 500 wide and centred, with a
        // band of 250 on each side.
        const wide = rect(0, 0, 1000, 350);

        expect(projectClientPoint(wide, 500, 175)).toEqual({ x: 500, y: 350 });
        expect(projectClientPoint(wide, 250, 0)).toEqual({ x: 0, y: 0 });
        expect(projectClientPoint(wide, 750, 350)).toEqual({ x: 1000, y: 700 });
        expect(projectClientPoint(wide, 100, 175)).toBeNull();
        expect(projectClientPoint(wide, 900, 175)).toBeNull();
    });

    it("measures from the picture when the element is taller than the map", () => {
        // 500 x 700: the width decides (scale 0.5), the picture is 350 high and centred, with a
        // band of 175 above and below.
        const tall = rect(0, 0, 500, 700);

        expect(projectClientPoint(tall, 250, 350)).toEqual({ x: 500, y: 350 });
        expect(projectClientPoint(tall, 0, 175)).toEqual({ x: 0, y: 0 });
        expect(projectClientPoint(tall, 500, 525)).toEqual({ x: 1000, y: 700 });
        expect(projectClientPoint(tall, 250, 100)).toBeNull();
        expect(projectClientPoint(tall, 250, 600)).toBeNull();
    });

    it("gives the same point for the same spot at every size", () => {
        // The spot 30% across and 60% down the picture, shown at four widths of the same shape.
        for (const width of [328, 358, 768, 1200]) {
            const height = (width * 700) / 1000;
            const point = projectClientPoint(rect(0, 0, width, height), width * 0.3, height * 0.6);

            expect(point?.x).toBeCloseTo(300, 6);
            expect(point?.y).toBeCloseTo(420, 6);
        }
    });

    it("gives the same point whatever the shape, when the spot is the same on the picture", () => {
        // The picture's centre-left quarter point (250, 350) in an element of any shape.
        for (const [width, height] of [
            [1000, 700],
            [1400, 700],
            [1000, 1400],
            [333, 1000],
        ]) {
            const scale = Math.min(width / 1000, height / 700);
            const left = (width - 1000 * scale) / 2;
            const top = (height - 700 * scale) / 2;
            const point = projectClientPoint(rect(0, 0, width, height), left + 250 * scale, top + 350 * scale);

            expect(point?.x).toBeCloseTo(250, 6);
            expect(point?.y).toBeCloseTo(350, 6);
        }
    });

    it("answers null for an element with no size", () => {
        expect(projectClientPoint(rect(0, 0, 0, 0), 0, 0)).toBeNull();
        expect(projectClientPoint(rect(0, 0, 100, 0), 10, 0)).toBeNull();
    });
});

describe("normalizePosition", () => {
    it("rounds to a tenth", () => {
        expect(normalizePosition({ x: 380.46, y: 230.04 })).toEqual({ x: 380.5, y: 230 });
    });

    it("keeps the point inside the map", () => {
        expect(normalizePosition({ x: -5, y: 900 })).toEqual({ x: 0, y: 700 });
        expect(normalizePosition({ x: 1000.04, y: -0.04 })).toEqual({ x: 1000, y: 0 });
    });
});

describe("isOnMap", () => {
    it("accepts the borders and refuses what is outside or not a number", () => {
        expect(isOnMap(0, 0)).toBe(true);
        expect(isOnMap(1000, 700)).toBe(true);
        expect(isOnMap(1000.1, 700)).toBe(false);
        expect(isOnMap(0, -0.1)).toBe(false);
        expect(isOnMap(Number.NaN, 5)).toBe(false);
        expect(isOnMap(5, Number.POSITIVE_INFINITY)).toBe(false);
    });
});

describe("clientToMapPoint", () => {
    function fakeSvg(box: ReturnType<typeof rect>, matrix?: DOMMatrix | null) {
        const svg = {
            getBoundingClientRect: () => ({ ...box, right: box.left + box.width, bottom: box.top + box.height }),
        } as unknown as SVGSVGElement;

        if (matrix !== undefined) {
            Object.assign(svg, {
                getScreenCTM: () => matrix,
                createSVGPoint: () => ({
                    x: 0,
                    y: 0,
                    matrixTransform(m: { a: number; d: number; e: number; f: number }) {
                        return { x: this.x * m.a + m.e, y: this.y * m.d + m.f };
                    },
                }),
            });
        }

        return svg;
    }

    it("falls back to the arithmetic where the SVG has no screen matrix", () => {
        const svg = fakeSvg(rect(20, 10, 500, 350));

        expect(clientToMapPoint(svg, 270, 185)).toEqual({ x: 500, y: 350 });
        expect(clientToMapPoint(svg, 5, 185)).toBeNull();
    });

    it("uses the screen matrix of the SVG when there is one, rounded to a tenth", () => {
        // Client = 0.5 * map + (20, 10), so the inverse is what the SVG itself reports.
        const inverse = { a: 2, d: 2, e: -40, f: -20 };
        const matrix = { inverse: () => inverse } as unknown as DOMMatrix;
        const svg = fakeSvg(rect(20, 10, 500, 350), matrix);

        expect(clientToMapPoint(svg, 270.03, 185)).toEqual({ x: 500.1, y: 350 });
        expect(clientToMapPoint(svg, 0, 0)).toBeNull();
    });
});

describe("describePosition", () => {
    it("says how far across and down, in words and percent", () => {
        expect(describePosition({ x: 250, y: 350 })).toBe(
            "25% of the way from the west edge, 50% of the way down from the north edge",
        );
    });
});
