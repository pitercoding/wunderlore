import type { ComponentProps } from "react";
import styles from "./PageShell.module.css";

interface PageShellProps extends ComponentProps<"main"> {
    // narrow: 640px reading column. content: 760px default. wide: 1080px grids.
    size?: "narrow" | "content" | "wide";
}

// The page's <main> landmark plus its column: max width, responsive gutters and
// vertical rhythm, so every page lines up the same way.
export default function PageShell({
    size = "content",
    className,
    ...rest
}: PageShellProps) {
    const classes = [styles.shell, styles[size], className ?? ""]
        .filter(Boolean)
        .join(" ");

    return <main className={classes} {...rest} />;
}
