import type { ReactNode } from "react";
import SceneNav from "@/components/SceneNav/SceneNav";
import styles from "./WhereInMiddleEarthFrame.module.css";

interface WhereInMiddleEarthFrameProps {
    title: string;
    // Shown under the title.
    description?: string;
    // The small line above the title.
    badge?: string;
    children?: ReactNode;
}

// The game's scene: contour lines of an imagined land under a dark green wash and a faint lantern
// glow, in the Home's navigation capsule and palette. It is drawn in CSS, so it adds no image to
// download. Owns the <main> landmark.
export default function WhereInMiddleEarthFrame({
    title,
    description,
    badge = "Click the map. Find the place.",
    children,
}: WhereInMiddleEarthFrameProps) {
    return (
        <div className={styles.page}>
            <div className={styles.scene} aria-hidden="true">
                <div className={styles.contours} />
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
