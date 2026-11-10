"use client";

import { useRef, type KeyboardEvent } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { isActiveLink } from "./activeLink";
import styles from "./SceneNav.module.css";

interface SceneNavMenuProps {
    links: { href: string; label: string }[];
}

// The compact menu is a native <details>, so opening, Enter and Space keep working
// as the browser defines them. The only addition is Escape, which closes it and
// hands focus back to the button that opened it.
export default function SceneNavMenu({ links }: SceneNavMenuProps) {
    const details = useRef<HTMLDetailsElement>(null);
    const pathname = usePathname();

    function closeOnEscape(event: KeyboardEvent<HTMLDetailsElement>) {
        const menu = details.current;

        if (event.key !== "Escape" || !menu?.open) {
            return;
        }

        menu.open = false;
        menu.querySelector("summary")?.focus();
    }

    return (
        <details ref={details} className={styles.menu} onKeyDown={closeOnEscape}>
            <summary className={styles.menuButton}>
                Menu
                <span className={styles.chevron} aria-hidden="true" />
            </summary>
            <nav aria-label="Menu" className={styles.menuPanel}>
                {links.map((link) => (
                    <Link
                        key={link.href}
                        href={link.href}
                        className={styles.menuLink}
                        aria-current={isActiveLink(pathname, link.href) ? "page" : undefined}
                    >
                        {link.label}
                    </Link>
                ))}
            </nav>
        </details>
    );
}
