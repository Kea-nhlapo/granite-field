import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import App from "./App";
import { SessionProvider } from "./features/access/SessionProvider";
import { MotionProvider } from "./motion";
import { handlers } from "./shared/api/mocks/handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

function renderApplication() {
    return render(
        <SessionProvider>
            <MotionProvider>
                <App />
            </MotionProvider>
        </SessionProvider>,
    );
}

async function openLogin() {
    const user = userEvent.setup();
    await user.click(screen.getByRole("button", { name: "Skip" }));
    await screen.findByRole("heading", { name: "Welcome Back!" });
    return user;
}

async function signIn(email: string) {
    const user = await openLogin();
    await user.type(
        screen.getByRole("textbox", { name: "Email address" }),
        email,
    );
    await user.type(screen.getByLabelText("Password"), "correct-horse");
    await user.click(screen.getByRole("button", { name: "Continue" }));
    await screen.findByText("Core Operations");
    return user;
}

describe("TradeMesh replacement app", () => {
    test("signs in through the generated Spring API client", async () => {
        renderApplication();

        const user = await signIn("owner@example.com");
        await user.click(
            screen.getByRole("button", {
                name: "Open account and preferences",
            }),
        );

        expect(
            screen.queryByText("Operations & Fraud Console"),
        ).not.toBeInTheDocument();
        expect(sessionStorage.getItem("trademesh.refresh-token")).toBe(
            "mock-refresh-token",
        );
    });

    test("shows internal tools only when the authenticated role allows them", async () => {
        renderApplication();

        const user = await signIn("analyst@example.com");
        await user.click(
            screen.getByRole("button", {
                name: "Open account and preferences",
            }),
        );

        expect(
            screen.getByText("Operations & Fraud Console"),
        ).toBeInTheDocument();
        expect(screen.getByText("Authorised")).toBeInTheDocument();
    });

    test("does not bypass authentication from placeholder actions", async () => {
        renderApplication();
        const user = await openLogin();

        await user.click(
            screen.getByRole("button", { name: "Sign in with MoMo" }),
        );
        expect(
            screen.getByText(
                /MoMo sign-in requires the hosted consent challenge/,
            ),
        ).toBeInTheDocument();
        expect(screen.queryByText("Core Operations")).not.toBeInTheDocument();

        await user.click(screen.getByRole("button", { name: "Sign Up" }));
        expect(
            screen.getByText(
                "Account registration is not available from this screen yet.",
            ),
        ).toBeInTheDocument();
        expect(screen.queryByText("Core Operations")).not.toBeInTheDocument();
    });
});
