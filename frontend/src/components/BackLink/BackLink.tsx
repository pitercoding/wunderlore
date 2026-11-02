import Link from "next/link";
import styles from "./BackLink.module.css";

interface BackLinkProps {
    href?: string;
    children?: string;
}

// The "← Wunderlore" link shared by every inner page: left-aligned, with a 44px touch target.
export default function BackLink({
    href = "/",
    children = "← Wunderlore",
}: BackLinkProps) {
    return (
        <Link href={href} className={styles.backLink}>
            {children}
        </Link>
    );
}
