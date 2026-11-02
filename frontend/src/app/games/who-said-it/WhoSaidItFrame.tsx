import type { ReactNode } from "react";
import Image from "next/image";
import SceneNav from "@/components/SceneNav/SceneNav";
import styles from "./WhoSaidItFrame.module.css";

interface WhoSaidItFrameProps {
    title: string;
    // Shown under the title; the failed-load state has nothing to introduce.
    description?: string;
    // Brand-only navigation, for the failed-load state.
    compactNav?: boolean;
    children?: ReactNode;
}

// Artwork: a lone hooded traveler in a misty old forest, mirrored so the figure
// stands on the right, clear of the title (frontend/public/images/who-said-it-hooded-path.avif,
// 2400x1350, optimized from a downloaded original whose license is still unverified).
const SCENE_IMAGE = "/images/who-said-it-hooded-path.avif";

// The game's scene: the traveler banner under a dark green wash, in the Home's and
// Quotes' navigation capsule and palette. Owns the <main> landmark.
export default function WhoSaidItFrame({
    title,
    description,
    compactNav = false,
    children,
}: WhoSaidItFrameProps) {
    return (
        <div className={styles.page}>
            <div className={styles.scene} aria-hidden="true">
                <Image
                    src={SCENE_IMAGE}
                    alt=""
                    fill
                    priority
                    sizes="100vw"
                    className={styles.image}
                />
                <div className={styles.tint} />
                <div className={styles.fog} />
                <div className={styles.shade} />
            </div>

            <SceneNav compact={compactNav} />

            <main className={styles.main}>
                <header className={styles.hero}>
                    <p className={styles.badge}>
                        <span className={styles.badgeDot} aria-hidden="true" />
                        Guess the speaker
                    </p>
                    <h1 className={styles.title}>{title}</h1>
                    {description && <p className={styles.description}>{description}</p>}
                </header>

                {children}
            </main>
        </div>
    );
}
