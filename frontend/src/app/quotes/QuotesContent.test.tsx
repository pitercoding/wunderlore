import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { redirect } from "next/navigation";
import { connection } from "next/server";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import { getQuotes, type QuotePage } from "@/lib/api/quotes";
import { suspendingRefresh } from "@/test/suspendingRefresh";
import QuotesContent from "./QuotesContent";

const refresh = vi.fn();
const push = vi.fn();
const replace = vi.fn();

vi.mock("next/navigation", () => ({
    // Like Next.js, stop rendering by throwing when redirect() is called.
    redirect: vi.fn((url: string) => {
        throw new Error(`NEXT_REDIRECT ${url}`);
    }),
    useRouter: () => ({ refresh, push, replace }),
}));

vi.mock("next/server", () => ({
    connection: vi.fn(),
}));

vi.mock("@/lib/api/quotes", () => ({
    getQuotes: vi.fn(),
}));

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";
const COOLDOWN =
    "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.";
const UNEXPECTED = "We couldn't load the quotes. Please try again.";

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
                characterName: "Arwen",
            },
            {
                _id: "q2",
                dialog: "Even the smallest person can change the course of the future.",
                movie: "m2",
                character: "galadriel-id",
                id: "q2",
                characterName: "Galadriel",
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
async function renderQuotes(
    page?: string,
    options?: Parameters<typeof render>[1],
) {
    const element = await QuotesContent({
        searchParams: Promise.resolve(page === undefined ? {} : { page }),
    });
    return render(element, options);
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
        expect(articles[0]).toHaveTextContent("Arwen");
        expect(articles[1]).toHaveTextContent("Galadriel");
        expect(catalog.queryByText(/Character ID/)).not.toBeInTheDocument();
        expect(catalog.queryByText(/arwen-id/)).not.toBeInTheDocument();
        expect(screen.getByText("45 quotes available")).toBeInTheDocument();
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute(
            "href",
            "/",
        );
    });

    it.each([
        ["null", null],
        ["absent", undefined],
        ["blank", "   "],
    ])(
        "shows a fallback without the raw id when the character name is %s",
        async (_, characterName) => {
            const base = quotePage().docs[0];
            getQuotesMock.mockResolvedValue(
                quotePage({ docs: [{ ...base, characterName }] }),
            );

            await renderQuotes();

            const article = screen.getByRole("article");
            expect(article).toHaveTextContent("Unknown character");
            expect(article).toHaveTextContent(
                "I would rather share one lifetime with you.",
            );
            expect(article).not.toHaveTextContent("arwen-id");
        },
    );

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
        ["a 503 without a retry time", new ApiError("API request failed with status 503", 503), UNAVAILABLE],
        ["a 503 with an invalid retry time", new ApiError("API request failed with status 503", 503, Number.NaN), UNAVAILABLE],
        ["a 503 with a retry time", new ApiError("API request failed with status 503", 503, 30), COOLDOWN],
        ["a timeout", new ApiTimeoutError(15_000), UNAVAILABLE],
        ["a network error", new TypeError("fetch failed"), UNAVAILABLE],
        ["an unmapped API error", new ApiError("API request failed with status 502", 502), UNEXPECTED],
        ["an invalid payload", new ApiResponseError("/api/catalog/quotes"), UNEXPECTED],
    ])("shows an error message when loading fails with %s", async (_, error, message) => {
        getQuotesMock.mockRejectedValue(error);

        await renderQuotes("2");

        expect(
            screen.getByRole("heading", { level: 1, name: "Quotes" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent(message);
        expect(screen.queryByRole("navigation")).not.toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
        expect(redirectMock).not.toHaveBeenCalled();
        expect(console.error).toHaveBeenCalledWith("Failed to load quotes", error);
        // Retrying is the player's decision, never automatic.
        expect(refresh).not.toHaveBeenCalled();
    });

    it("does not offer a retry when the quotes load", async () => {
        getQuotesMock.mockResolvedValue(quotePage());

        await renderQuotes();

        expect(
            screen.queryByRole("button", { name: "Try again" }),
        ).not.toBeInTheDocument();
    });

    it("retries the same page without navigating away from it", async () => {
        const user = userEvent.setup();
        getQuotesMock.mockRejectedValue(new TypeError("fetch failed"));
        await renderQuotes("7");

        expect(getQuotesMock).toHaveBeenCalledWith(7, 20);
        expect(refresh).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(push).not.toHaveBeenCalled();
        expect(replace).not.toHaveBeenCalled();
        expect(redirectMock).not.toHaveBeenCalled();
        expect(screen.getByRole("alert")).toHaveTextContent(UNAVAILABLE);
    });

    it("disables the retry until the server answers", async () => {
        const user = userEvent.setup();
        const server = suspendingRefresh();
        refresh.mockImplementationOnce(server.refresh);
        getQuotesMock.mockRejectedValue(new TypeError("fetch failed"));
        await renderQuotes("2", { wrapper: server.Wrapper });

        // Awaited: the update suspends, and React only resumes it in an awaited act.
        await act(() =>
            user.click(screen.getByRole("button", { name: "Try again" })),
        );

        expect(
            screen.getByRole("button", { name: "Trying again..." }),
        ).toBeDisabled();
        expect(screen.getByRole("alert")).toBeInTheDocument();

        await act(async () => server.respond());

        expect(
            await screen.findByRole("button", { name: "Try again" }),
        ).toBeEnabled();
    });
});
