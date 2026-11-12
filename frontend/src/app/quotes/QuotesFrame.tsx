import type { CSSProperties, ReactNode } from "react";
import Image from "next/image";
import SceneNav from "@/components/SceneNav/SceneNav";
import styles from "./QuotesFrame.module.css";

interface QuotesFrameProps {
    title: string;
    // Shown under the title; the error state has nothing to introduce.
    description?: string;
    // Brand-only navigation, for the failed-load state.
    compactNav?: boolean;
    // Which banner to paint: the forest stream (Quotes) or the fire-breathing dragon (Leaderboard).
    scene?: "forest" | "dragon";
    // The small label above the title; pages that are not about exploring (the 404) say what they are.
    eyebrow?: string;
    // Lets a page lay out its own scene, e.g. to centre its content in the height left over.
    className?: string;
    children?: ReactNode;
}

// Photograph: an old oak wood with a stream, cropped 2:1 and kept low so the
// water stays in view (frontend/public/images/quotes-forest-stream.avif).
const SCENE_IMAGE = "/images/quotes-forest-stream.avif";

// Photograph: a dragon sculpture breathing fire over a crowd at dusk, for the Leaderboard
// (frontend/public/images/leaderboard-fire-dragon.avif).
const DRAGON_IMAGE = "/images/leaderboard-fire-dragon.avif";

// x/y are positions in the scene, s the size in px, d the loop in seconds.
const FIREFLIES = [
    { x: "9%", y: "58%", s: 2.5, d: 12, delay: 0.6, dx: 14, dy: 12 },
    { x: "22%", y: "74%", s: 2, d: 14, delay: 3, dx: -12, dy: 10 },
    { x: "46%", y: "52%", s: 2, d: 11, delay: 5, dx: 12, dy: 14 },
    { x: "63%", y: "70%", s: 3, d: 13, delay: 1.4, dx: -16, dy: 12 },
    { x: "78%", y: "46%", s: 2, d: 15, delay: 7, dx: 12, dy: 10 },
    { x: "88%", y: "66%", s: 2.5, d: 12, delay: 4, dx: -10, dy: 14 },
];

// The quotes page's scene: the forest banner, the Home's navigation capsule, the
// title and whatever the page puts below. Owns the <main> landmark.
export default function QuotesFrame({
    title,
    description,
    compactNav = false,
    scene = "forest",
    eyebrow = "Explore Middle-earth",
    className,
    children,
}: QuotesFrameProps) {
    const dragon = scene === "dragon";

    return (
        <div className={`${styles.page} ${dragon ? styles.dragon : ""} ${className ?? ""}`}>
            <div className={styles.scene} aria-hidden="true">
                <Image
                    src={dragon ? DRAGON_IMAGE : SCENE_IMAGE}
                    alt=""
                    fill
                    priority
                    sizes="100vw"
                    className={styles.image}
                />
                <div className={styles.light} />
                <div className={styles.fog} />
                <div className={styles.fireflies}>
                    {FIREFLIES.map((f) => (
                        <span
                            key={`${f.x}-${f.y}`}
                            className={styles.firefly}
                            style={
                                {
                                    "--x": f.x,
                                    "--y": f.y,
                                    "--s": `${f.s}px`,
                                    "--d": `${f.d}s`,
                                    "--delay": `${f.delay}s`,
                                    "--dx": `${f.dx}px`,
                                    "--dy": `${f.dy}px`,
                                } as CSSProperties
                            }
                        />
                    ))}
                </div>
                <div className={styles.shade} />
            </div>

            <SceneNav compact={compactNav} />

            <main className={styles.main}>
                <header className={styles.hero}>
                    <p className={styles.badge}>
                        <span className={styles.badgeDot} aria-hidden="true" />
                        {eyebrow}
                    </p>
                    <h1 className={styles.title}>{title}</h1>
                    {description && <p className={styles.description}>{description}</p>}
                </header>

                {children}
            </main>
        </div>
    );
}
