import type { ReactNode } from "react";
import Image from "next/image";
import SceneNav from "@/components/SceneNav/SceneNav";
import styles from "./LoreChallengeFrame.module.css";

interface LoreChallengeFrameProps {
    title: string;
    // Shown under the title.
    description?: string;
    children?: ReactNode;
}

// Artwork: a sunlit reading hall of an old library, with shelves receding into shadow
// (frontend/public/images/lore-challenge-archive-library.avif, 2400x1638, optimized from a
// downloaded original; its license is still unverified, see public/images/ATTRIBUTIONS.md).
const SCENE_IMAGE = "/images/lore-challenge-archive-library.avif";

// The game's scene: the library banner under a dark green wash, in the Home's navigation
// capsule and palette. Owns the <main> landmark.
export default function LoreChallengeFrame({
    title,
    description,
    children,
}: LoreChallengeFrameProps) {
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

            <SceneNav />

            <main className={styles.main}>
                <header className={styles.hero}>
                    <p className={styles.badge}>
                        <span className={styles.badgeDot} aria-hidden="true" />
                        Test your knowledge
                    </p>
                    <h1 className={styles.title}>{title}</h1>
                    {description && <p className={styles.description}>{description}</p>}
                </header>

                {children}
            </main>
        </div>
    );
}
