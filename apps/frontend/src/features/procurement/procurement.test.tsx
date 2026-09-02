import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import { applyTokenResponse, clearSession } from "../access/session";
import { SessionProvider } from "../access/SessionProvider";
import {
    handlers,
    ownerTokens,
    resetProcurementMocks,
} from "../../shared/api/mocks/handlers";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import {
    mockOrderId,
    mockQuoteId,
    mockSupplierProfileId,
} from "../../shared/api/mocks/procurement-handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetProcurementMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function confirmedOrderBody() {
    return {
        id: mockOrderId,
        requestId: "00000000-0000-4000-8000-000000000070",
        sourceQuoteId: mockQuoteId,
        buyerBusinessId: mockBusinessId,
        status: "CONFIRMED" as const,
        money: {
            currency: "ZAR",
            subtotal: 1050,
            taxAmount: 23,
            total: 1073,
        },
        items: [],
    };
}

function mockConfirmAndGetOrder(
    onConfirm?: (body: { requestId?: string }) => Response | Promise<Response>,
) {
    server.use(
        http.post(
            `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId/confirmations`,
            async ({ request }) => {
                const body = (await request.json()) as { requestId?: string };
                if (onConfirm) {
                    return onConfirm(body);
                }
                return HttpResponse.json(confirmedOrderBody(), { status: 201 });
            },
        ),
        http.get(
            `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/orders/:orderId`,
            () => HttpResponse.json(confirmedOrderBody()),
        ),
    );
}

function procurementPath() {
    return `/app/procurement/${mockBusinessId}`;
}

