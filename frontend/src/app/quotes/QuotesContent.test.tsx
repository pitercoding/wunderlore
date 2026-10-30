import { render, screen, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { redirect } from "next/navigation";
import { connection } from "next/server";
import { ApiError } from "@/lib/api/client";
import { getQuotes, type QuotePage } from "@/lib/api/quotes";
import QuotesContent from "./QuotesContent";

vi.mock("next/navigation", () => ({
    // Like Next.js, stop rendering by throwing when redirect() is called.
    redirect: vi.fn((url: string) => {
        throw new Error(`NEXT_REDIRECT ${url}`);
    }),
}));

vi.mock("next/server", () => ({
    connection: vi.fn(),
}));

vi.mock("@/lib/api/quotes", () => ({
    getQuotes: vi.fn(),
}));

const connectionMock = vi.mocked(connection);
const getQuotesMock = vi.mocked(getQuotes);
const redirectMock = vi.mocked(redirect);

function quotePage(overrides: Partial<QuotePage> = {}): QuotePage {
    return {
        docs: [
            {
                _id: "q1",
                dialog: "I would rather share one lifetime with you.",
                movie: "m1",
                character: "arwen-id",
                id: "q1",
            },
            {
                _id: "q2",
                dialog: "Even the smallest person can change the course of the future.",
                movie: "m2",
                character: "galadriel-id",
                id: "q2",
            },
        ],
        total: 45,
        limit: 20,
        offset: 0,
        page: 1,
        pages: 3,
        ...overrides,
    };
}

// QuotesContent is an async Server Component: resolve it, then render the JSX.
async function renderQuotes(page?: string) {
    const element = await QuotesContent({
        searchParams: Promise.resolve(page === undefined ? {} : { page }),
    });
    return render(element);
}

function pagination() {
    return within(screen.getByRole("navigation", { name: "Quote pagination" }));
}

beforeEach(() => {
    connectionMock.mockResolvedValue(undefined);
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.clearAllMocks();
    vi.restoreAllMocks();
});

describe("QuotesContent", () => {
    it("waits for a request before fetching the quotes", async () => {
        let connectionReady!: () => void;
        connectionMock.mockReturnValue(
            new Promise<void>((resolve) => {
                connectionReady = resolve;
            }),
        );
        getQuotesMock.mockResolvedValue(quotePage());

        const rendered = renderQuotes();
        // Let every pending microtask and timer run while connection() is unresolved.
        await new Promise((resolve) => setTimeout(resolve));

        expect(connectionMock).toHaveBeenCalledTimes(1);
        expect(getQuotesMock).not.toHaveBeenCalled();

        connectionReady();
        await rendered;

        expect(getQuotesMock).toHaveBeenCalledTimes(1);
    });

    it.each([
        ["missing", undefined],
        ["empty", ""],
        ["not a number", "abc"],
        ["zero", "0"],
        ["negative", "-2"],
        ["fractional", "1.5"],
    ])("requests page 1 when the page parameter is %s", async (_, page) => {
        getQuotesMock.mockResolvedValue(quotePage());

        await renderQuotes(page);

        expect(getQuotesMock).toHaveBeenCalledWith(1, 20);
    });

    it("requests the page from the search parameters", async () => {
        getQuotesMock.mockResolvedValue(quotePage({ page: 2 }));

        await renderQuotes("2");

        expect(getQuotesMock).toHaveBeenCalledWith(2, 20);
    });

    it("lists the quotes with their character and the total", async () => {
        getQuotesMock.mockResolvedValue(quotePage());

        await renderQuotes();

        expect(
            screen.getByRole("heading", { level: 1, name: "Who said it?" }),
        ).toBeInTheDocument();
        const catalog = within(
            screen.getByRole("region", { name: "Quote catalog" }),
        );
        const articles = catalog.getAllByRole("article");
        expect(articles).toHaveLength(2);
        expect(articles[0]).toHaveTextContent(
            "I would rather share one lifetime with you.",
        );
        expect(articles[0]).toHaveTextContent("Character ID: arwen-id");
        expect(articles[1]).toHaveTextContent("Character ID: galadriel-id");
        expect(screen.getByText("45 quotes available")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute(
            "href",
            "/",
        );
    });

    it("shows an empty state when the page has no quotes", async () => {
        getQuotesMock.mockResolvedValue(
            quotePage({ docs: [], total: 0, page: 1, pages: 0 }),
        );

        await renderQuotes();

        expect(
            screen.getByText("No quotes found on this page."),
        ).toBeInTheDocument();
        expect(screen.queryByRole("article")).not.toBeInTheDocument();
        expect(redirectMock).not.toHaveBeenCalled();
    });

    it("disables Previous and links Next on the first page", async () => {
        getQuotesMock.mockResolvedValue(quotePage({ page: 1, pages: 3 }));

        await renderQuotes("1");

        const nav = pagination();
        expect(nav.queryByRole("link", { name: "Previous" })).not.toBeInTheDocument();
        expect(nav.getByText("Previous")).toHaveAttribute("aria-disabled", "true");
        expect(nav.getByText("Page 1 of 3")).toBeInTheDocument();
        expect(nav.getByRole("link", { name: "Next" })).toHaveAttribute(
            "href",
            "/quotes?page=2",
        );
    });

    it("links both directions on a middle page", async () => {
        getQuotesMock.mockResolvedValue(quotePage({ page: 2, pages: 3 }));

        await renderQuotes("2");

        const nav = pagination();
        expect(nav.getByRole("link", { name: "Previous" })).toHaveAttribute(
            "href",
            "/quotes?page=1",
        );
        expect(nav.getByText("Page 2 of 3")).toBeInTheDocument();
        expect(nav.getByRole("link", { name: "Next" })).toHaveAttribute(
            "href",
            "/quotes?page=3",
        );
    });

    it("disables Next on the last page", async () => {
        getQuotesMock.mockResolvedValue(quotePage({ page: 3, pages: 3 }));

        await renderQuotes("3");

        const nav = pagination();
        expect(nav.getByRole("link", { name: "Previous" })).toHaveAttribute(
            "href",
            "/quotes?page=2",
        );
        expect(nav.queryByRole("link", { name: "Next" })).not.toBeInTheDocument();
        expect(nav.getByText("Next")).toHaveAttribute("aria-disabled", "true");
    });

    it("redirects to the last page when the requested page is past the end", async () => {
        getQuotesMock.mockResolvedValue(
            quotePage({ docs: [], page: 9, pages: 3 }),
        );

        await expect(renderQuotes("9")).rejects.toThrow(
            "NEXT_REDIRECT /quotes?page=3",
        );
        expect(redirectMock).toHaveBeenCalledWith("/quotes?page=3");
    });

    it.each([
        ["an API error", new ApiError("API request failed with status 502", 502)],
        ["a network error", new TypeError("fetch failed")],
    ])("shows an error message when loading fails with %s", async (_, error) => {
        getQuotesMock.mockRejectedValue(error);

        await renderQuotes("2");

        expect(
            screen.getByRole("heading", { level: 1, name: "Quotes" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent(
            "We couldn't load the quotes. Please try again later.",
        );
        expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
        expect(redirectMock).not.toHaveBeenCalled();
        expect(console.error).toHaveBeenCalledWith("Failed to load quotes", error);
    });
});
