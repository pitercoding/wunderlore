"use client";

import { useEffect, useRef, type ReactNode } from "react";

interface GameFilterListProps {
    className: string;
    // Identifies the selected game; the list is re-aligned only when it changes.
    activeKey: string;
    children: ReactNode;
}

// The horizontal scroll position that brings an item fully into a scrolling box, or the current
// one when it is already inside. All values are in the same coordinate space (viewport pixels for
// the two rectangles).
export function scrollLeftToReveal(
    scrollLeft: number,
    box: { left: number; right: number },
    item: { left: number; right: number },
    padding = 8,
): number {
    if (item.left < box.left + padding) {
        return Math.max(0, scrollLeft - (box.left + padding - item.left));
    }

    if (item.right > box.right - padding) {
        return scrollLeft + (item.right - (box.right - padding));
    }

    return scrollLeft;
}

// The games row. On a narrow screen it scrolls sideways, so the selected game can start out of
// view: when the selection changes (and on first mount) it is brought into view, and only then.
// This moves the row's own scroll position, never the page, takes no focus, and has no animation.
export default function GameFilterList({ className, activeKey, children }: GameFilterListProps) {
    const listRef = useRef<HTMLUListElement>(null);

    useEffect(() => {
        const list = listRef.current;
        const current = list?.querySelector<HTMLElement>('[aria-current="page"]');

        if (!list || !current) {
            return;
        }

        const next = scrollLeftToReveal(
            list.scrollLeft,
            list.getBoundingClientRect(),
            current.getBoundingClientRect(),
        );

        if (next !== list.scrollLeft) {
            list.scrollLeft = next;
        }
    }, [activeKey]);

    return (
        <ul ref={listRef} className={className}>
            {children}
        </ul>
    );
}
