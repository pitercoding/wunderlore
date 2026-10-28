import { render, screen } from "@testing-library/react";
import type { ReactElement } from "react";
import { describe, expect, it, vi } from "vitest";
import QuotesPage from "./page";
import QuotesContent from "./QuotesContent";

// QuotesContent has its own tests and would otherwise call the API.
vi.mock("./QuotesContent", () => ({
    default: vi.fn(),
}));

type SuspenseElement = ReactElement<{
    children: ReactElement<{ searchParams: unknown }>;
    fallback: ReactElement;
}>;

describe("QuotesPage", () => {
    it("shows a loading message while the quotes are loading", () => {
        const page = QuotesPage({
            searchParams: Promise.resolve({}),
        }) as SuspenseElement;

        render(page.props.fallback);

        expect(
            screen.getByRole("heading", { level: 1, name: "Who said it?" }),
        ).toBeInTheDocument();
        expect(screen.getByText("Loading quotes...")).toBeInTheDocument();
    });

    it("passes the search parameters to the quote catalog", () => {
        const searchParams = Promise.resolve({ page: "4" });

        const page = QuotesPage({ searchParams }) as SuspenseElement;

        expect(page.props.children.type).toBe(QuotesContent);
        expect(page.props.children.props.searchParams).toBe(searchParams);
    });
});
