"use client";

import { useSyncExternalStore } from "react";

interface LocalDateProps {
    // An instant in UTC (ISO 8601).
    iso: string;
}

const noop = () => () => {};

// The server and the first client render show the date in UTC with a fixed locale, so they always
// match. Right after hydration the browser's own time zone and language take over.
function formatUtc(iso: string): string {
    return `${new Intl.DateTimeFormat("en-US", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "UTC",
    }).format(new Date(iso))} UTC`;
}

function formatLocal(iso: string): string {
    return new Intl.DateTimeFormat(undefined, {
        dateStyle: "medium",
        timeStyle: "short",
    }).format(new Date(iso));
}

export default function LocalDate({ iso }: LocalDateProps) {
    const text = useSyncExternalStore(
        noop,
        () => formatLocal(iso),
        () => formatUtc(iso),
    );

    return <time dateTime={iso}>{text}</time>;
}
