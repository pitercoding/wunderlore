import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { MAP_HEIGHT, MAP_WIDTH } from "@/lib/api/whereInMiddleEarth";
import { FORESTS, LAKES, PEAKS, RIVERS, TREES, WAVES, random } from "./mapArt";
import { LABELS, LAND, REGIONS, VIEWBOX, pointInPolygon } from "./mapGeometry";

// The shared geometry: what the backend tests place the 45 places against.
const GEOMETRY = JSON.parse(
    readFileSync(
        resolve(process.cwd(), "..", "contracts", "where-in-middle-earth", "map-geometry.json"),
        "utf-8",
    ),
) as {
    viewBox: { width: number; height: number };
    land: [number, number][];
    regions: { id: string; name: string; polygon: [number, number][] }[];
    labels: string[];
};

describe("map geometry", () => {
    it("draws in the viewBox the backend and the contract name", () => {
        expect(VIEWBOX).toEqual(GEOMETRY.viewBox);
        expect([MAP_WIDTH, MAP_HEIGHT]).toEqual([GEOMETRY.viewBox.width, GEOMETRY.viewBox.height]);
    });

    it("draws the land from the outline the backend checks the places against", () => {
        expect(LAND).toEqual(GEOMETRY.land);
    });

    it("has the regions the backend names, with the same polygons", () => {
        expect(REGIONS).toEqual(GEOMETRY.regions);
    });

    it("writes on the map exactly the labels of the contract", () => {
        expect(LABELS.map((label) => label.text).sort()).toEqual([...GEOMETRY.labels].sort());
    });

    it("keeps every label inside the map", () => {
        for (const label of LABELS) {
            expect(label.x).toBeGreaterThan(0);
            expect(label.x).toBeLessThan(VIEWBOX.width);
            expect(label.y).toBeGreaterThan(0);
            expect(label.y).toBeLessThan(VIEWBOX.height);
        }
    });
});

describe("map decoration", () => {
    it("is generated from fixed seeds, so the picture never changes", () => {
        const first = random(1954);
        const second = random(1954);
        const other = random(1955);
        const sequence = Array.from({ length: 5 }, () => first());

        expect(Array.from({ length: 5 }, () => second())).toEqual(sequence);
        expect(Array.from({ length: 5 }, () => other())).not.toEqual(sequence);
        expect(sequence.every((value) => value >= 0 && value < 1)).toBe(true);
    });

    it("keeps the mountains, trees and rivers on the map", () => {
        for (const peak of PEAKS) {
            expect(peak.x).toBeGreaterThan(0);
            expect(peak.x).toBeLessThan(VIEWBOX.width);
            expect(peak.y).toBeGreaterThan(0);
            expect(peak.y).toBeLessThan(VIEWBOX.height);
        }

        for (const river of RIVERS) {
            for (const [x, y] of river.path) {
                expect(x).toBeGreaterThanOrEqual(0);
                expect(x).toBeLessThanOrEqual(VIEWBOX.width);
                expect(y).toBeGreaterThanOrEqual(0);
                expect(y).toBeLessThanOrEqual(VIEWBOX.height);
            }
        }

        for (const lake of LAKES) {
            expect(pointInPolygon(lake.cx, lake.cy, LAND)).toBe(true);
        }
    });

    it("plants trees only inside a forest, and waves only at sea", () => {
        for (const tree of TREES) {
            expect(FORESTS.some((forest) => pointInPolygon(tree.x, tree.y, forest))).toBe(true);
        }

        for (const wave of WAVES) {
            expect(pointInPolygon(wave.x, wave.y, LAND)).toBe(false);
        }

        expect(TREES.length).toBeGreaterThan(100);
        expect(PEAKS.length).toBeGreaterThan(80);
        expect(WAVES.length).toBeGreaterThan(30);
    });

    it("keeps the forests on dry land", () => {
        for (const forest of FORESTS) {
            for (const [x, y] of forest) {
                expect(pointInPolygon(x, y, LAND)).toBe(true);
            }
        }
    });
});

describe("pointInPolygon", () => {
    const square = [
        [0, 0],
        [10, 0],
        [10, 10],
        [0, 10],
    ] as const;

    it("tells inside from outside", () => {
        expect(pointInPolygon(5, 5, square)).toBe(true);
        expect(pointInPolygon(15, 5, square)).toBe(false);
        expect(pointInPolygon(-1, 5, square)).toBe(false);
    });
});
