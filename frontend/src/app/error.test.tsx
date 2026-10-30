import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import ErrorPage from "./error";

const internalError = Object.assign(
    new Error("connect ECONNREFUSED http://internal-api:8080"),
    { digest: "digest-3233162933" },
);

afterEach(() => {
    vi.restoreAllMocks();
});

describe("ErrorPage", () => {
    it("shows a friendly message", () => {
        render(<ErrorPage error={internalError} retry={vi.fn()} />);

        expect(
            screen.getByRole("heading", {
                level: 1,
                name: "Something went wrong",
            }),
        ).toBeInTheDocument();
        expect(screen.getByRole("alert")).toHaveTextContent(
            "An unexpected error occurred. You can try again.",
        );
    });

    it("retries only when the button is clicked", async () => {
        const user = userEvent.setup();
        const retry = vi.fn();
        render(<ErrorPage error={internalError} retry={retry} />);

        expect(retry).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(retry).toHaveBeenCalledTimes(1);
    });

    it("links back to the home page", () => {
        render(<ErrorPage error={internalError} retry={vi.fn()} />);

        expect(screen.getByRole("link", { name: /Wunderlore/ })).toHaveAttribute(
            "href",
            "/",
        );
    });

    it("does not expose or log technical details", () => {
        const consoleError = vi
            .spyOn(console, "error")
            .mockImplementation(() => {});

        render(<ErrorPage error={internalError} retry={vi.fn()} />);

        const text = document.body.textContent;
        expect(text).not.toContain(internalError.message);
        expect(text).not.toContain(internalError.digest);
        expect(text).not.toContain(internalError.name);
        expect(consoleError).not.toHaveBeenCalled();
    });
});
