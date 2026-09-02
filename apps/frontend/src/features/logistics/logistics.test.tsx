import { render, screen } from "@testing-library/react";
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
    mockScenarioHeader,
    ownerTokens,
    resetLogisticsMocks,
} from "../../shared/api/mocks/handlers";
import {
    mockCapacitySearchId,
    mockCompatibleOfferId,
    mockCompatibleOrderId,
    mockExcludedOrderId,
    mockFailedOfferId,
    mockSuggestionId,
} from "../../shared/api/mocks/logistics-handlers";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import { mockOrderId } from "../../shared/api/mocks/procurement-handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetLogisticsMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderLogistics(path = `/app/logistics/${mockBusinessId}`) {
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

describe("logistics", () => {
    it("suggests a privacy-safe consolidation and distinguishes hard failure from trade-off", async () => {
        const { router, user } = renderLogistics();
        expect(
            await screen.findByRole("heading", {
                name: "Consolidate confirmed orders",
            }),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Suggest consolidation" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Consolidation suggestion",
            }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/logistics/${mockBusinessId}/suggestions/${mockSuggestionId}`,
        );
        expect(screen.getByText(new RegExp(mockOrderId))).toBeInTheDocument();
        expect(
            screen.getByText(new RegExp(mockCompatibleOrderId)),
        ).toBeInTheDocument();
        expect(
            screen.getByText(/The delivery windows do not overlap enough/),
        ).toBeInTheDocument();
        expect(screen.queryByText(/Mahlako/)).not.toBeInTheDocument();
        expect(screen.queryByText(/Pty/)).not.toBeInTheDocument();
        expect(
            screen.getByText(new RegExp(mockExcludedOrderId)),
        ).toBeInTheDocument();

        await user.click(
            screen.getByRole("button", { name: "Search capacity" }),
        );
        expect(
            await screen.findByRole("heading", { name: "Capacity matches" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/logistics/${mockBusinessId}/capacity-matches/${mockCapacitySearchId}`,
        );
        expect(screen.getByText(/Combined weight 80 kg/)).toBeInTheDocument();
        expect(
            screen.getByText(new RegExp(mockCompatibleOfferId)),
        ).toBeInTheDocument();
        expect(screen.getByText(/Hard failure/)).toBeInTheDocument();
        expect(screen.getByText(/Trade-off/)).toBeInTheDocument();
        expect(screen.getByText(/added distance 1800/)).toBeInTheDocument();
        expect(screen.getByText(/estimated cost ZAR 1450/)).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Reserve this offer" }),
        ).toBeInTheDocument();
        expect(
            screen.queryByText(new RegExp(`Reserve ${mockFailedOfferId}`)),
        ).not.toBeInTheDocument();
    });

    it("shows empty consolidation", async () => {
        const { user } = renderLogistics();
        await screen.findByRole("heading", {
            name: "Consolidate confirmed orders",
        });
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/aggregation/suggestions`,
                () =>
                    HttpResponse.json({
                        suggestionId: mockSuggestionId,
                        anchorOrderId: mockOrderId,
                        status: "NO_MATCH",
                        includedOrderCount: 1,
                        orders: [
                            {
                                orderId: mockOrderId,
                                role: "ANCHOR",
                                included: true,
                                windowOverlapSeconds: 0,
                                cargoOverlapRatio: 1,
                            },
                        ],
                    }),
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/aggregation/suggestions/:suggestionId`,
                () =>
                    HttpResponse.json({
                        suggestionId: mockSuggestionId,
                        anchorOrderId: mockOrderId,
                        status: "NO_MATCH",
                        includedOrderCount: 1,
                        orders: [
                            {
                                orderId: mockOrderId,
                                role: "ANCHOR",
                                included: true,
                                windowOverlapSeconds: 0,
                                cargoOverlapRatio: 1,
                            },
                        ],
                    }),
            ),
        );
        await user.click(
            screen.getByRole("button", { name: "Suggest consolidation" }),
        );
        expect(
            await screen.findByText(/Empty consolidation/),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Widen window" }),
        ).toBeInTheDocument();
    });

    it("shows no capacity match", async () => {
        const { user } = renderLogistics();
        await screen.findByRole("heading", {
            name: "Consolidate confirmed orders",
        });
        await user.click(
            screen.getByRole("button", { name: "Suggest consolidation" }),
        );
        await screen.findByRole("heading", {
            name: "Consolidation suggestion",
        });
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/logistics/capacity-matches`,
                () =>
                    HttpResponse.json(
                        {
                            searchId: mockCapacitySearchId,
                            demandGroupSuggestionId: mockSuggestionId,
                            status: "NO_MATCH",
                            requiredCapacity: {
                                weightKg: 80,
                                volumeCubicMetres: 6,
                            },
                            candidates: [],
                        },
                        { status: 201 },
                    ),
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/logistics/capacity-matches/:searchId`,
                () =>
                    HttpResponse.json({
                        searchId: mockCapacitySearchId,
                        demandGroupSuggestionId: mockSuggestionId,
                        status: "NO_MATCH",
                        requiredCapacity: {
                            weightKg: 80,
                            volumeCubicMetres: 6,
                        },
                        candidates: [],
                    }),
            ),
        );
        await user.click(
            screen.getByRole("button", { name: "Search capacity" }),
        );
        expect(
            await screen.findByText(/No capacity match/),
        ).toBeInTheDocument();
        expect(
            screen.getByRole("button", { name: "Change cargo" }),
        ).toBeInTheDocument();
    });

    it("retries a server failure", async () => {
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/aggregation/suggestions`,
                () =>
                    HttpResponse.json(
                        {
                            code: "INTERNAL_ERROR",
                            detail: "Request could not be completed.",
                            instance: "/api",
                            requestId: "00000000-0000-4000-8000-000000000099",
                            status: 500,
                            title: "Request could not be completed",
                            type: "about:blank",
                        },
                        { status: 500 },
                    ),
            ),
        );
        const { user } = renderLogistics();
        await screen.findByRole("heading", {
            name: "Consolidate confirmed orders",
        });
        await user.click(
            screen.getByRole("button", { name: "Suggest consolidation" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Request could not be completed",
            }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Try again" }));
        expect(
            await screen.findByRole("heading", {
                name: "Consolidate confirmed orders",
            }),
        ).toBeInTheDocument();
    });

    it("surfaces forbidden aggregation", async () => {
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/aggregation/suggestions`,
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
                        {
                            headers: { [mockScenarioHeader]: "forbidden" },
                            status: 403,
                        },
                    ),
            ),
        );
        const { user } = renderLogistics();
        await screen.findByRole("heading", {
            name: "Consolidate confirmed orders",
        });
        await user.click(
            screen.getByRole("button", { name: "Suggest consolidation" }),
        );
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
    });
});
