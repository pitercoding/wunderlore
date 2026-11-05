import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import SiteFooter from "./SiteFooter";

describe("SiteFooter", () => {
    it("states that Wunderlore is an unofficial fan project", () => {
        render(<SiteFooter />);

        expect(screen.getByRole("contentinfo")).toHaveTextContent(
            /unofficial fan project.*not affiliated with, endorsed or sponsored by the Tolkien Estate or the rights holders of the film adaptations/,
        );
    });

    it("links to the image credits", () => {
        render(<SiteFooter />);

        expect(
            screen.getByRole("link", { name: "Image credits" }),
        ).toHaveAttribute("href", "/images/ATTRIBUTIONS.md");
    });
});
