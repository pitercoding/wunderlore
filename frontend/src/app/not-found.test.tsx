import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import NotFound from "./not-found";

describe("NotFound", () => {
    it("explains the missing page and links back to the home page", () => {
        render(<NotFound />);

        expect(screen.getByRole("heading", { level: 1, name: "Page not found" })).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Back to the home page" })).toHaveAttribute("href", "/");
    });

    it("keeps the site navigation", () => {
        render(<NotFound />);

        expect(screen.getByRole("navigation", { name: "Primary" })).toBeInTheDocument();
    });
});
