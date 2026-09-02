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
    ownerTokens,
    resetTrackingMocks,
} from "../../shared/api/mocks/handlers";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import {
    failNextLiveCalls,
    injectStaleLiveOnce,
    mockShipmentId,
} from "../../shared/api/mocks/tracking-handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetTrackingMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderTracking() {
    applyTokenResponse(ownerTokens);
    const router = createMemoryRouter(appRoutes, {
        initialEntries: [`/app/tracking/${mockBusinessId}`],
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

describe("tracking", () => {
    it("updates live position without a refresh and keeps approved vs actual paths", async () => {
        Object.defineProperty(window, "innerWidth", {
            configurable: true,
            value: 1280,
        });
        const { router, user } = renderTracking();
        expect(
            await screen.findByRole("heading", { name: "Track a shipment" }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Open tracking" }));
        expect(
            await screen.findByRole("heading", { name: "Shipment tracking" }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/tracking/${mockBusinessId}/shipments/${mockShipmentId}`,
        );
        expect(
            screen.getByRole("img", {
                name: "Approved route versus actual path",
            }),
        ).toBeInTheDocument();
        expect(screen.getByLabelText("Path summary")).toBeInTheDocument();
        expect(
            screen.getByText(/possible delay, requires review/i),
        ).toBeInTheDocument();
        expect(screen.getAllByText(/Tracker offline/i).length).toBeGreaterThan(
            0,
        );
        expect(
            screen.getAllByText(/Possible route deviation/i).length,
        ).toBeGreaterThan(0);
        expect(screen.getByText(/Possible fuel loss/i)).toBeInTheDocument();
        expect(screen.getByText(/Possible seal open/i)).toBeInTheDocument();
        expect(screen.getByText(/Possible device change/i)).toBeInTheDocument();
        expect(
            screen.getByText(/Collection handover recorded/i),
        ).toBeInTheDocument();
        expect(
            await screen.findByText(/-26.0300/, {}, { timeout: 8000 }),
        ).toBeInTheDocument();
        expect(window.innerWidth).toBe(1280);
    });

    it("reconnects without duplicating points and ignores stale live updates", async () => {
        failNextLiveCalls(1);
        injectStaleLiveOnce();
        const { user } = renderTracking();
        await screen.findByRole("heading", { name: "Track a shipment" });
        await user.click(screen.getByRole("button", { name: "Open tracking" }));
        expect(
            await screen.findByText(
                /Reconnecting to live telemetry|Live authorized position/,
            ),
        ).toBeInTheDocument();
        expect(
            await screen.findByText(/-26.0300/, {}, { timeout: 8000 }),
        ).toBeInTheDocument();
        expect(screen.queryByText(/-26.9000/)).not.toBeInTheDocument();
    });

    it("shows an empty telemetry state", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId/telemetry/live`,
                () =>
                    HttpResponse.json(
                        {
                            title: "No live position",
                            status: 404,
                            code: "TELEMETRY_NOT_FOUND",
                        },
                        { status: 404 },
                    ),
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId/telemetry`,
                () =>
                    HttpResponse.json({
                        readings: [],
                    }),
            ),
        );
        const { user } = renderTracking();
        await screen.findByRole("heading", { name: "Track a shipment" });
        await user.click(screen.getByRole("button", { name: "Open tracking" }));
        expect(
            await screen.findByText(/No telemetry readings are available yet/),
        ).toBeInTheDocument();
    });
});
