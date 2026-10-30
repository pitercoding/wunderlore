import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import GlobalError from "./global-error";

const internalError = Object.assign(
    new Error("connect ECONNREFUSED http://internal-api:8080"),
    { digest: "digest-3233162933" },
);

// GlobalError renders <html>, which only fits directly under the document.
function renderGlobalError(retry = vi.fn()) {
    render(<GlobalError error={internalError} retry={retry} />, {
        container: document,
    });
}

afterEach(() => {
    vi.restoreAllMocks();
});

describe("GlobalError", () => {
    it("shows a friendly message", () => {
        renderGlobalError();

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

    it("renders its own html and body", () => {
        renderGlobalError();

        expect(document.documentElement).toHaveAttribute("lang", "en");
        expect(document.documentElement).toHaveStyle({ colorScheme: "light dark" });
        expect(document.body).toContainElement(screen.getByRole("main"));
        expect(document.title).toBe("Wunderlore");
    });

    it("retries only when the button is clicked", async () => {
        const user = userEvent.setup();
        const retry = vi.fn();
        renderGlobalError(retry);

        expect(retry).not.toHaveBeenCalled();

        await user.click(screen.getByRole("button", { name: "Try again" }));

        expect(retry).toHaveBeenCalledTimes(1);
    });

    it("does not expose or log technical details", () => {
        const consoleError = vi
            .spyOn(console, "error")
            .mockImplementation(() => {});

        renderGlobalError();

        const text = document.documentElement.textContent;
        expect(text).not.toContain(internalError.message);
        expect(text).not.toContain(internalError.digest);
        expect(text).not.toContain(internalError.name);
        expect(consoleError).not.toHaveBeenCalled();
    });
});
