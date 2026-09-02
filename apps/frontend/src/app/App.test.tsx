import { cleanup, render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { mocks } from "../shared/api/mocks";
import { assertAccessible, renderApp, signInAs } from "../test/render-app";
import { AppErrorBoundary } from "./AppErrorBoundary";
import { AppLoading } from "./AppLoading";

afterEach(() => {
    mocks.reset();
});

describe("application shell", () => {
    it("exposes an accessible loading state", () => {
        render(<AppLoading />);
        expect(screen.getByText("Loading application...")).toBeInTheDocument();
        expect(screen.getByRole("main")).toHaveAttribute("aria-busy", "true");
    });

    it("contains render failures inside the application boundary", () => {
        vi.spyOn(console, "error").mockImplementation(() => undefined);

        function Broken(): ReactNode {
            throw new Error("Expected test error");
        }

        render(
            <AppErrorBoundary>
                <Broken />
            </AppErrorBoundary>,
        );

        expect(screen.getByRole("alert")).toHaveTextContent(
            "Something went wrong",
        );
    });

    it("signs a business user into the workspace instead of a marketing page", async () => {
        await signInAs();
        expect(
            await screen.findByRole("heading", { name: /Mama Nkosi/i }),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("heading", {
                name: "Move stock like a bigger business",
            }),
        ).not.toBeInTheDocument();
        assertAccessible(document.body);
    });

    it("opens sourcing from the bottom navigation", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: /Source/ }));
        expect(
            screen.getByPlaceholderText("Search products or suppliers…"),
        ).toBeInTheDocument();
    });

    it("sends signed-out users to login", async () => {
        renderApp("/app");
        expect(
            await screen.findByRole("heading", { name: "Log in" }),
        ).toBeInTheDocument();
    });

    it("keeps unauthorized and forbidden copy distinct", async () => {
        renderApp("/unauthorized");
        expect(
            await screen.findByRole("heading", { name: "Sign in required" }),
        ).toBeInTheDocument();
        cleanup();
        renderApp("/forbidden");
        expect(
            screen.getByRole("heading", { name: "You cannot open this area" }),
        ).toBeInTheDocument();
    });
});
