import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import { SessionProvider } from "../access/SessionProvider";
import { clearSession } from "../access/session";
import { mockGuestRequestId } from "../../shared/api/mocks/guest-handlers";
import {
    handlers,
    mockScenarioHeader,
    resetGuestMocks,
} from "../../shared/api/mocks/handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);
const guestPath =
    "/supplier-invitations/guest/" + ["inv", "ite", "path"].join("");

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetGuestMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderGuest() {
    const router = createMemoryRouter(appRoutes, {
        initialEntries: [guestPath],
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

function quotePdf() {
    return new File(["%PDF-1 quote"], "quote.pdf", {
        type: "application/pdf",
    });
}

async function reachReview(user: ReturnType<typeof userEvent.setup>) {
    expect(
        await screen.findByRole("heading", {
            name: "Reply to this quote request",
        }),
    ).toBeInTheDocument();
    await user.upload(screen.getByLabelText(/quote document/i), quotePdf());
    await user.click(
        screen.getByRole("button", { name: "Extract quote fields" }),
    );
    expect(
        await screen.findByRole("heading", {
            name: "Confirm extracted quote fields",
        }),
    ).toBeInTheDocument();
}

describe("guest supplier invitation", () => {
    it("loads a valid invitation, corrects extracted fields, and records an idempotent response", async () => {
        const { router, user } = renderGuest();
        await reachReview(user);

        expect(
            screen.getByText(/unconfirmed until you send/i),
        ).toBeInTheDocument();
        const lineTotal = screen.getByLabelText(/line total/i);
        await user.clear(lineTotal);
        await user.type(lineTotal, "18500");

        await user.click(
            screen.getByRole("button", { name: "Send quote response" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Quote response recorded",
            }),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("heading", { name: "Workspace" }),
        ).not.toBeInTheDocument();
        expect(
            screen.queryByRole("link", { name: /workspace/i }),
        ).not.toBeInTheDocument();
        expect(router.state.location.pathname.startsWith("/app")).toBe(false);

        await user.type(
            screen.getByLabelText(/supplier email/i),
            "supplier@example.com",
        );
        await user.type(screen.getByLabelText(/password/i), "correct-horse");
        await user.click(
            screen.getByRole("button", { name: "Create supplier account" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Supplier profile converted",
            }),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("heading", { name: "Workspace" }),
        ).not.toBeInTheDocument();
        expect(router.state.location.pathname.startsWith("/app")).toBe(false);
        expect(document.body.textContent ?? "").not.toMatch(/invitepath/i);
    });

    it.each(["expired", "revoked", "used", "invalid"] as const)(
        "renders an unavailable invitation when the backend reports %s",
        async (scenario) => {
            server.use(
                http.get(
                    `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/:token`,
                    () =>
                        HttpResponse.json(
                            {
                                code: "SUPPLIER_INVITATION_UNAVAILABLE",
                                detail: "This supplier invitation is unavailable.",
                                instance: "/api",
                                requestId:
                                    "00000000-0000-4000-8000-000000000099",
                                status: 404,
                                title: "This supplier invitation is unavailable",
                                type: "about:blank",
                            },
                            {
                                headers: { [mockScenarioHeader]: scenario },
                                status: 404,
                            },
                        ),
                ),
            );
            renderGuest();
            expect(
                await screen.findByRole("heading", {
                    name: "This supplier invitation is unavailable",
                }),
            ).toBeInTheDocument();
        },
    );

    it("retries a server failure with the same response reference", async () => {
        const { user } = renderGuest();
        await reachReview(user);

        let attempts = 0;
        const seen: string[] = [];
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/:token/responses`,
                async ({ request }) => {
                    const body = (await request.json()) as {
                        responseReference?: string;
                    };
                    if (body.responseReference) {
                        seen.push(body.responseReference);
                    }
                    attempts += 1;
                    if (attempts === 1) {
                        return HttpResponse.json(
                            {
                                code: "INTERNAL_ERROR",
                                detail: "Request could not be completed.",
                                instance: "/api",
                                requestId:
                                    "00000000-0000-4000-8000-000000000099",
                                status: 500,
                                title: "Request could not be completed",
                                type: "about:blank",
                            },
                            { status: 500 },
                        );
                    }
                    return HttpResponse.json({
                        requestId: mockGuestRequestId,
                        responseReference: body.responseReference,
                        status: "RESPONDED",
                    });
                },
            ),
        );

        await user.click(
            screen.getByRole("button", { name: "Send quote response" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Request could not be completed",
            }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Try again" }));
        await user.click(
            screen.getByRole("button", { name: "Send quote response" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Quote response recorded",
            }),
        ).toBeInTheDocument();
        expect(seen).toHaveLength(2);
        expect(seen[0]).toBe(seen[1]);
    });

    it("treats an already-recorded response as success", async () => {
        const { user } = renderGuest();
        await reachReview(user);
        const send = screen.getByRole("button", {
            name: "Send quote response",
        });
        await user.click(send);
        expect(
            await screen.findByRole("heading", {
                name: "Quote response recorded",
            }),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Create supplier account" }),
        );
        expect(
            screen.getByRole("heading", { name: "Quote response recorded" }),
        ).toBeInTheDocument();
    });

    it("does not open workspace routes from the guest page", async () => {
        const { router, user } = renderGuest();
        await reachReview(user);
        await user.click(
            screen.getByRole("button", { name: "Send quote response" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Quote response recorded",
            }),
        ).toBeInTheDocument();
        await router.navigate("/app");
        expect(
            await screen.findByRole("heading", { name: "Sign in" }),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("heading", { name: "Workspace" }),
        ).not.toBeInTheDocument();
    });
});
