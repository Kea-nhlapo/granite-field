import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import {
    handlers,
    ownerTokens,
    supplierTokens,
} from "../../shared/api/mocks/handlers";
import { persistTheme, readStoredTheme } from "../../shared/theme/theme";
import { resetOnboardingMocks } from "../../shared/api/mocks/onboarding-handlers";
import { clearAccountProfile, saveAccountDetails } from "./account-profile";
import { applyTokenResponse, clearSession } from "./session";
import { SessionProvider } from "./SessionProvider";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    clearAccountProfile();
    resetOnboardingMocks();
    localStorage.removeItem("trademesh.theme");
    server.resetHandlers();
});
afterAll(() => server.close());

function renderApp(
    path: string,
    tokens: typeof ownerTokens | null = ownerTokens,
) {
    if (tokens) {
        applyTokenResponse(tokens);
    }
    const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
    return {
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

describe("customer home and settings", () => {
    it("shows the three home actions for a customer", async () => {
        renderApp("/app");
        expect(
            await screen.findByRole("heading", { name: "Home" }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("link", { name: /Source stock/ }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("link", { name: /Upload invoice/ }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("link", { name: /Trust and Risk/ }),
        ).toBeInTheDocument();
    });

    it("does not show source stock to a supplier", async () => {
        renderApp("/app/supplier", supplierTokens);
        expect(
            await screen.findByRole("heading", { name: "Home" }),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("link", { name: /Source stock/ }),
        ).not.toBeInTheDocument();
    });

    it("saves email and phone on settings", async () => {
        saveAccountDetails({
            firstName: "Lerato",
            lastName: "Mokoena",
            businessName: "Mokoena Stores",
            registrationNumber: "2024/123456/07",
            email: "owner@example.com",
            phoneNumber: "",
        });
        const { user } = renderApp("/app/settings");
        expect(
            await screen.findByRole("heading", { name: "Settings" }),
        ).toBeInTheDocument();
        await user.clear(screen.getByLabelText(/^email$/i));
        await user.type(
            screen.getByLabelText(/^email$/i),
            "lerato@example.com",
        );
        await user.type(screen.getByLabelText(/phone number/i), "0821234567");
        await user.click(screen.getByRole("button", { name: "Save" }));
        expect(await screen.findByText("Saved")).toBeInTheDocument();
    });

    it("loads public trust without calling internal risk", async () => {
        let riskCalls = 0;
        server.events.on("request:start", ({ request }) => {
            if (request.url.includes("/api/internal/risk/")) {
                riskCalls += 1;
            }
        });
        renderApp("/app/trust");
        expect(
            await screen.findByRole("heading", { name: "Trust and Risk" }),
        ).toBeInTheDocument();
        expect(riskCalls).toBe(0);
    });

    it("creates a customer account and lands on home tiles", async () => {
        const { user } = renderApp("/signup", null);
        await screen.findByRole("heading", { name: "Create an account" });
        await user.type(screen.getByLabelText(/first name/i), "Lerato");
        await user.type(screen.getByLabelText(/last name/i), "Mokoena");
        await user.type(
            screen.getByLabelText(/business name/i),
            "Mokoena Stores",
        );
        await user.type(
            screen.getByLabelText(/company registration/i),
            "2024/123456/07",
        );
        await user.type(
            screen.getByLabelText(/^email$/i),
            "lerato.new@example.com",
        );
        await user.type(screen.getByLabelText(/^password$/i), "correct-horse");
        await user.click(
            screen.getByRole("button", { name: "Create account" }),
        );
        expect(
            await screen.findByRole("heading", { name: "Home" }),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("link", { name: "Source stock" }),
        ).toBeInTheDocument();
    });

    it("shows a taken email as a conflict", async () => {
        const { user } = renderApp("/signup", null);
        await screen.findByRole("heading", { name: "Create an account" });
        await user.type(screen.getByLabelText(/first name/i), "Lerato");
        await user.type(screen.getByLabelText(/last name/i), "Mokoena");
        await user.type(
            screen.getByLabelText(/business name/i),
            "Mokoena Stores",
        );
        await user.type(
            screen.getByLabelText(/company registration/i),
            "2024/123456/07",
        );
        await user.type(screen.getByLabelText(/^email$/i), "taken@example.com");
        await user.type(screen.getByLabelText(/^password$/i), "correct-horse");
        await user.click(
            screen.getByRole("button", { name: "Create account" }),
        );
        expect(await screen.findByRole("alert")).toHaveTextContent(
            /already registered/i,
        );
    });

    it("keeps the chosen look after a reload", async () => {
        persistTheme("dark");
        const { user } = renderApp("/app/settings");
        expect(
            await screen.findByRole("heading", { name: "Settings" }),
        ).toBeInTheDocument();
        expect(readStoredTheme()).toBe("dark");
        await user.click(screen.getByLabelText("Light"));
        expect(readStoredTheme()).toBe("light");
        expect(document.documentElement.dataset.theme).toBe("light");
    });

    it("asks the customer to finish registration when details are missing", async () => {
        renderApp("/app");
        expect(
            await screen.findByRole("link", {
                name: "Finish business registration",
            }),
        ).toBeInTheDocument();
    });
});
