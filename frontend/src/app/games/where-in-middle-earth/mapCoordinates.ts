import { MAP_HEIGHT, MAP_WIDTH, type MapPosition } from "@/lib/api/whereInMiddleEarth";

// Where a click on the picture lands on the map, in the map's own units (the SVG viewBox).
//
// The SVG is drawn with preserveAspectRatio="xMidYMid meet": the whole viewBox is scaled by one
// factor and centred in the element, so on an element whose shape differs from 1000:700 there are
// empty bands left and right, or above and below. Dividing the offset inside the element by its
// width and height would stretch the click over those bands; the factor and the offsets have to
// be removed first. In a browser the SVG's own transformation (getScreenCTM) is used, which also
// accounts for CSS transforms; projectClientPoint is the same arithmetic for the plain case, used
// where getScreenCTM is not available and kept apart so that it can be tested for any shape.

export interface ClientRect {
    left: number;
    top: number;
    width: number;
    height: number;
}

// The point of the viewBox under a client position, or null when the position is on one of the
// empty bands (or the element has no size).
export function projectClientPoint(
    rect: ClientRect,
    clientX: number,
    clientY: number,
    viewBox: { width: number; height: number } = { width: MAP_WIDTH, height: MAP_HEIGHT },
): MapPosition | null {
    if (!(rect.width > 0) || !(rect.height > 0)) {
        return null;
    }

    const scale = Math.min(rect.width / viewBox.width, rect.height / viewBox.height);
    const offsetX = (rect.width - viewBox.width * scale) / 2;
    const offsetY = (rect.height - viewBox.height * scale) / 2;
    const x = (clientX - rect.left - offsetX) / scale;
    const y = (clientY - rect.top - offsetY) / scale;

    return isOnMap(x, y) ? { x, y } : null;
}

export function isOnMap(x: number, y: number): boolean {
    return (
        Number.isFinite(x) && Number.isFinite(y) && x >= 0 && x <= MAP_WIDTH && y >= 0 && y <= MAP_HEIGHT
    );
}

// One decimal, inside the map: the form in which a position is shown, kept and sent, so that the
// same click always gives the same answer.
export function normalizePosition(position: MapPosition): MapPosition {
    const clamp = (value: number, max: number) => Math.min(max, Math.max(0, value));
    const round = (value: number) => Math.round(value * 10) / 10;

    return {
        x: round(clamp(position.x, MAP_WIDTH)),
        y: round(clamp(position.y, MAP_HEIGHT)),
    };
}

// A pointer position on an SVG element, in the SVG's own units; null outside the picture.
export function clientToMapPoint(
    svg: SVGSVGElement,
    clientX: number,
    clientY: number,
): MapPosition | null {
    const matrix = typeof svg.getScreenCTM === "function" ? svg.getScreenCTM() : null;

    if (matrix && typeof svg.createSVGPoint === "function") {
        const point = svg.createSVGPoint();

        point.x = clientX;
        point.y = clientY;

        const local = point.matrixTransform(matrix.inverse());

        return isOnMap(local.x, local.y) ? normalizePosition(local) : null;
    }

    const projected = projectClientPoint(svg.getBoundingClientRect(), clientX, clientY);

    return projected ? normalizePosition(projected) : null;
}

// Keyboard control: how far an arrow key moves the marker.
export const KEY_STEP = 10;
export const KEY_STEP_LARGE = 40;

export function describePosition(position: MapPosition): string {
    const east = Math.round((position.x / MAP_WIDTH) * 100);
    const south = Math.round((position.y / MAP_HEIGHT) * 100);

    return `${east}% of the way from the west edge, ${south}% of the way down from the north edge`;
}
