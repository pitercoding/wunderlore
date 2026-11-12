import type { ReactNode } from "react";
import Image from "next/image";
import SceneNav from "@/components/SceneNav/SceneNav";
import CharacterSymbol from "./characterSymbols";
import styles from "./WhichCharacterFrame.module.css";

interface WhichCharacterFrameProps {
    // The hero (badge, title, notebook spread). Omitted on the result, which owns the <h1>.
    title?: string;
    description?: string;
    // Brand-only navigation, for the failed-load state.
    compactNav?: boolean;
    // The notebook spread of sketches next to the title: the quiz's first page only.
    spread?: boolean;
    children?: ReactNode;
}

// A mossy hobbit-hole under a dark canopy (frontend/public/images/which-character-hobbit-hole.avif,
// 2400x1600; see frontend/public/images/ATTRIBUTIONS.md: origin and licence were not recorded).
const SCENE_IMAGE = "/images/which-character-hobbit-hole.avif";

// Three plates fanned across the page as a field-guide spread. Purely decorative,
// so they say nothing about any answer.
const SPREAD = ["GALADRIEL", "GANDALF", "FRODO"];

// The game's scene: the Enchanted Worlds forest under a dark green wash, with the
// character notebook as its one distinct motif. Owns the <main> landmark.
export default function WhichCharacterFrame({
    title,
    description,
    compactNav = false,
    spread = false,
    children,
}: WhichCharacterFrameProps) {
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
                <div className={styles.shade} />
            </div>

            <SceneNav compact={compactNav} />

            <main className={styles.main}>
                {title && (
                    <header className={spread ? `${styles.hero} ${styles.heroSpread}` : styles.hero}>
                        <div className={styles.heroText}>
                            <p className={styles.badge}>
                                <span className={styles.badgeDot} aria-hidden="true" />
                                A notebook of characters
                            </p>
                            <h1 className={styles.title}>{title}</h1>
                            {description && <p className={styles.description}>{description}</p>}
                        </div>

                        {spread && (
                            <ul className={styles.spread} aria-hidden="true">
                                {SPREAD.map((id) => (
                                    <li key={id} className={styles.sheet}>
                                        <CharacterSymbol
                                            id={id}
                                            decorative
                                            className={styles.sheetImage}
                                        />
                                    </li>
                                ))}
                            </ul>
                        )}
                    </header>
                )}

                {children}
            </main>
        </div>
    );
}
