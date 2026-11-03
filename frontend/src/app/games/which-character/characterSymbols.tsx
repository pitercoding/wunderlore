import { useId, type ReactNode } from "react";

// One engraved symbol per character the backend can return (CharacterId in
// backend/.../whichcharacter/CharacterId.java): an object tied to the character
// in the books, drawn like a plate in an old field guide. Original, simple
// motifs: no film costume, likeness or promotional art. Sources and credits:
// frontend/public/images/ATTRIBUTIONS.md.

const INK = "#1f2a22";
const GOLD = "#8a6f35";

interface PlateProps {
    uid: string;
    children: ReactNode;
}

// The shared plate: hatching pattern, a ruled double border and a small ornament.
// All symbols draw in the 300x400 space inside it, with the same line weights:
// 2.4 for contours, 1.2 for detail, 0.7 for hatching.
function Plate({ uid, children }: PlateProps) {
    return (
        <>
            <defs>
                <pattern
                    id={`${uid}-h`}
                    width="4.5"
                    height="4.5"
                    patternUnits="userSpaceOnUse"
                    patternTransform="rotate(38)"
                >
                    <line x1="0" y1="0" x2="0" y2="4.5" stroke={INK} strokeWidth="0.7" opacity="0.6" />
                </pattern>
            </defs>

            <rect x="14" y="14" width="272" height="372" fill="none" stroke={INK} strokeWidth="1.2" opacity="0.7" />
            <rect x="22" y="22" width="256" height="356" fill="none" stroke={INK} strokeWidth="0.6" opacity="0.6" />
            <g stroke={GOLD} strokeWidth="1" fill="none" opacity="0.9">
                <path d="M118 354h26M156 354h26" />
                <path d="M150 349l5 5-5 5-5-5z" />
            </g>

            <g
                fill="none"
                stroke={INK}
                strokeWidth="2.4"
                strokeLinecap="round"
                strokeLinejoin="round"
            >
                {children}
            </g>
        </>
    );
}

type Draw = (hatch: string) => ReactNode;

const detail = { strokeWidth: 1.2 } as const;
const gold = { stroke: GOLD } as const;

// Frodo: the Ring on its chain.
const ring: Draw = (hatch) => (
    <>
        <path
            d="M74 205a76 76 0 1 0 152 0a76 76 0 1 0-152 0ZM96 205a54 54 0 1 0 108 0a54 54 0 1 0-108 0Z"
            fill={hatch}
            fillRule="evenodd"
        />
        <circle cx="150" cy="205" r="65" strokeDasharray="2 5" {...detail} />
        <path d="M88.9 182.8A65 65 0 0 1 127.8 143.9" {...gold} strokeWidth={3} />
        <path d="M150 129v-58" strokeDasharray="7 3" strokeWidth={2} />
        <ellipse cx="150" cy="66" rx="5" ry="7" {...detail} />
    </>
);

// Sam: a seedling in turned soil.
const seedling: Draw = (hatch) => (
    <>
        <path d="M78 322Q150 262 222 322Z" fill={hatch} />
        <path d="M64 322h172" {...detail} />
        <path d="M150 292C148 258 154 226 150 192" />
        <path d="M150 218C110 218 80 192 76 150C118 150 148 174 150 218Z" fill={hatch} />
        <path d="M150 214C128 192 104 172 84 154" {...detail} />
        <path d="M150 192C186 188 218 162 226 116C184 116 152 142 150 192Z" />
        <path d="M150 190C176 170 200 146 220 122M164 164l14 6M180 146l14 6M138 232l-14-10" {...detail} />
        <g {...gold} strokeWidth={2}>
            <ellipse cx="104" cy="310" rx="5" ry="3" />
            <ellipse cx="196" cy="312" rx="5" ry="3" />
            <ellipse cx="170" cy="304" rx="4" ry="2.6" />
        </g>
    </>
);

