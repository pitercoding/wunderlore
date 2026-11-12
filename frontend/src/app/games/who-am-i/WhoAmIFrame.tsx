import type { ReactNode } from "react";
import Image from "next/image";
import SceneNav from "@/components/SceneNav/SceneNav";
import styles from "./WhoAmIFrame.module.css";

interface WhoAmIFrameProps {
    title: string;
    // Shown under the title.
    description?: string;
    // The small line above the title.
    badge?: string;
    children?: ReactNode;
}

// Artwork: old keys laid out on dark crumpled paper (frontend/public/images/
// who-am-i-old-keys.avif, 2400x1600, optimized from a downloaded original; see
// public/images/ATTRIBUTIONS.md).
const SCENE_IMAGE = "/images/who-am-i-old-keys.avif";

// The game's scene: the keys under a dark green wash and a faint lantern glow, in the Home's
// navigation capsule and palette. Owns the <main> landmark.
export default function WhoAmIFrame({
    title,
    description,
    badge = "Five clues. One character.",
    children,
}: WhoAmIFrameProps) {
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
                <div className={styles.glow} />
                <div className={styles.shade} />
            </div>

            <SceneNav />

            <main className={styles.main}>
                <header className={styles.hero}>
                    <p className={styles.badge}>
                        <span className={styles.badgeDot} aria-hidden="true" />
                        {badge}
                    </p>
                    <h1 className={styles.title}>{title}</h1>
                    {description && <p className={styles.description}>{description}</p>}
                </header>

                {children}
            </main>
        </div>
    );
}
