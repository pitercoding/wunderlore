"use client";

import {
    memo,
    useEffect,
    useId,
    useRef,
    useState,
    type KeyboardEvent,
    type MouseEvent,
} from "react";
import { MAP_HEIGHT, MAP_WIDTH, type MapPosition } from "@/lib/api/whereInMiddleEarth";
import { FORESTS, LAKES, LAND_PATH, PEAKS, RANGES, RIVERS, TREES, WAVES, smoothPath } from "./mapArt";
import {
    KEY_STEP,
    KEY_STEP_LARGE,
    clientToMapPoint,
    describePosition,
    normalizePosition,
} from "./mapCoordinates";
import { LABELS, VIEWBOX } from "./mapGeometry";
import styles from "./MiddleEarthMap.module.css";

// What the map shows once a round is answered: where the player clicked, where the place really
// is, and how far apart they were.
export interface MapFeedback {
    guess: MapPosition;
    answer: MapPosition;
    answerLabel: string;
    distance: number;
}

interface MiddleEarthMapProps {
    // The point chosen and not yet confirmed.
    selection: MapPosition | null;
    onSelect: (position: MapPosition) => void;
    onClear: () => void;
    // True while an answer is being checked or the round is over: the map keeps showing, but
    // ignores clicks and keys.
    disabled?: boolean;
    feedback?: MapFeedback | null;
}

const ZOOM_LEVELS = [1, 1.5, 2, 3] as const;

// The radius, in map units, of the best scoring band: drawn around the real place in the
// feedback so that the player sees how close "excellent" is. Display only; the backend scores.
const EXCELLENT_RADIUS = 15;

const toPercent = (value: number, max: number) => `${(value / max) * 100}%`;

