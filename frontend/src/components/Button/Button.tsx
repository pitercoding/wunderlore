import type { ComponentProps } from "react";
import styles from "./Button.module.css";

export type ButtonVariant = "primary" | "secondary" | "ghost";
export type ButtonSize = "md" | "lg";

// - never: sized by its content.
// - mobile: fills the row on small screens, sized by its content from 576px up.
// - always: fills the row at every size.
export type ButtonStretch = "never" | "mobile" | "always";

interface ButtonProps extends ComponentProps<"button"> {
    variant?: ButtonVariant;
    size?: ButtonSize;
    stretch?: ButtonStretch;
}

// A real <button>: the caller keeps full control of onClick, disabled and aria-*.
// Defaults to type="button" so it never submits a surrounding form by accident.
export default function Button({
    variant = "primary",
    size = "md",
    stretch = "never",
    type = "button",
    className,
    ...rest
}: ButtonProps) {
    const classes = [
        styles.button,
        styles[variant],
        styles[size],
        stretch === "never" ? "" : styles[stretch],
        className ?? "",
    ]
        .filter(Boolean)
        .join(" ");

    return <button type={type} className={classes} {...rest} />;
}