// Aragorn: a ranger's sword, point up.
const sword: Draw = (hatch) => (
    <>
        <path d="M150 62L164 238H136Z" />
        <path d="M150 90L164 238H150Z" fill={hatch} />
        <path d="M150 90V232" {...detail} />
        <path
            d="M96 246Q96 237 108 239H192Q204 237 204 246Q204 255 192 253H108Q96 255 96 246Z"
            {...gold}
        />
        <circle cx="96" cy="246" r="3" {...gold} {...detail} />
        <circle cx="204" cy="246" r="3" {...gold} {...detail} />
        <rect x="144" y="254" width="12" height="56" />
        <path d="M144 262l12 6M144 272l12 6M144 282l12 6M144 292l12 6M144 302l12 6" {...detail} />
        <circle cx="150" cy="322" r="11" {...gold} />
        <circle cx="150" cy="322" r="4" />
    </>
);

// Gandalf: a wizard's staff cradling a pale stone.
const staff: Draw = (hatch) => (
    <>
        <path d="M144 338C146 290 142 240 146 190C148 160 142 140 146 118" />
        <path d="M156 338C154 290 158 240 154 190C152 160 158 140 154 118" />
        <path d="M146 190C148 160 142 140 146 118L154 118C158 140 152 160 154 190Z" fill={hatch} />
        <path d="M146 120C126 116 112 98 118 78C122 66 132 62 138 68" />
        <path d="M154 120C174 116 188 98 182 78C178 66 168 62 162 68" />
        <ellipse cx="150" cy="232" rx="5" ry="8" {...detail} />
        <ellipse cx="149" cy="284" rx="4" ry="7" {...detail} />
        <path d="M144 338l6 12 6-12" {...detail} />
        <circle cx="150" cy="94" r="13" {...gold} />
        <path d="M150 84l6 10-6 10-6-10Z" {...gold} {...detail} />
        <path d="M150 62v-14M128 74l-9-9M172 74l9-9" {...gold} {...detail} />
    </>
);

// Sauron: the Great Eye, rimmed with fire. Rays are computed, rounded to stay stable.
const eyeRays = Array.from({ length: 24 }, (_, i) => {
    const a = (i / 24) * Math.PI * 2;
    const long = i % 2 === 0;
    const r0 = 62 + Math.abs(Math.cos(a)) * 22;
    const r1 = r0 + (long ? 24 : 12);
    const f = (n: number) => n.toFixed(1);
    return {
        d: `M${f(150 + Math.cos(a) * r0)} ${f(190 + Math.sin(a) * r0 * 0.78)}L${f(
            150 + Math.cos(a + 0.03) * r1,
        )} ${f(190 + Math.sin(a + 0.03) * r1 * 0.78)}`,
        long,
    };
});

const eye: Draw = (hatch) => (
    <>
        {eyeRays.map((ray) => (
            <path
                key={ray.d}
                d={ray.d}
                {...(ray.long ? gold : {})}
                strokeWidth={ray.long ? 1.8 : 1.2}
            />
        ))}
        <path d="M62 190Q150 108 238 190Q150 272 62 190Z" />
        <circle cx="150" cy="190" r="33" fill={hatch} />
        <circle cx="150" cy="190" r="33" {...gold} />
        <ellipse cx="150" cy="190" rx="5.5" ry="31" fill={INK} stroke="none" />
    </>
);