// The picture itself: sea, land, mountains, forests, rivers, the compass and the frame. It never
// changes, so it is drawn once and kept out of the way of the markers.
const MapArtwork = memo(function MapArtwork({ ids }: { ids: Record<string, string> }) {
    const ridgeLines = RANGES.map((range) => smoothPath(range.ridge));

    return (
        <>
            <defs>
                <linearGradient id={ids.sea} x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0" className={styles.seaTop} />
                    <stop offset="1" className={styles.seaBottom} />
                </linearGradient>
                <radialGradient id={ids.vignette} cx="50%" cy="50%" r="75%">
                    <stop offset="0.6" stopColor="#000" stopOpacity="0" />
                    <stop offset="1" stopColor="#000" stopOpacity="0.38" />
                </radialGradient>
                <clipPath id={ids.land}>
                    <path d={LAND_PATH} />
                </clipPath>
                <filter id={ids.paper} x="0" y="0" width="100%" height="100%">
                    <feTurbulence type="fractalNoise" baseFrequency="0.011 0.018" numOctaves="3" seed="7" />
                    <feColorMatrix
                        type="matrix"
                        values="0 0 0 0 0.30  0 0 0 0 0.27  0 0 0 0 0.14  0 0 0 0.9 -0.28"
                    />
                </filter>
                <filter id={ids.grain} x="0" y="0" width="100%" height="100%">
                    <feTurbulence type="fractalNoise" baseFrequency="0.85" numOctaves="2" seed="3" />
                    <feColorMatrix
                        type="matrix"
                        values="0 0 0 0 0.2  0 0 0 0 0.18  0 0 0 0 0.1  0 0 0 0.55 -0.16"
                    />
                </filter>
            </defs>

            <rect width={VIEWBOX.width} height={VIEWBOX.height} fill={`url(#${ids.sea})`} />

            <g className={styles.waves}>
                {WAVES.map((wave) => (
                    <path
                        key={`${wave.x}-${wave.y}`}
                        d={`M${wave.x - 9} ${wave.y}q4.5 -5 9 0t9 0`}
                    />
                ))}
            </g>

            {/* The coast fades into the sea in three bands. */}
            <path d={LAND_PATH} className={`${styles.shore} ${styles.shoreWide}`} />
            <path d={LAND_PATH} className={`${styles.shore} ${styles.shoreMid}`} />
            <path d={LAND_PATH} className={`${styles.shore} ${styles.shoreNear}`} />

            <path d={LAND_PATH} className={styles.land} />

            <g clipPath={`url(#${ids.land})`}>
                <rect width={VIEWBOX.width} height={VIEWBOX.height} filter={`url(#${ids.paper})`} opacity="0.55" />
                <rect width={VIEWBOX.width} height={VIEWBOX.height} filter={`url(#${ids.grain})`} opacity="0.5" />
                <g className={styles.graticule}>
                    {[100, 200, 300, 400, 500, 600, 700, 800, 900].map((x) => (
                        <line key={`x${x}`} x1={x} y1="0" x2={x} y2={VIEWBOX.height} />
                    ))}
                    {[100, 200, 300, 400, 500, 600].map((y) => (
                        <line key={`y${y}`} x1="0" y1={y} x2={VIEWBOX.width} y2={y} />
                    ))}
                </g>
                {/* The higher ground sits in a darker tone along each range. */}
                <g className={styles.foothills}>
                    {ridgeLines.map((d, index) => (
                        <path key={index} d={d} />
                    ))}
                </g>
            </g>

            <path d={LAND_PATH} className={styles.coast} />

            <g className={styles.lakes}>
                {LAKES.map((lake) => (
                    <ellipse key={`${lake.cx}-${lake.cy}`} cx={lake.cx} cy={lake.cy} rx={lake.rx} ry={lake.ry} />
                ))}
            </g>

            <g className={styles.rivers}>
                {RIVERS.map((river) => (
                    <path
                        key={river.path[0].join(",")}
                        d={smoothPath(river.path)}
                        strokeWidth={river.width}
                    />
                ))}
            </g>

            <g className={styles.forests}>
                {FORESTS.map((forest, index) => (
                    <path key={index} d={smoothPath(forest, true)} className={styles.forestFloor} />
                ))}
                {TREES.map((tree) => (
                    <path
                        key={`${tree.x}-${tree.y}`}
                        d={`M${tree.x} ${tree.y - 5 * tree.s}l${3.4 * tree.s} ${7 * tree.s}h${-6.8 * tree.s}z`}
                        className={styles.tree}
                    />
                ))}
            </g>

            <g className={styles.peaks}>
                {PEAKS.map((peak) => (
                    <g key={`${peak.x}-${peak.y}`}>
                        <path
                            d={`M${peak.x - peak.w} ${peak.y}L${peak.x} ${peak.y - peak.h}L${peak.x + peak.w} ${peak.y}Z`}
                            className={styles.peak}
                        />
                        <path
                            d={`M${peak.x} ${peak.y - peak.h}L${peak.x + peak.w} ${peak.y}L${peak.x + peak.w * 0.1} ${peak.y}Z`}
                            className={styles.peakShade}
                        />
                    </g>
                ))}
            </g>

            <g className={styles.labels}>
                {LABELS.map((label) => (
                    <text
                        key={label.text}
                        x={label.x}
                        y={label.y}
                        textAnchor="middle"
                        className={styles[`label_${label.kind}`]}
                        transform={label.rotate ? `rotate(${label.rotate} ${label.x} ${label.y})` : undefined}
                    >
                        {label.text}
                    </text>
                ))}
            </g>

            {/* A compass rose in the north-western sea: eight points, the northern one drawn long. */}
            <g transform="translate(70 72)" className={styles.compass}>
                <circle r="34" className={styles.compassRing} />
                <circle r="26" className={styles.compassRing} />
                <path d="M0 -42L6 -6L0 0L-6 -6Z" className={styles.compassNorth} />
                <path d="M0 34L6 6L0 0L-6 6ZM34 0L6 -6L0 0L6 6ZM-34 0L-6 -6L0 0L-6 6Z" className={styles.compassPoint} />
                <path d="M23 -23L4 -4L0 0L4 4ZM-23 -23L-4 -4L0 0L-4 4ZM23 23L4 4L0 0L4 -4ZM-23 23L-4 4L0 0L-4 -4Z" className={styles.compassMinor} />
                <path d="M-5 -49V-60L5 -49V-60" className={styles.compassLetter} />
            </g>

            <rect width={VIEWBOX.width} height={VIEWBOX.height} fill={`url(#${ids.vignette})`} pointerEvents="none" />
            <rect x="9" y="9" width={VIEWBOX.width - 18} height={VIEWBOX.height - 18} className={styles.frameOuter} />
            <rect x="15" y="15" width={VIEWBOX.width - 30} height={VIEWBOX.height - 30} className={styles.frameInner} />
        </>
    );
});

