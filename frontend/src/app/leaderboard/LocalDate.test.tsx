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
        expect(html).toContain("1 Mar 2026");
        expect(html).toContain("23:30");
        expect(html).toContain("UTC");
    });

    it("shows the instant in the browser's own time zone", () => {
        const expected = new Intl.DateTimeFormat("en-GB", {
            dateStyle: "medium",
            timeStyle: "short",
        }).format(new Date("2026-03-01T23:30:00Z"));

        render(<LocalDate iso="2026-03-01T23:30:00Z" />);

        const time = screen.getByText(expected);

        expect(time.tagName).toBe("TIME");
        expect(time).toHaveAttribute("datetime", "2026-03-01T23:30:00Z");
    });

    it("is written in English, day first and on a 24-hour clock, for any browser language", () => {
        const { container } = render(<LocalDate iso="2026-03-01T23:30:00Z" />);

        expect(container.querySelector("time")?.textContent).toMatch(
            /^[0-9]{1,2} [A-Z][a-z]{2} 2026, [0-9]{2}:[0-9]{2}$/,
        );
    });
});