// Gollum: a fish, pale and slick, caught in the dark.
const fish: Draw = (hatch) => (
    <>
        <g transform="rotate(-16 150 200)">
            <path d="M68 200C100 148 190 148 222 200C190 252 100 252 68 200Z" />
            <path d="M70 202C100 250 190 250 222 202C190 226 100 226 70 202Z" fill={hatch} />
            <path d="M220 200L264 162C256 190 256 210 264 238Z" />
            <path d="M130 160C145 136 176 136 192 160" {...gold} />
            <path d="M150 246C158 262 172 264 180 252" {...detail} />
            <path d="M120 168C109 190 109 210 120 232" {...detail} />
            <circle cx="94" cy="192" r="6" {...detail} />
            <circle cx="94" cy="192" r="2" fill={INK} stroke="none" />
            <path d="M96 214q8 5 16 0" {...detail} />
            <path
                d="M138 188q5 6 10 0M152 188q5 6 10 0M166 188q5 6 10 0M180 188q5 6 10 0M145 200q5 6 10 0M159 200q5 6 10 0M173 200q5 6 10 0M187 200q5 6 10 0M138 212q5 6 10 0M152 212q5 6 10 0M166 212q5 6 10 0M180 212q5 6 10 0"
                {...detail}
            />
        </g>
        <path d="M62 306q14-6 28 0t28 0t28 0t28 0t28 0t28 0" {...gold} {...detail} />
        <path d="M86 322q14-5 26 0t26 0t26 0t26 0" {...detail} opacity={0.6} />
    </>
);

// Legolas: a long bow, drawn, with a fletched arrow.
const bow: Draw = (hatch) => (
    <>
        <path d="M196 66C64 128 64 272 196 334" strokeWidth={3.2} />
        <path d="M200 70C84 132 84 268 200 330" {...detail} />
        <path d="M196 66C70 130 78 150 90 190L96 190C86 150 110 110 200 70Z" fill={hatch} stroke="none" />
        <path d="M76 188l20 4M75 196l22 4M75 204l22 4M77 212l18 4" {...gold} strokeWidth={2} />
        <path d="M196 66L222 200L196 334" {...detail} />
        <path d="M222 200H44" strokeWidth={2} />
        <path d="M32 200L58 189L51 200L58 211Z" fill={INK} />
        <path d="M216 200l14-10M216 200l14 10M206 200l14-10M206 200l14 10M196 200l14-10M196 200l14 10" {...gold} strokeWidth={2} />
    </>
);

// Gimli: a dwarf's axe, bearded head and runeless chevrons.
const axe: Draw = (hatch) => (
    <>
        <path d="M146 328V80M154 328V80" />
        <path d="M146 80L150 66L154 80" {...detail} />
        <path d="M142 328h16l-4 12h-8z" {...detail} />
        <path d="M146 240l8 6M146 250l8 6M146 260l8 6M146 270l8 6" {...detail} />
        <path d="M146 288h8M146 298h8" {...gold} strokeWidth={2.4} />
        <path d="M154 96L232 64C262 92 272 140 262 190C258 208 248 224 236 238C214 214 184 204 154 196Z" />
        <path d="M154 98L184 88V204C174 202 164 200 154 196Z" fill={hatch} />
        <path d="M250 84C258 112 264 150 256 190C252 204 246 216 240 226" {...detail} />
        <path d="M146 104L116 98V130L146 140Z" fill={hatch} />
        <path d="M214 118L238 146L214 174L190 146Z" {...gold} />
        <path d="M214 132L226 146L214 160L202 146Z" {...gold} {...detail} />
        <circle cx="214" cy="146" r="2" fill={INK} stroke="none" />
    </>
);

// Galadriel: a star in a ring of light, and two leaves of the golden wood.
const starPoints = Array.from({ length: 16 }, (_, k) => {
    const a = ((k * 22.5 - 90) * Math.PI) / 180;
    const r = k % 2 === 0 ? (k % 4 === 0 ? 98 : 60) : 16;
    return [150 + Math.cos(a) * r, 168 + Math.sin(a) * r] as const;
});
const pt = ([x, y]: readonly [number, number]) => `${x.toFixed(1)} ${y.toFixed(1)}`;