export default function MiddleEarthMap({
    selection,
    onSelect,
    onClear,
    disabled = false,
    feedback = null,
}: MiddleEarthMapProps) {
    const baseId = useId().replace(/:/g, "");
    const ids = {
        sea: `${baseId}-sea`,
        vignette: `${baseId}-vignette`,
        land: `${baseId}-land`,
        paper: `${baseId}-paper`,
        grain: `${baseId}-grain`,
        title: `${baseId}-title`,
        desc: `${baseId}-desc`,
        help: `${baseId}-help`,
    };

    const svgRef = useRef<SVGSVGElement>(null);
    const viewportRef = useRef<HTMLDivElement>(null);
    const [zoom, setZoom] = useState<(typeof ZOOM_LEVELS)[number]>(1);

    // The latest selection, for the effect that re-centres the view after a zoom without making
    // the zoom depend on every click.
    const selectionRef = useRef(selection);

    useEffect(() => {
        selectionRef.current = selection;
    }, [selection]);

    useEffect(() => {
        const viewport = viewportRef.current;

        if (!viewport || zoom === 1) {
            return;
        }

        const focus = selectionRef.current ?? { x: MAP_WIDTH / 2, y: MAP_HEIGHT / 2 };

        viewport.scrollLeft = (focus.x / MAP_WIDTH) * viewport.scrollWidth - viewport.clientWidth / 2;
        viewport.scrollTop = (focus.y / MAP_HEIGHT) * viewport.scrollHeight - viewport.clientHeight / 2;
    }, [zoom]);

    function handleClick(event: MouseEvent<SVGSVGElement>) {
        if (disabled || !svgRef.current) {
            return;
        }

        const position = clientToMapPoint(svgRef.current, event.clientX, event.clientY);

        if (position) {
            onSelect(position);
        }
    }

    // The same choice without a pointer: the arrow keys move the marker (starting from the middle
    // of the map), Shift makes the step four times larger, Escape takes the marker away.
    function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
        if (disabled) {
            return;
        }

        if (event.key === "Escape" || event.key === "Delete" || event.key === "Backspace") {
            if (selection) {
                event.preventDefault();
                onClear();
            }

            return;
        }

        const step = event.shiftKey ? KEY_STEP_LARGE : KEY_STEP;
        const moves: Record<string, [number, number]> = {
            ArrowLeft: [-step, 0],
            ArrowRight: [step, 0],
            ArrowUp: [0, -step],
            ArrowDown: [0, step],
        };
        const move = moves[event.key];

        if (!move) {
            return;
        }

        event.preventDefault();

        if (!selection) {
            onSelect(normalizePosition({ x: MAP_WIDTH / 2, y: MAP_HEIGHT / 2 }));
            return;
        }

        onSelect(normalizePosition({ x: selection.x + move[0], y: selection.y + move[1] }));
    }

    const zoomIndex = ZOOM_LEVELS.indexOf(zoom);

    return (
        <div className={styles.map} data-disabled={disabled || undefined}>
            <div className={styles.toolbar}>
                <p className={styles.zoomLabel} aria-live="polite">
                    Zoom {zoom}×
                </p>
                <div className={styles.zoomButtons}>
                    <button
                        type="button"
                        className={styles.zoomButton}
                        onClick={() => setZoom(ZOOM_LEVELS[Math.max(0, zoomIndex - 1)])}
                        disabled={zoomIndex === 0}
                        aria-label="Zoom out"
                    >
                        <span aria-hidden="true">−</span>
                    </button>
                    <button
                        type="button"
                        className={styles.zoomButton}
                        onClick={() =>
                            setZoom(ZOOM_LEVELS[Math.min(ZOOM_LEVELS.length - 1, zoomIndex + 1)])
                        }
                        disabled={zoomIndex === ZOOM_LEVELS.length - 1}
                        aria-label="Zoom in"
                    >
                        <span aria-hidden="true">+</span>
                    </button>
                </div>
            </div>

            <div
                ref={viewportRef}
                className={styles.viewport}
                role="group"
                aria-label="Map of Middle-earth. Press the arrow keys to place the marker."
                aria-describedby={ids.help}
                tabIndex={0}
                onKeyDown={handleKeyDown}
            >
                <div className={styles.canvas} style={{ width: `${zoom * 100}%` }} data-zoom={zoom}>
                    <svg
                        ref={svgRef}
                        className={styles.svg}
                        viewBox={`0 0 ${VIEWBOX.width} ${VIEWBOX.height}`}
                        preserveAspectRatio="xMidYMid meet"
                        role="img"
                        aria-labelledby={`${ids.title} ${ids.desc}`}
                        onClick={handleClick}
                    >
                        <title id={ids.title}>Map of Middle-earth</title>
                        <desc id={ids.desc}>
                            An illustrated, unlabelled map: the western sea and its coast, mountain
                            ranges running north to south and along the edges of the southern lands,
                            great forests, rivers and lakes, with only the large regions named. Click
                            or tap it to place a marker.
                        </desc>

                        <MapArtwork ids={ids} />

                        {feedback && (
                            <g className={styles.feedbackLayer} pointerEvents="none">
                                <circle
                                    cx={feedback.answer.x}
                                    cy={feedback.answer.y}
                                    r={EXCELLENT_RADIUS}
                                    className={styles.zone}
                                />
                                <line
                                    x1={feedback.guess.x}
                                    y1={feedback.guess.y}
                                    x2={feedback.answer.x}
                                    y2={feedback.answer.y}
                                    className={styles.lineHalo}
                                    vectorEffect="non-scaling-stroke"
                                />
                                <line
                                    x1={feedback.guess.x}
                                    y1={feedback.guess.y}
                                    x2={feedback.answer.x}
                                    y2={feedback.answer.y}
                                    className={styles.line}
                                    vectorEffect="non-scaling-stroke"
                                />
                            </g>
                        )}
                    </svg>

                    {/* Markers are ordinary elements over the picture, so they keep a readable size
                        however far the map is scaled down. The feedback text carries the same
                        information for assistive technology. */}
                    <div className={styles.markers} aria-hidden="true">
                        {selection && !feedback && (
                            <span
                                className={`${styles.marker} ${styles.pending}`}
                                data-marker="selection"
                                style={{
                                    left: toPercent(selection.x, MAP_WIDTH),
                                    top: toPercent(selection.y, MAP_HEIGHT),
                                }}
                            />
                        )}

                        {feedback && (
                            <>
                                <span
                                    className={`${styles.marker} ${styles.guess}`}
                                    data-marker="guess"
                                    style={{
                                        left: toPercent(feedback.guess.x, MAP_WIDTH),
                                        top: toPercent(feedback.guess.y, MAP_HEIGHT),
                                    }}
                                >
                                    <span className={styles.chip}>Your guess</span>
                                </span>
                                <span
                                    className={`${styles.marker} ${styles.answer}`}
                                    data-marker="answer"
                                    style={{
                                        left: toPercent(feedback.answer.x, MAP_WIDTH),
                                        top: toPercent(feedback.answer.y, MAP_HEIGHT),
                                    }}
                                >
                                    <span className={styles.diamond} />
                                    <span className={`${styles.chip} ${styles.chipAnswer}`}>
                                        {feedback.answerLabel}
                                    </span>
                                </span>
                            </>
                        )}
                    </div>
                </div>
            </div>

            <p id={ids.help} className={styles.help}>
                {disabled
                    ? "The round is over."
                    : "Click or tap the map to place your marker, then click again to move it. With a keyboard, focus the map and use the arrow keys (Shift for bigger steps); Escape clears the marker."}
            </p>

            <p role="status" className={styles.srOnly}>
                {selection && !feedback ? `Marker placed: ${describePosition(selection)}.` : ""}
            </p>

            {selection && !disabled && (
                <button type="button" className={styles.clear} onClick={onClear}>
                    Clear marker
                </button>
            )}
        </div>
    );
}
