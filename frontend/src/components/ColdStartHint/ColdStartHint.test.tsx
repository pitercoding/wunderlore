import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import ColdStartHint from "./ColdStartHint";

describe("ColdStartHint", () => {
    it("explains the possible wait without announcing a failure", () => {
        render(<ColdStartHint />);

        expect(screen.getByText(/waking them can take a minute or two/)).toBeInTheDocument();
        expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    });
});
