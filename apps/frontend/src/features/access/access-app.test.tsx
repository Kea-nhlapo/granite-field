import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import { handlers, ownerTokens } from "../../shared/api/mocks/handlers";
import { SessionProvider } from "./SessionProvider";
import { applyTokenResponse, clearSession, dropMemorySession } from "./session";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderAccessApp(path: string) {
    const router = createMemoryRouter(appRoutes, {
        initialEntries: [path],
    });

    return {
        router,
        user: userEvent.setup(),
        ...render(
            <FluentAppProvider>
                <SessionProvider>
                    <RouterProvider router={router} />
                </SessionProvider>
            </FluentAppProvider>,
        ),
    };
}

async function signIn(
    user: ReturnType<typeof userEvent.setup>,
    email = "owner@example.com",
) {
    await screen.findByRole("heading", { name: "Sign in" });
    await user.type(screen.getByLabelText(/email/i), email);
    await user.type(screen.getByLabelText(/password/i), "correct-horse");
    await user.click(screen.getByRole("button", { name: "Sign in" }));
    expect(
        await screen.findByRole("heading", { name: "Workspace" }),
    ).toBeInTheDocument();
}

describe("session shell", () => {
    it("logs in through the generated auth client and lands on a URL workspace", async () => {
        const { router, user } = renderAccessApp("/login");

        await signIn(user);

        expect(router.state.location.pathname).toBe("/app");
        expect(
            screen.getByText(`Signed in as user ${ownerTokens.userId}`),
        ).toBeInTheDocument();
        expect(screen.getByText("Roles: BUSINESS_OWNER")).toBeInTheDocument();
        expect(
            screen.queryByText(/onboardingComplete/i),
        ).not.toBeInTheDocument();
    });

    it("restores the session after a reload using the refresh token", async () => {
        const first = renderAccessApp("/login");
        await signIn(first.user);
        first.unmount();
        dropMemorySession();

        const { router } = renderAccessApp("/app");

        expect(
            await screen.findByRole("heading", { name: "Workspace" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/app");
        expect(screen.getByTestId("app-shell")).toBeInTheDocument();
    });

    it("returns to sign-in when refresh fails after reload", async () => {
        const first = renderAccessApp("/login");
        await signIn(first.user);
        first.unmount();
        dropMemorySession();
        sessionStorage.setItem("trademesh.refresh-token", "expired-refresh");

        const { http, HttpResponse } = await import("msw");
        const { runtimeConfig } =
            await import("../../shared/lib/runtime-config");
        server.use(
            http.post(`${runtimeConfig.apiBaseUrl}/api/auth/refresh`, () =>
                HttpResponse.json(
                    {
                        code: "UNAUTHORIZED",
                        detail: "Authentication is required.",
                        instance: "/api/auth/refresh",
                        requestId: "00000000-0000-4000-8000-000000000099",
                        status: 401,
                        title: "Authentication is required",
                        type: "about:blank",
                    },
                    { status: 401 },
                ),
            ),
        );

        const { router } = renderAccessApp("/app");

        expect(
            await screen.findByRole("heading", { name: "Sign in" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/login");
    });

    it("logs out and leaves the private workspace", async () => {
        const { router, user } = renderAccessApp("/login");
        await signIn(user);

        await user.click(screen.getByRole("button", { name: "Sign out" }));

        expect(
            await screen.findByRole("heading", { name: "Sign in" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/login");
    });

    it("rejects a missing role with a 403 page and does not offer internal risk", async () => {
        const { router, user } = renderAccessApp("/login");
        await signIn(user);

        expect(
            screen.queryByRole("link", { name: "Internal risk" }),
        ).not.toBeInTheDocument();

        await router.navigate("/app/internal-risk");

        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/app/internal-risk");
    });

    it("lets an internal risk analyst open the restricted route", async () => {
        const { router, user } = renderAccessApp("/login");
        await signIn(user, "analyst@example.com");

        await user.click(screen.getByRole("link", { name: "Internal risk" }));

        expect(
            await screen.findByRole("heading", { name: "Internal risk" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/app/internal-risk");
    });

    it("can complete sign-in with the keyboard", async () => {
        const { user } = renderAccessApp("/login");
        await screen.findByRole("heading", { name: "Sign in" });

        const email = screen.getByLabelText(/email/i);
        const password = screen.getByLabelText(/password/i);

        email.focus();
        expect(email).toHaveFocus();
        await user.keyboard("owner@example.com");

        password.focus();
        expect(password).toHaveFocus();
        await user.keyboard("correct-horse{Enter}");

        expect(
            await screen.findByRole("heading", { name: "Workspace" }),
        ).toBeInTheDocument();
    });

    it("stays full-width on desktop instead of a 390px phone frame", async () => {
        Object.defineProperty(window, "innerWidth", {
            configurable: true,
            value: 1440,
        });
        applyTokenResponse(ownerTokens);

        renderAccessApp("/app");

        const shell = await screen.findByTestId("app-shell");
        const skip = screen.getByRole("link", { name: "Skip to main content" });
        skip.focus();
        expect(skip).toHaveFocus();
        expect(document.querySelector("[data-phone-frame]")).toBeNull();
        expect(shell).not.toHaveStyle({ width: "390px" });
        expect(window.innerWidth).toBe(1440);
    });

    it("keeps 44px touch targets on a mobile viewport", async () => {
        Object.defineProperty(window, "innerWidth", {
            configurable: true,
            value: 390,
        });
        applyTokenResponse(ownerTokens);

        renderAccessApp("/app");

        const shell = await screen.findByTestId("app-shell");
        const signOut = within(shell).getByRole("button", { name: "Sign out" });
        expect(window.innerWidth).toBe(390);
        expect(signOut).toBeInTheDocument();
        expect(document.querySelector("[data-phone-frame]")).toBeNull();
    });

    it("supports Back after opening a deep workspace URL", async () => {
        applyTokenResponse(ownerTokens);
        const { router } = renderAccessApp("/app");

        await screen.findByRole("heading", { name: "Workspace" });
        await router.navigate("/app/internal-risk");
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();

        await router.navigate(-1);

        expect(
            await screen.findByRole("heading", { name: "Workspace" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe("/app");
    });
});
