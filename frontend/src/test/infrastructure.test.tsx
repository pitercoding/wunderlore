// Provisional infrastructure test: it only proves that Vitest, jsdom,
// React Testing Library and jest-dom are wired together. Replace or remove
// it once real application tests are added.
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it } from "vitest";

function Counter() {
  const [count, setCount] = useState(0);

  return (
    <button type="button" onClick={() => setCount(count + 1)}>
      Clicked {count} times
    </button>
  );
}

describe("test infrastructure", () => {
  it("runs a plain assertion", () => {
    expect(1 + 1).toBe(2);
  });

  it("renders and interacts with a React component in jsdom", async () => {
    const user = userEvent.setup();
    render(<Counter />);

    const button = screen.getByRole("button", { name: "Clicked 0 times" });
    expect(button).toBeInTheDocument();

    await user.click(button);

    expect(button).toHaveTextContent("Clicked 1 times");
  });
});
