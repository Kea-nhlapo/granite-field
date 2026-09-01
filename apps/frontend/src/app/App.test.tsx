import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { App } from "./App";
import { AppErrorBoundary } from "./AppErrorBoundary";
import { AppLoading } from "./AppLoading";

function BrokenComponent(): ReactNode {
    throw new Error("Expected test error");
}

describe("frontend foundation", () => {
    it("renders the root route", async () => {
        render(<App />);

        expect(
            await screen.findByRole("heading", {
                name: "Frontend foundation is ready",
            }),
        ).toBeInTheDocument();
    });

    it("exposes an accessible loading state", () => {
        render(<AppLoading />);

        expect(screen.getByText("Loading application...")).toBeInTheDocument();
        expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
    });

    it("contains render failures inside the application boundary", () => {
        vi.spyOn(console, "error").mockImplementation(() => undefined);

        render(
            <AppErrorBoundary>
                <BrokenComponent />
            </AppErrorBoundary>,
        );

        expect(screen.getByRole("alert")).toHaveTextContent(
            "Something went wrong",
        );
    });
});
