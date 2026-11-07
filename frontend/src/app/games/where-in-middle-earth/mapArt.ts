import { LAND, VIEWBOX, pointInPolygon, type Point } from "./mapGeometry";

// The decoration of the map: mountains, forests, rivers, lakes and the waves of the sea. All of
// it is original, drawn from the relations the sources state (see
// backend/src/main/resources/where-in-middle-earth/REFERENCES.md) and none of it is traced from a
// published map. It carries no names: the only text on the map is LABELS in mapGeometry.ts.
//
// Everything is generated from fixed seeds when the module loads, so the picture is the same on
// the server and in the browser, and on every render.

// A small deterministic random generator (mulberry32).
export function random(seed: number): () => number {
    let state = seed >>> 0;

    return () => {
        state = (state + 0x6d2b79f5) >>> 0;

        let t = state;

        t = Math.imul(t ^ (t >>> 15), t | 1);
        t ^= t + Math.imul(t ^ (t >>> 7), t | 61);

        return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
}

const round1 = (value: number) => Math.round(value * 10) / 10;

// A smooth curve through the points (Catmull-Rom turned into cubic Beziers).
export function smoothPath(points: readonly Point[], closed = false): string {
    if (points.length < 2) {
        return "";
    }

    const at = (index: number): Point => {
        if (closed) {
            return points[(index + points.length) % points.length];
        }

        return points[Math.min(points.length - 1, Math.max(0, index))];
    };

    const count = closed ? points.length : points.length - 1;
    let path = `M${round1(points[0][0])} ${round1(points[0][1])}`;

    for (let i = 0; i < count; i++) {
        const p0 = at(i - 1);
        const p1 = at(i);
        const p2 = at(i + 1);
        const p3 = at(i + 2);
        const c1x = p1[0] + (p2[0] - p0[0]) / 6;
        const c1y = p1[1] + (p2[1] - p0[1]) / 6;
        const c2x = p2[0] - (p3[0] - p1[0]) / 6;
        const c2y = p2[1] - (p3[1] - p1[1]) / 6;

        path += `C${round1(c1x)} ${round1(c1y)} ${round1(c2x)} ${round1(c2y)} ${round1(p2[0])} ${round1(p2[1])}`;
    }

    return closed ? `${path}Z` : path;
}

export const LAND_PATH = smoothPath(LAND, true);

// ---- Mountains -----------------------------------------------------

export interface MountainRange {
    // The ridge line.
    ridge: readonly Point[];
    // Distance between peaks along the ridge, and their size.
    spacing: number;
    size: number;
}

export const RANGES: readonly MountainRange[] = [
    {
        // The long range down the middle of the map, from the cold north to the Gap.
        ridge: [[410, 110], [404, 150], [400, 190], [398, 230], [402, 290], [404, 335], [406, 380], [396, 415], [382, 424]],
        spacing: 11,
        size: 1.15,
    },
    { ridge: [[410, 110], [372, 86], [338, 66], [318, 54]], spacing: 12, size: 1 },
    { ridge: [[410, 108], [480, 96], [560, 90], [640, 86], [720, 92], [780, 100]], spacing: 13, size: 1 },
    {
        ridge: [[330, 572], [380, 566], [430, 566], [470, 572], [520, 586], [555, 598]],
        spacing: 11,
        size: 1.1,
    },
    { ridge: [[385, 566], [392, 530], [396, 508]], spacing: 11, size: 0.9 },
    { ridge: [[672, 450], [664, 500], [660, 540], [662, 580], [676, 620], [720, 640], [800, 642]], spacing: 11, size: 1.05 },
    { ridge: [[694, 470], [730, 486], [770, 492], [820, 484], [880, 470]], spacing: 11, size: 1 },
    { ridge: [[74, 150], [86, 178], [100, 196]], spacing: 11, size: 0.85 },
    { ridge: [[104, 236], [118, 262], [136, 284]], spacing: 11, size: 0.85 },
];

export interface Peak {
    x: number;
    y: number;
    // Half the width and the height of the glyph.
    w: number;
    h: number;
}

// Points evenly spaced along a polyline, each moved a little off the line.
function along(polyline: readonly Point[], spacing: number, rand: () => number): [number, number, number][] {
    const points: [number, number, number][] = [];
    let carried = 0;

    for (let i = 0; i < polyline.length - 1; i++) {
        const [x1, y1] = polyline[i];
        const [x2, y2] = polyline[i + 1];
        const length = Math.hypot(x2 - x1, y2 - y1);
        const nx = -(y2 - y1) / length;
        const ny = (x2 - x1) / length;
        let at = carried;

        while (at <= length) {
            const side = (rand() - 0.5) * 9;
            const t = at / length;

            points.push([x1 + (x2 - x1) * t + nx * side, y1 + (y2 - y1) * t + ny * side, rand()]);
            at += spacing * (0.8 + rand() * 0.5);
        }

        carried = at - length;
    }

    return points;
}

export const PEAKS: readonly Peak[] = (() => {
    const rand = random(1954);
    const peaks: Peak[] = [];

    for (const range of RANGES) {
        for (const [x, y, r] of along(range.ridge, range.spacing, rand)) {
            const w = (5.5 + r * 3.5) * range.size;

            peaks.push({ x: round1(x), y: round1(y), w: round1(w), h: round1(w * (1.15 + r * 0.45)) });
        }
    }

    // Drawn from the back (north) to the front, so that the nearer peaks overlap the farther ones.
    return peaks.sort((a, b) => a.y - b.y);
})();

// ---- Forests -------------------------------------------------------

export const FORESTS: readonly (readonly Point[])[] = [
    // A great forest east of the river, widest in the north.
    [[498, 128], [540, 116], [596, 124], [638, 148], [650, 200], [646, 262], [612, 302], [572, 322], [534, 336], [504, 334], [496, 296], [500, 230], [496, 170]],
    // A golden wood between the mountains and the river.
    [[430, 334], [468, 334], [478, 366], [466, 392], [440, 390], [426, 362]],
    // A deep wood on the eastern flank of the range's southern end.
    [[414, 402], [466, 402], [486, 422], [470, 446], [432, 450], [410, 426]],
    // A small old wood west of some low hills.
    [[204, 254], [228, 252], [236, 274], [222, 290], [204, 282]],
];

export interface Tree {
    x: number;
    y: number;
    s: number;
}

export const TREES: readonly Tree[] = (() => {
    const rand = random(1937);
    const trees: Tree[] = [];

    for (const forest of FORESTS) {
        const xs = forest.map(([x]) => x);
        const ys = forest.map(([, y]) => y);
        const minX = Math.min(...xs);
        const maxX = Math.max(...xs);
        const minY = Math.min(...ys);
        const maxY = Math.max(...ys);
        const placed: Tree[] = [];

        for (let attempt = 0; attempt < 1400; attempt++) {
            const x = minX + rand() * (maxX - minX);
            const y = minY + rand() * (maxY - minY);

            if (!pointInPolygon(x, y, forest)) {
                continue;
            }

            if (placed.some((tree) => Math.hypot(tree.x - x, tree.y - y) < 10)) {
                continue;
            }

            placed.push({ x: round1(x), y: round1(y), s: round1(0.8 + rand() * 0.5) });
        }

        trees.push(...placed);
    }

    return trees.sort((a, b) => a.y - b.y);
})();

// ---- Water ---------------------------------------------------------

export const RIVERS: readonly { width: number; path: readonly Point[] }[] = [
    // The great river, down the whole map to the sea in the south.
    {
        width: 2.6,
        path: [[470, 100], [464, 150], [462, 205], [466, 260], [468, 310], [466, 352], [484, 376], [522, 384]],
    },
    {
        width: 2.6,
        path: [[540, 422], [546, 470], [556, 500], [570, 522], [590, 550], [602, 572], [612, 600], [618, 630], [630, 646], [640, 656]],
    },
    { width: 2, path: [[206, 126], [208, 160], [214, 190], [212, 222], [206, 252], [190, 280], [160, 300], [138, 296]] },
    { width: 1.6, path: [[98, 212], [130, 196], [150, 180]] },
    { width: 1.8, path: [[385, 226], [352, 246], [322, 262], [296, 296], [268, 320], [250, 328]] },
    { width: 1.6, path: [[372, 160], [362, 190], [350, 230], [322, 262]] },
    { width: 2, path: [[250, 328], [228, 352], [208, 370], [200, 372]] },
    { width: 1.4, path: [[335, 334], [300, 342], [270, 336], [250, 328]] },
    { width: 1.8, path: [[385, 438], [376, 462], [360, 482], [332, 490], [296, 486], [262, 478]] },
    { width: 1.8, path: [[440, 400], [452, 450], [480, 490], [512, 512], [548, 512], [556, 500]] },
    { width: 1.6, path: [[668, 162], [672, 190], [664, 212], [666, 230]] },
    { width: 1.5, path: [[600, 196], [630, 206], [656, 218]] },
];

export interface Lake {
    cx: number;
    cy: number;
    rx: number;
    ry: number;
}

export const LAKES: readonly Lake[] = [
    { cx: 178, cy: 98, rx: 34, ry: 17 },
    { cx: 534, cy: 398, rx: 13, ry: 20 },
    { cx: 672, cy: 262, rx: 20, ry: 46 },
    { cx: 830, cy: 385, rx: 55, ry: 40 },
    { cx: 860, cy: 600, rx: 50, ry: 28 },
];

// Short strokes on the open sea, away from the shore.
export const WAVES: readonly { x: number; y: number }[] = (() => {
    const rand = random(1892);
    const waves: { x: number; y: number }[] = [];

    for (let attempt = 0; attempt < 600 && waves.length < 70; attempt++) {
        const x = 14 + rand() * (VIEWBOX.width - 28);
        const y = 14 + rand() * (VIEWBOX.height - 28);

        if (pointInPolygon(x, y, LAND) || x > 300 || (x > 130 && y < 380)) {
            continue;
        }

        if (waves.some((wave) => Math.hypot(wave.x - x, wave.y - y) < 34)) {
            continue;
        }

        waves.push({ x: round1(x), y: round1(y) });
    }

    // The southern bay too.
    for (let attempt = 0; attempt < 200 && waves.length < 84; attempt++) {
        const x = 380 + rand() * 250;
        const y = 656 + rand() * 28;

        if (pointInPolygon(x, y, LAND) || waves.some((wave) => Math.hypot(wave.x - x, wave.y - y) < 34)) {
            continue;
        }

        waves.push({ x: round1(x), y: round1(y) });
    }

    return waves;
})();
