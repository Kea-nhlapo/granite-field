import { render, screen, within } from "@testing-library/react";
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
    resetRoutingMocks,
} from "../../shared/api/mocks/handlers";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import {
    mockLowestCostCandidateId,
    mockRouteAssessmentId,
    mockRouteCalculationId,
} from "../../shared/api/mocks/routing-handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetRoutingMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderRouting() {
    applyTokenResponse(ownerTokens);
    const router = createMemoryRouter(appRoutes, {
        initialEntries: [`/app/routing/${mockBusinessId}`],
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

describe("routing", () => {
    it("calculates, scores, and shows every factor with a keyboard summary", async () => {
        Object.defineProperty(window, "innerWidth", {
            configurable: true,
            value: 1440,
        });
        const { router, user } = renderRouting();
        expect(
            await screen.findByRole("heading", { name: "Compare routes" }),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Calculate and score" }),
        );
        expect(
            await screen.findByRole("heading", { name: "Route comparison" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/routing/${mockBusinessId}/calculations/${mockRouteCalculationId}/assessments/${mockRouteAssessmentId}`,
        );
        expect(
            screen.getByRole("img", { name: "Candidate route map" }),
        ).toBeInTheDocument();
        expect(screen.getByText(/Fastest/)).toBeInTheDocument();
        expect(screen.getByText(/Lowest cost/)).toBeInTheDocument();
        expect(screen.getByText(/Safest/)).toBeInTheDocument();
        expect(screen.getByText(/Best connectivity/)).toBeInTheDocument();
        expect(screen.getByText(/Recommended/)).toBeInTheDocument();
        expect(
            screen.getByText(
                /Best weighted fit for the high value electronics profile/,
            ),
        ).toBeInTheDocument();
        expect(screen.getByText(/Safety exposure 18/)).toBeInTheDocument();
        expect(screen.getByText(/connectivity 64/)).toBeInTheDocument();
        expect(screen.getByText(/confidence 0.91/)).toBeInTheDocument();
        expect(window.innerWidth).toBe(1440);

        const group = screen.getByRole("radiogroup", { name: "Route options" });
        const lowest = within(group).getByRole("radio", {
            name: /R21 service road/,
        });
        lowest.focus();
        expect(lowest).toHaveFocus();
        await user.click(lowest);
        expect(
            await screen.findByText(
                /Missing ROAD_QUALITY data — not treated as safe/,
            ),
        ).toBeInTheDocument();
        expect(router.state.location.search).toContain(
            mockLowestCostCandidateId,
        );
    });

    it("changes the recommendation after cargo and weights are recalculated", async () => {
        const { user } = renderRouting();
        await screen.findByRole("heading", { name: "Compare routes" });
        await user.click(
            screen.getByRole("button", { name: "Calculate and score" }),
        );
        expect(
            await screen.findByText(
                /Best weighted fit for the high value electronics profile/,
            ),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Change cargo or weights" }),
        );
        await screen.findByRole("heading", { name: "Compare routes" });
        await user.selectOptions(
            screen.getByLabelText("Cargo profile"),
            "LOW_VALUE_DRY_GOODS",
        );
        await user.click(
            screen.getByRole("button", { name: "Calculate and score" }),
        );
        expect(
            await screen.findByText(/Lowest combined cost for dry goods/),
        ).toBeInTheDocument();
        expect(
            screen.queryByText(
                /Best weighted fit for the high value electronics profile/,
            ),
        ).not.toBeInTheDocument();
    });

    it("retries when no routes are returned", async () => {
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/routing/calculations`,
                () =>
                    HttpResponse.json(
                        {
                            calculationId: mockRouteCalculationId,
                            candidates: [],
                        },
                        { status: 201 },
                    ),
            ),
        );
        const { user } = renderRouting();
        await screen.findByRole("heading", { name: "Compare routes" });
        await user.click(
            screen.getByRole("button", { name: "Calculate and score" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "No routes were returned",
            }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Try again" }));
        expect(
            await screen.findByRole("heading", { name: "Compare routes" }),
        ).toBeInTheDocument();
    });
});
