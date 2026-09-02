import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { SessionProvider } from "../features/access/SessionProvider";
import { App } from "./App";
import { appRoutes } from "./app-routes";
import { AppErrorBoundary } from "./AppErrorBoundary";
import { AppLoading } from "./AppLoading";
import { FluentAppProvider } from "./FluentAppProvider";

function BrokenComponent(): ReactNode {
    throw new Error("Expected test error");
}

describe("frontend foundation", () => {
    it("renders the sign-in route", async () => {
        render(<App />);

        expect(
            await screen.findByRole("heading", {
                name: "Sign in",
            }),
        ).toBeInTheDocument();
    });

    it("exposes an accessible loading state", () => {
        render(
            <FluentAppProvider>
                <AppLoading />
            </FluentAppProvider>,
        );

        expect(screen.getByText("Loading application...")).toBeInTheDocument();
        expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
    });

    it("contains render failures inside the application boundary", () => {
        vi.spyOn(console, "error").mockImplementation(() => undefined);

        render(
            <FluentAppProvider>
                <AppErrorBoundary>
                    <BrokenComponent />
                </AppErrorBoundary>
            </FluentAppProvider>,
        );

        expect(screen.getByRole("alert")).toHaveTextContent(
            "Something went wrong",
        );
    });

    it("keeps workspace routes on the URL instead of private history", async () => {
        const router = createMemoryRouter(appRoutes, {
            initialEntries: ["/login"],
        });

        render(
            <FluentAppProvider>
                <SessionProvider>
                    <RouterProvider router={router} />
                </SessionProvider>
            </FluentAppProvider>,
        );

        expect(
            await screen.findByRole("heading", { name: "Sign in" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/login");
    });
});