const star: Draw = (hatch) => (
    <>
        <circle cx="150" cy="168" r="112" strokeDasharray="1 7" {...gold} strokeWidth={1.8} />
        <circle cx="150" cy="168" r="118" {...gold} {...detail} opacity={0.6} />
        <path d={`M${starPoints.map(pt).join("L")}Z`} />
        {Array.from({ length: 8 }, (_, i) => {
            const tip = starPoints[i * 2];
            const next = starPoints[(i * 2 + 1) % 16];
            return (
                <g key={i}>
                    <path d={`M150 168L${pt(tip)}`} {...detail} />
                    <path d={`M150 168L${pt(tip)}L${pt(next)}Z`} fill={hatch} stroke="none" />
                </g>
            );
        })}
        <circle cx="150" cy="168" r="5" {...gold} />
        <path d="M150 332C122 330 104 314 100 292C126 294 146 308 150 332Z" fill={hatch} />
        <path d="M150 332C178 330 196 314 200 292C174 294 154 308 150 332Z" />
        <path d="M150 330C138 318 120 304 106 296M150 330C162 318 180 304 194 296" {...detail} />
    </>
);

// Boromir: the great horn of Gondor, banded in silver (here, gold ink).
const horn: Draw = (hatch) => (
    <>
        <path d="M214 92C250 170 220 260 110 304C100 306 92 296 98 284C160 238 190 190 176 104Z" />
        <path d="M214 92C250 170 220 260 110 304C170 262 196 190 196 98Z" fill={hatch} />
        <ellipse cx="195" cy="98" rx="24" ry="8" transform="rotate(-25 195 98)" {...gold} />
        <path d="M227.6 139.8L179.3 151.3M226 146L178 158M216.8 210.8L165.5 209M214 217L164 216" {...gold} strokeWidth={2.4} />
        <path d="M120 298C140 286 162 262 176 236" {...detail} />
    </>
);

export const CHARACTER_SYMBOLS = {
    FRODO: { label: "a ring on a chain", draw: ring },
    SAM: { label: "a seedling in the soil", draw: seedling },
    ARAGORN: { label: "a ranger's sword", draw: sword },
    GANDALF: { label: "a wizard's staff", draw: staff },
    SAURON: { label: "a rayed, burning eye", draw: eye },
    GOLLUM: { label: "a raw fish", draw: fish },
    LEGOLAS: { label: "a bow and a drawn arrow", draw: bow },
    GIMLI: { label: "a dwarf's axe", draw: axe },
    GALADRIEL: { label: "a star above two leaves", draw: star },
    BOROMIR: { label: "the horn of Gondor", draw: horn },
} as const;

export type CharacterSymbolId = keyof typeof CHARACTER_SYMBOLS;

// The backend sends the enum name ("GANDALF"); matching ignores case so a
// differently cased identifier still resolves to its own symbol, never another's.
// Unknown identifiers return null: the caller shows a text-only fallback.
export function getCharacterSymbol(id: string) {
    const key = id.trim().toUpperCase();

    return Object.hasOwn(CHARACTER_SYMBOLS, key)
        ? CHARACTER_SYMBOLS[key as CharacterSymbolId]
        : null;
}

interface CharacterSymbolProps {
    id: string;
    name?: string;
    className?: string;
    // The page already names the character: the plate is purely decorative.
    decorative?: boolean;
}

// The symbol plate, 3:4. Null for an identifier without a symbol.
export default function CharacterSymbol({
    id,
    name,
    className,
    decorative = false,
}: CharacterSymbolProps) {
    const uid = useId().replace(/:/g, "");
    const symbol = getCharacterSymbol(id);

    if (!symbol) {
        return null;
    }

    return (
        <svg
            viewBox="0 0 300 400"
            className={className}
            {...(decorative
                ? { "aria-hidden": true, focusable: false }
                : {
                      role: "img",
                      "aria-label": `${name ? `${name}: ` : ""}${symbol.label}`,
                  })}
        >
            <Plate uid={uid}>{symbol.draw(`url(#${uid}-h)`)}</Plate>
        </svg>
    );
}
