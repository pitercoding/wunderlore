import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createRound } from "@/lib/api/whoSaidIt";
import { createWhoSaidItRound } from "./actions";
import WhoSaidItPage from "./page";

vi.mock("next/navigation", () => ({
    useRouter: () => ({ refresh: vi.fn() }),
    usePathname: () => "/games/who-said-it",
}));

vi.mock("@/lib/api/whoSaidIt", () => ({
    createRound: vi.fn(),
}));

vi.mock("./actions", () => ({
    createWhoSaidItRound: vi.fn(),
    submitWhoSaidItAnswer: vi.fn(),
}));

afterEach(() => {
    vi.resetAllMocks();
});

describe("WhoSaidItPage", () => {
    it("renders the introduction and a Play button without creating a round", () => {
        render(<WhoSaidItPage />);

        expect(
            screen.getByRole("heading", { level: 1, name: "Who Said It?" }),
        ).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Play" })).toBeEnabled();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
        expect(screen.queryByRole("status")).not.toBeInTheDocument();
        expect(createWhoSaidItRound).not.toHaveBeenCalled();
        expect(createRound).not.toHaveBeenCalled();
    });

    it("renders synchronously, with no async loader to fetch while the page renders", () => {
        const page = WhoSaidItPage();

        // An element, not a promise: nothing here waits for, or can start, a request.
        expect(page).not.toBeInstanceOf(Promise);
        expect(typeof page.type).toBe("function");
    });
});
