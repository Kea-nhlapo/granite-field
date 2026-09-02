import { render, screen, cleanup } from "@testing-library/react";
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
    adminTokens,
    analystTokens,
    handlers,
    insurerTokens,
    ownerTokens,
} from "../../shared/api/mocks/handlers";
import { mockShipmentId } from "../../shared/api/mocks/tracking-handlers";
import { problem } from "../../shared/api/mocks/mock-http";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);
let riskRequests = 0;
let insuranceRequests = 0;

beforeAll(() =>
    server.listen({
        onUnhandledRequest: "error",
    }),
);
afterEach(() => {
    clearSession();
    riskRequests = 0;
    insuranceRequests = 0;
    server.resetHandlers();
});
afterAll(() => server.close());

server.events.on("request:start", ({ request }) => {
    if (request.url.includes("/api/internal/risk/")) {
        riskRequests += 1;
    }
    if (request.url.includes("/api/insurance/")) {
        insuranceRequests += 1;
    }
});

function renderApp(path: string, tokens: typeof ownerTokens) {
    applyTokenResponse(tokens);
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

describe("restricted partner views", () => {
    it("rejects ordinary users on direct risk and insurance URLs without requesting data", async () => {
        renderApp(`/app/internal-risk/${mockShipmentId}`, ownerTokens);
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
        expect(riskRequests).toBe(0);

        cleanup();
        clearSession();
        renderApp("/app/insurance", ownerTokens);
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
        expect(insuranceRequests).toBe(0);
    });

    it("lets an internal risk analyst load permitted indicators", async () => {
        const { user } = renderApp("/app/internal-risk", analystTokens);
        expect(
            await screen.findByRole("heading", {
                name: "Review shipment risk",
            }),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Load indicators" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Internal risk indicators",
            }),
        ).toBeInTheDocument();
        expect(screen.getByText(/ROUTE_DEVIATION/)).toBeInTheDocument();
        expect(screen.getAllByText(/INVESTIGATING/).length).toBeGreaterThan(0);
        expect(
            screen.getByText(/Corridor mismatch under review/),
        ).toBeInTheDocument();
        expect(screen.queryByText(/owner@example.com/)).not.toBeInTheDocument();
    });

    it("lets an insurer load authorized case evidence without raw identity", async () => {
        const { user } = renderApp("/app/insurance", insurerTokens);
        expect(
            await screen.findByRole("heading", {
                name: "Open an insurance case",
            }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Load evidence" }));
        expect(
            await screen.findByRole("heading", {
                name: "Authorized insurance evidence",
            }),
        ).toBeInTheDocument();
        expect(
            screen.getByText(new RegExp(mockShipmentId)),
        ).toBeInTheDocument();
        expect(screen.getByText(/Handover COLLECTION/)).toBeInTheDocument();
        expect(
            screen.getByText(/Missing evidence: SEAL_PHOTO/),
        ).toBeInTheDocument();
        expect(screen.queryByText(/driverDisplayName/)).not.toBeInTheDocument();
        expect(screen.queryByText(/@example.com/)).not.toBeInTheDocument();
    });

    it("lets an administrator load the same risk indicators", async () => {
        renderApp(`/app/internal-risk/${mockShipmentId}`, adminTokens);
        expect(
            await screen.findByRole("heading", {
                name: "Internal risk indicators",
            }),
        ).toBeInTheDocument();
        expect(screen.getByText(/ROUTE_DEVIATION/)).toBeInTheDocument();
    });

    it("rejects cross-role access without issuing the other API", async () => {
        renderApp(`/app/internal-risk/${mockShipmentId}`, insurerTokens);
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
        expect(riskRequests).toBe(0);

        cleanup();
        clearSession();
        renderApp("/app/insurance", analystTokens);
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
        expect(insuranceRequests).toBe(0);
    });

    it("shows an empty risk list without flashing restricted copy", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/internal/risk/shipments/:shipmentId/indicators`,
                () => HttpResponse.json({ indicators: [] }),
            ),
        );
        renderApp(`/app/internal-risk/${mockShipmentId}`, analystTokens);
        expect(
            await screen.findByRole("heading", {
                name: "Internal risk indicators",
            }),
        ).toBeInTheDocument();
        expect(
            screen.getByText(
                "No permitted indicators are available for this shipment.",
            ),
        ).toBeInTheDocument();
        expect(screen.queryByText(/ROUTE_DEVIATION/)).not.toBeInTheDocument();
    });

    it("offers retry after a server failure", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/internal/risk/shipments/:shipmentId/indicators`,
                () =>
                    problem(
                        500,
                        "Request could not be completed",
                        "INTERNAL_ERROR",
                    ),
            ),
        );
        const { user } = renderApp(
            `/app/internal-risk/${mockShipmentId}`,
            analystTokens,
        );
        expect(
            await screen.findByRole("heading", {
                name: "Request could not be completed",
            }),
        ).toBeInTheDocument();
        server.resetHandlers();
        await user.click(screen.getByRole("button", { name: "Try again" }));
        expect(
            await screen.findByRole("heading", {
                name: "Internal risk indicators",
            }),
        ).toBeInTheDocument();
    });

    it("distinguishes 401 from 403 on the risk API", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/internal/risk/shipments/:shipmentId/indicators`,
                () =>
                    problem(401, "Authentication is required", "UNAUTHORIZED"),
            ),
        );
        renderApp(`/app/internal-risk/${mockShipmentId}`, analystTokens);
        expect(
            await screen.findByRole("heading", { name: "Sign in is required" }),
        ).toBeInTheDocument();

        cleanup();
        server.resetHandlers();
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/internal/risk/shipments/:shipmentId/indicators`,
                () => problem(403, "Access denied", "ACCESS_DENIED"),
            ),
        );
        renderApp(`/app/internal-risk/${mockShipmentId}`, analystTokens);
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
    });
});
