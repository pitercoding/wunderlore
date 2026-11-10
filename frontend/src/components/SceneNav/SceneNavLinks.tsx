"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { isActiveLink } from "./activeLink";
import styles from "./SceneNav.module.css";

interface SceneNavLinksProps {
    links: { href: string; label: string }[];
}

// The links of the wide navigation. Client-side only to know the current path, so the page being
// viewed can say so (aria-current) and be marked.
export default function SceneNavLinks({ links }: SceneNavLinksProps) {
    const pathname = usePathname();

    return (
        <nav aria-label="Primary" className={styles.primaryNav}>
            {links.map((link) => (
                <Link
                    key={link.href}
                    href={link.href}
                    className={styles.navLink}
                    aria-current={isActiveLink(pathname, link.href) ? "page" : undefined}
                >
                    {link.label}
                </Link>
            ))}
        </nav>
    );
}
