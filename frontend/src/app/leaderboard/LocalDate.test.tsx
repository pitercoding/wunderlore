import { render, screen } from "@testing-library/react";
import { renderToString } from "react-dom/server";
import { afterEach, describe, expect, it, vi } from "vitest";
import LocalDate from "./LocalDate";

afterEach(() => {
    vi.unstubAllEnvs();
});

describe("LocalDate", () => {
    it("renders on the server as a fixed UTC text, with the instant in dateTime", () => {
        const html = renderToString(<LocalDate iso="2026-03-01T23:30:00Z" />);

        expect(html).toContain('dateTime="2026-03-01T23:30:00Z"');
        expect(html).toContain("Mar 1, 2026");
        expect(html).toContain("11:30");
        expect(html).toContain("UTC");
    });

    it("shows the instant in the browser's own time zone", () => {
        const expected = new Intl.DateTimeFormat(undefined, {
            dateStyle: "medium",
            timeStyle: "short",
        }).format(new Date("2026-03-01T23:30:00Z"));

        render(<LocalDate iso="2026-03-01T23:30:00Z" />);

        const time = screen.getByText(expected);

        expect(time.tagName).toBe("TIME");
        expect(time).toHaveAttribute("datetime", "2026-03-01T23:30:00Z");
    });
});
