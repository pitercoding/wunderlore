import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactElement } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { connection } from "next/server";
import { ApiError, ApiResponseError, ApiTimeoutError } from "@/lib/api/client";
import {
    getQuestions,
    type WhichCharacterQuestion,
} from "@/lib/api/whichCharacter";
import { suspendingRefresh } from "@/test/suspendingRefresh";
import WhichCharacterPage from "./page";

const refresh = vi.fn();

vi.mock("next/navigation", () => ({
    useRouter: () => ({ refresh }),
    usePathname: () => "/games/which-character",
}));

vi.mock("next/server", () => ({
    connection: vi.fn(),
}));

vi.mock("@/lib/api/whichCharacter", () => ({
    getQuestions: vi.fn(),
}));

// WhichCharacterContent has its own tests; here it only needs to show what it received.
vi.mock("./WhichCharacterContent", () => ({
    default: ({ questions }: { questions: WhichCharacterQuestion[] }) => (
        <p data-testid="which-character-content">{questions.length} questions</p>
    ),
}));

const UNAVAILABLE = "Wunderlore is temporarily unavailable. Please try again.";
const COOLDOWN =
    "Wunderlore is temporarily unavailable. Please try again in about 30 seconds.";
const UNEXPECTED = "Unable to load the quiz. Please try again.";

const connectionMock = vi.mocked(connection);
const getQuestionsMock = vi.mocked(getQuestions);

type SuspenseElement = ReactElement<{
    children: ReactElement;
    fallback: ReactElement;
}>;

// jsdom cannot render async Server Components, so the loader is awaited directly.
async function renderLoadedPage(options?: Parameters<typeof render>[1]) {
    const page = WhichCharacterPage() as SuspenseElement;
    const loader = page.props.children.type as () => Promise<ReactElement>;
    return render(await loader(), options);
}

beforeEach(() => {
    connectionMock.mockResolvedValue(undefined);
    vi.spyOn(console, "error").mockImplementation(() => {});
});

afterEach(() => {
    vi.resetAllMocks();
    vi.restoreAllMocks();
});

describe("WhichCharacterPage", () => {
    it("shows a loading status while the questions are loading", () => {
        const page = WhichCharacterPage() as SuspenseElement;

        render(page.props.fallback);

        expect(screen.getByRole("status")).toHaveTextContent("Loading the questions...");
    });

    it("waits for a request before fetching the questions", async () => {
        const order: string[] = [];
        connectionMock.mockImplementation(async () => {
            order.push("connection");
        });
        getQuestionsMock.mockImplementation(async () => {
            order.push("getQuestions");
            return { questions: [] };
        });

        await renderLoadedPage();

        expect(order).toEqual(["connection", "getQuestions"]);
    });

    it("renders the quiz with the loaded questions", async () => {
        getQuestionsMock.mockResolvedValue({
            questions: Array.from({ length: 10 }, (_, i) => ({
                id: `q${i}`,
                text: "t",
                options: [{ id: "a", text: "A" }],
            })),
        });

        await renderLoadedPage();

        expect(screen.getByTestId("which-character-content")).toHaveTextContent(
            "10 questions",
        );
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });

    it("retries only when the player asks to, keeping the error visible", async () => {
        const user = userEvent.setup();
        getQuestionsMock.mockRejectedValue(new TypeError("fetch failed"));
        await renderLoadedPage();

        expect(refresh).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(refresh).toHaveBeenCalledTimes(1);
        expect(screen.getByRole("alert")).toHaveTextContent(UNAVAILABLE);
    });

    it("disables the retry until the server answers", async () => {
        const user = userEvent.setup();
        const server = suspendingRefresh();
        refresh.mockImplementation(server.refresh);
        getQuestionsMock.mockRejectedValue(new TypeError("fetch failed"));
        await renderLoadedPage({ wrapper: server.Wrapper });

        await act(() => user.click(screen.getByRole("button", { name: "Try again" })));

        expect(screen.getByRole("button", { name: "Trying again..." })).toBeDisabled();

        await act(async () => server.respond());

        expect(await screen.findByRole("button", { name: "Try again" })).toBeEnabled();
    });

    it.each([
        ["a 503 without a retry time", new ApiError("API request failed with status 503", 503), UNAVAILABLE],
        ["a 503 with a retry time", new ApiError("API request failed with status 503", 503, 30), COOLDOWN],
        ["a timeout", new ApiTimeoutError(15_000), UNAVAILABLE],
        ["a network error", new TypeError("fetch failed"), UNAVAILABLE],
        ["an unmapped API error", new ApiError("API request failed with status 502", 502), UNEXPECTED],
        ["an invalid payload", new ApiResponseError("/api/games/which-character/questions"), UNEXPECTED],
        ["a missing configuration", new Error("API_URL is not configured"), UNEXPECTED],
    ])("shows a safe error message when loading fails with %s", async (_, error, message) => {
        getQuestionsMock.mockRejectedValue(error);

        await renderLoadedPage();

        expect(
            screen.getByRole("heading", { level: 1, name: "Which Character Are You?" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent(message);
        expect(screen.getByRole("alert")).not.toHaveTextContent("API_URL");
        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute("href", "/");
        expect(screen.getByRole("button", { name: "Try again" })).toBeEnabled();
        expect(screen.queryByTestId("which-character-content")).not.toBeInTheDocument();
        expect(refresh).not.toHaveBeenCalled();
        expect(console.error).toHaveBeenCalledWith(
            "Failed to load Which Character Are You questions",
            error,
        );
    });
});
