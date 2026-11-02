import type { ComponentProps } from "react";
import styles from "./Card.module.css";

interface CardProps extends ComponentProps<"div"> {
    // Renders a landmark-friendly element; use "section" with aria-labelledby when the card has a heading.
    as?: "div" | "section" | "article";
    // Slightly lighter surface and a deeper shadow, for the main focus of a page.
    elevated?: boolean;
}

export default function Card({
    as: Component = "div",
    elevated = false,
    className,
    ...rest
}: CardProps) {
    const classes = [styles.card, elevated ? styles.elevated : "", className ?? ""]
        .filter(Boolean)
        .join(" ");

    return <Component className={classes} {...rest} />;
}