function renderProcurement(path = procurementPath()) {
    applyTokenResponse(ownerTokens);
    const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
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

async function fillValidRequest(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByRole("heading", {
        name: "Create a product request",
    });
    await user.type(screen.getByLabelText(/destination/i), "Tembisa, Gauteng");
    fireEvent.change(screen.getByLabelText(/delivery window start/i), {
        target: { value: "2026-10-01T08:00" },
    });
    fireEvent.change(screen.getByLabelText(/delivery window end/i), {
        target: { value: "2026-10-02T08:00" },
    });
    await user.type(
        screen.getByLabelText(/description/i),
        "20 cases soft drinks",
    );
    await user.type(screen.getByLabelText(/quantity/i), "20");
    await user.selectOptions(screen.getByLabelText(/^unit$/i), "CASE");
    await user.click(screen.getByRole("button", { name: "Add line" }));
    const descriptions = screen.getAllByLabelText(/description/i);
    const quantities = screen.getAllByLabelText(/quantity/i);
    const units = screen.getAllByLabelText(/^unit$/i);
    const secondDescription = descriptions[1];
    const secondQuantity = quantities[1];
    const secondUnit = units[1];
    if (!secondDescription || !secondQuantity || !secondUnit) {
        throw new Error("expected a second line item");
    }
    await user.type(secondDescription, "10 bags maize meal");
    await user.type(secondQuantity, "10");
    await user.selectOptions(secondUnit, "EACH");
}

describe("procurement", () => {
    it("creates a multi-item request and shows backend quote identifiers and money", async () => {
        const { router, user } = renderProcurement();

        expect(
            await screen.findByRole("heading", {
                name: "Create a product request",
            }),
        ).toBeInTheDocument();
        await fillValidRequest(user);
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );

        expect(
            await screen.findByRole("heading", { name: "Supplier quote" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/procurement/${mockBusinessId}/quotes/${mockQuoteId}`,
        );
        expect(screen.getByText(new RegExp(mockQuoteId))).toBeInTheDocument();
        expect(
            screen.getByText(new RegExp(mockSupplierProfileId)),
        ).toBeInTheDocument();
        expect(screen.queryByText(/QUO-1001/)).not.toBeInTheDocument();
        expect(screen.getByText(/ZAR 40/)).toBeInTheDocument();
        expect(screen.getByText(/ZAR 25/)).toBeInTheDocument();
        expect(screen.getByText("Subtotal ZAR 1050")).toBeInTheDocument();
        expect(screen.getByText("Tax ZAR 23")).toBeInTheDocument();
        expect(screen.getByText("Total ZAR 1073")).toBeInTheDocument();
        expect(screen.getByText(/requested 20 CASE/)).toBeInTheDocument();
        expect(screen.getByText(/requested 10 EACH/)).toBeInTheDocument();
    });

    it("blocks empty or invalid lines before submit", async () => {
        const { user } = renderProcurement();
        await screen.findByRole("heading", {
            name: "Create a product request",
        });
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );
        expect(
            screen.getAllByText("Enter a destination.").length,
        ).toBeGreaterThan(0);
        expect(
            screen.getByRole("heading", { name: "Create a product request" }),
        ).toBeInTheDocument();
    });

    it("shows the immutable snapshot and confirms with a stable request identity", async () => {
        const seen: string[] = [];
        mockConfirmAndGetOrder((body) => {
            if (body.requestId) {
                seen.push(body.requestId);
            }
            return HttpResponse.json(confirmedOrderBody(), { status: 201 });
        });
        const { router, user } = renderProcurement();
        await fillValidRequest(user);
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );
        await screen.findByRole("heading", { name: "Supplier quote" });
        await user.click(
            screen.getByRole("button", { name: "Review confirmation" }),
        );
        expect(
            screen.getByRole("heading", { name: "Confirm this quote" }),
        ).toBeInTheDocument();
        expect(
            screen.getByText(/immutable order for quote/),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Confirm quote" }));
        expect(
            await screen.findByRole("heading", { name: "Confirmed order" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/procurement/${mockBusinessId}/orders/${mockOrderId}`,
        );
        expect(screen.queryByText(/ORD-2026-9012/)).not.toBeInTheDocument();
        expect(screen.getByText(new RegExp(mockOrderId))).toBeInTheDocument();
        expect(seen).toHaveLength(1);
        expect(seen[0]).toMatch(
            /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
        );
    });

    it("ignores a second click while confirmation is pending", async () => {
        let confirms = 0;
        let release: () => void = () => undefined;
        const gate = new Promise<void>((resolve) => {
            release = resolve;
        });
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId/confirmations`,
                async () => {
                    confirms += 1;
                    await gate;
                    return HttpResponse.json(confirmedOrderBody(), {
                        status: 201,
                    });
                },
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/orders/:orderId`,
                () => HttpResponse.json(confirmedOrderBody()),
            ),
        );
        const { user } = renderProcurement();
        await fillValidRequest(user);
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );
        await screen.findByRole("heading", { name: "Supplier quote" });
        await user.click(
            screen.getByRole("button", { name: "Review confirmation" }),
        );
        const confirm = screen.getByRole("button", { name: "Confirm quote" });
        fireEvent.click(confirm);
        fireEvent.click(confirm);
        expect(confirm).toBeDisabled();
        release();
        expect(
            await screen.findByRole("heading", { name: "Confirmed order" }),
        ).toBeInTheDocument();
        expect(confirms).toBe(1);
    });

    it("retries an ambiguous confirmation without changing the request identity", async () => {
        const seen: string[] = [];
        let attempts = 0;
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId/confirmations`,
                async ({ request }) => {
                    const body = (await request.json()) as {
                        requestId?: string;
                    };
                    if (body.requestId) {
                        seen.push(body.requestId);
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
                    return HttpResponse.json(confirmedOrderBody(), {
                        status: 201,
                    });
                },
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/orders/:orderId`,
                () => HttpResponse.json(confirmedOrderBody()),
            ),
        );
        const { user } = renderProcurement();
        await fillValidRequest(user);
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );
        await screen.findByRole("heading", { name: "Supplier quote" });
        await user.click(
            screen.getByRole("button", { name: "Review confirmation" }),
        );
        await user.click(screen.getByRole("button", { name: "Confirm quote" }));
        expect(
            await screen.findByRole("heading", {
                name: "Request could not be completed",
            }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Try again" }));
        await user.click(screen.getByRole("button", { name: "Confirm quote" }));
        expect(
            await screen.findByRole("heading", { name: "Confirmed order" }),
        ).toBeInTheDocument();
        expect(seen).toHaveLength(2);
        expect(seen[0]).toBe(seen[1]);
    });

    it("does not confirm an expired quote", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId`,
                () =>
                    HttpResponse.json({
                        id: mockQuoteId,
                        requestId: "00000000-0000-4000-8000-000000000071",
                        supplierProfileId: mockSupplierProfileId,
                        status: "EXPIRED",
                        money: {
                            currency: "ZAR",
                            subtotal: 1050,
                            taxAmount: 23,
                            total: 1073,
                        },
                        items: [],
                    }),
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/requests/:requestId`,
                () =>
                    HttpResponse.json({
                        id: "00000000-0000-4000-8000-000000000071",
                        status: "QUOTED",
                        items: [],
                    }),
            ),
        );
        renderProcurement(
            `/app/procurement/${mockBusinessId}/quotes/${mockQuoteId}`,
        );
        expect(
            await screen.findByText(/This quote has expired/),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("button", { name: "Review confirmation" }),
        ).not.toBeInTheDocument();
    });

    it("surfaces a confirmation conflict", async () => {
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId/confirmations`,
                () =>
                    HttpResponse.json(
                        {
                            code: "PROCUREMENT_STATE_CONFLICT",
                            detail: "The procurement record cannot change in its current state.",
                            instance: "/api",
                            requestId: "00000000-0000-4000-8000-000000000099",
                            status: 409,
                            title: "The procurement record cannot change in its current state",
                            type: "about:blank",
                        },
                        { status: 409 },
                    ),
            ),
        );
        const { user } = renderProcurement();
        await fillValidRequest(user);
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );
        await screen.findByRole("heading", { name: "Supplier quote" });
        await user.click(
            screen.getByRole("button", { name: "Review confirmation" }),
        );
        await user.click(screen.getByRole("button", { name: "Confirm quote" }));
        expect(
            await screen.findByRole("heading", {
                name: "The procurement record cannot change in its current state",
            }),
        ).toBeInTheDocument();
        expect(
            screen.queryByRole("button", { name: "Try again" }),
        ).not.toBeInTheDocument();
    });

    it("surfaces forbidden confirmation", async () => {
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId/confirmations`,
                () =>
                    HttpResponse.json(
                        {
                            code: "ACCESS_DENIED",
                            detail: "Access denied.",
                            instance: "/api",
                            requestId: "00000000-0000-4000-8000-000000000099",
                            status: 403,
                            title: "Access denied",
                            type: "about:blank",
                        },
                        { status: 403 },
                    ),
            ),
        );
        const { user } = renderProcurement();
        await fillValidRequest(user);
        await user.click(
            screen.getByRole("button", { name: "Submit request" }),
        );
        await screen.findByRole("heading", { name: "Supplier quote" });
        await user.click(
            screen.getByRole("button", { name: "Review confirmation" }),
        );
        await user.click(screen.getByRole("button", { name: "Confirm quote" }));
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
    });

    it("shows a missing quote as not found", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId`,
                () =>
                    HttpResponse.json(
                        {
                            code: "QUOTE_NOT_FOUND",
                            detail: "Quote was not found.",
                            instance: "/api",
                            requestId: "00000000-0000-4000-8000-000000000099",
                            status: 404,
                            title: "Quote was not found",
                            type: "about:blank",
                        },
                        { status: 404 },
                    ),
            ),
        );
        renderProcurement(
            `/app/procurement/${mockBusinessId}/quotes/${mockQuoteId}`,
        );
        expect(
            await screen.findByRole("heading", { name: "Quote was not found" }),
        ).toBeInTheDocument();
    });
});
