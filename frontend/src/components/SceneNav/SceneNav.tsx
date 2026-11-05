import Link from "next/link";
import SceneNavMenu from "./SceneNavMenu";
import styles from "./SceneNav.module.css";

interface SceneNavProps {
    // Brand only: used when the page has no other destination to offer (a failed load).
    compact?: boolean;
}

const LINKS = [
    { href: "/games/who-said-it", label: "Who Said It?" },
    { href: "/games/which-character", label: "Which Character?" },
    { href: "/games/lore-challenge", label: "Lore Challenge" },
    { href: "/games/who-am-i", label: "Who Am I?" },
    { href: "/quotes", label: "Quotes" },
];

function Logo() {
    return (
        <svg className={styles.logo} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
            <circle cx="16" cy="16" r="14.5" fill="none" stroke="currentColor" strokeOpacity="0.35" />
            <path
                d="M16 6c-5.5 4-7.5 9.5-1.5 19.5C20.5 15.5 21.5 10 16 6Z"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.4"
                strokeLinejoin="round"
            />
            <path
                d="M15.2 11.5v12.5M15.2 16.5l-3-2.4M15.2 20l3.4-2.6"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.1"
                strokeLinecap="round"
            />
        </svg>
    );
}

// The floating glass capsule of the Home, for inner pages set in the same scene.
// The brand link is the way back to the Home. Expects the scene palette tokens
// (the page re-points the semantic --color-* tokens, as the Home does).
export default function SceneNav({ compact = false }: SceneNavProps) {
    return (
        <header className={styles.navWrap}>
            <div className={compact ? `${styles.capsule} ${styles.compact}` : styles.capsule}>
                <Link href="/" className={styles.brand}>
                    <Logo />
                    Wunderlore
                </Link>

                {!compact && (
                    <>
                        <nav aria-label="Primary" className={styles.primaryNav}>
                            {LINKS.map((link) => (
                                <Link key={link.href} href={link.href} className={styles.navLink}>
                                    {link.label}
                                </Link>
                            ))}
                        </nav>

                        {/* Small screens: one compact control instead of three crowded links. */}
                        <SceneNavMenu links={LINKS} />
                    </>
                )}
            </div>
        </header>
    );
}
