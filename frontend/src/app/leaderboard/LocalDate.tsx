"use client";

import { useSyncExternalStore } from "react";

interface LocalDateProps {
    // An instant in UTC (ISO 8601).
    iso: string;
}

const noop = () => () => {};

// The interface is in English, so the dates are too, whatever language the browser is set to: one
// fixed locale (day first, 24-hour clock) for the server, the first client render and the final
// text. Only the time zone changes: the server and the first client render show UTC so they always
// match, and right after hydration the visitor's own time zone takes over.
const DATE_LOCALE = "en-GB";

function formatUtc(iso: string): string {
    return `${new Intl.DateTimeFormat(DATE_LOCALE, {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "UTC",
    }).format(new Date(iso))} UTC`;
}

function formatLocal(iso: string): string {
    return new Intl.DateTimeFormat(DATE_LOCALE, {
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
