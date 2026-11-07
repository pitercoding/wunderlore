import { MAP_HEIGHT, MAP_WIDTH } from "@/lib/api/whereInMiddleEarth";

// What the map has in common with the backend: its coordinate system, the outline of the land, the
// broad regions and the only text written on it. A test compares every value here with
// contracts/where-in-middle-earth/map-geometry.json, which the backend tests check the places
// against (every place on land, inside its region, none named on the map).

export type Point = readonly [number, number];

export const VIEWBOX = { width: MAP_WIDTH, height: MAP_HEIGHT } as const;

export const LAND: readonly Point[] = [
    [172, 14], [986, 14], [986, 688], [640, 688], [636, 664], [600, 654], [560, 652], [520, 650],
    [496, 652], [484, 670], [468, 676], [454, 664], [446, 652], [430, 648], [414, 642], [396, 646],
    [372, 652], [350, 640], [330, 612], [312, 580], [300, 548], [284, 516], [262, 478], [240, 440],
    [220, 408], [200, 372], [186, 346], [160, 318], [138, 296], [118, 282], [104, 268], [98, 250],
    [64, 238], [50, 228], [88, 222], [90, 206], [50, 198], [48, 176], [58, 150], [82, 120],
    [108, 90], [138, 56],
];

export interface MapRegion {
    id: string;
    name: string;
    polygon: readonly Point[];
}

export const REGIONS: readonly MapRegion[] = [
    { id: "eriador", name: "Eriador", polygon: [[50, 100], [396, 100], [396, 420], [50, 420]] },
    { id: "north", name: "Angmar and the North", polygon: [[140, 14], [520, 14], [520, 125], [140, 125]] },
    { id: "misty-mountains", name: "Misty Mountains", polygon: [[380, 250], [440, 250], [440, 400], [380, 400]] },
    { id: "wilderland", name: "Wilderland", polygon: [[435, 125], [900, 125], [900, 400], [435, 400]] },
    { id: "great-river", name: "Great River Lands", polygon: [[490, 360], [650, 360], [650, 440], [490, 440]] },
    { id: "rohan", name: "Rohan and the Gap", polygon: [[330, 405], [520, 405], [520, 580], [330, 580]] },
    { id: "gondor", name: "Gondor", polygon: [[400, 470], [660, 470], [660, 670], [400, 670]] },
    { id: "mordor", name: "Mordor", polygon: [[660, 430], [900, 430], [900, 640], [660, 640]] },
    { id: "south", name: "Umbar and the South", polygon: [[620, 655], [986, 655], [986, 688], [620, 688]] },
];

export type LabelKind = "region" | "sea" | "range" | "river";

// Where and how each label is written. Large regions, seas, ranges and a river only: none of
// them is the name of a place the game asks about.
export interface MapLabel {
    text: string;
    kind: LabelKind;
    x: number;
    y: number;
    // Degrees, clockwise.
    rotate?: number;
}

export const LABELS: readonly MapLabel[] = [
    { text: "Eriador", kind: "region", x: 205, y: 314 },
    { text: "Angmar", kind: "region", x: 238, y: 54 },
    { text: "Misty Mountains", kind: "range", x: 444, y: 262, rotate: -90 },
    { text: "Mirkwood", kind: "region", x: 572, y: 262 },
    { text: "Wilderland", kind: "region", x: 745, y: 290 },
    { text: "Rhûn", kind: "region", x: 905, y: 205 },
    { text: "Rohan", kind: "region", x: 426, y: 470 },
    { text: "Gondor", kind: "region", x: 548, y: 626 },
    { text: "Mordor", kind: "region", x: 800, y: 598 },
    { text: "Harad", kind: "region", x: 862, y: 668 },
    { text: "Belegaer", kind: "sea", x: 118, y: 430, rotate: -90 },
    { text: "Bay of Belfalas", kind: "sea", x: 560, y: 676 },
    { text: "Sea of Rhûn", kind: "sea", x: 830, y: 386 },
    { text: "White Mountains", kind: "range", x: 436, y: 590, rotate: 5 },
    { text: "Ered Luin", kind: "range", x: 126, y: 262, rotate: 48 },
    { text: "Anduin", kind: "river", x: 534, y: 482, rotate: 75 },
];

// Even-odd ray casting.
export function pointInPolygon(x: number, y: number, polygon: readonly Point[]): boolean {
    let inside = false;

    for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
        const [xi, yi] = polygon[i];
        const [xj, yj] = polygon[j];

        if (yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi) + xi) {
            inside = !inside;
        }
    }

    return inside;
}
