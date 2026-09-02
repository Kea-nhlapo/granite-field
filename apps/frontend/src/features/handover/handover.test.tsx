import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http } from "msw";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    it,
    vi,
} from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import { applyTokenResponse, clearSession } from "../access/session";
import { SessionProvider } from "../access/SessionProvider";
import {
    handlers,
    ownerTokens,
    resetHandoverMocks,
} from "../../shared/api/mocks/handlers";
import {
    mockChallengeId,
    mockDeliveryOrderId,
    mockQrPayload,
} from "../../shared/api/mocks/handover-handlers";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import { mockShipmentId } from "../../shared/api/mocks/tracking-handlers";
import { problem } from "../../shared/api/mocks/mock-http";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetHandoverMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderHandover() {
    applyTokenResponse(ownerTokens);
    const router = createMemoryRouter(appRoutes, {
        initialEntries: [`/app/handover/${mockBusinessId}`],
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

describe("handover", () => {
    it("issues a collection QR without showing the payload and confirms a receipt", async () => {
        const warn = vi.spyOn(console, "warn");
        const error = vi.spyOn(console, "error");
        const { router, user } = renderHandover();
        expect(
            await screen.findByRole("heading", {
                name: "Issue a handover challenge",
            }),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Issue challenge" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "COLLECTION challenge",
            }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/handover/${mockBusinessId}/shipments/${mockShipmentId}/challenges/${mockChallengeId}`,
        );
        expect(
            screen.getByRole("img", { name: "Handover challenge QR code" }),
        ).toBeInTheDocument();
        expect(screen.queryByText(mockQrPayload)).not.toBeInTheDocument();
        expect(warn.mock.calls.flat().join(" ")).not.toContain(mockQrPayload);
        expect(error.mock.calls.flat().join(" ")).not.toContain(mockQrPayload);
        await user.type(
            screen.getByLabelText("Fallback challenge code"),
            mockQrPayload,
        );
        await user.type(
            screen.getByLabelText("Dispute or quantity note"),
            "20 cases collected",
        );
        await user.click(
            screen.getByRole("button", { name: "Confirm handover" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Server-confirmed handover",
            }),
        ).toBeInTheDocument();
        expect(screen.getByText(/20 cases collected/)).toBeInTheDocument();
        warn.mockRestore();
        error.mockRestore();
    });

    it("issues a delivery challenge and uses the camera fallback when permission is denied", async () => {
        Object.defineProperty(navigator, "mediaDevices", {
            configurable: true,
            value: {
                getUserMedia: async () => {
                    throw new Error("denied");
                },
            },
        });
        const { user } = renderHandover();
        await screen.findByRole("heading", {
            name: "Issue a handover challenge",
        });
        await user.selectOptions(
            screen.getByLabelText("Handover type"),
            "DELIVERY",
        );
        await user.type(
            screen.getByLabelText(/Delivery order ID/),
            mockDeliveryOrderId,
        );
        await user.click(
            screen.getByRole("button", { name: "Issue challenge" }),
        );
        expect(
            await screen.findByRole("heading", { name: "DELIVERY challenge" }),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Use camera to scan" }),
        );
        expect(
            await screen.findByText(/Camera access is unavailable/),
        ).toBeInTheDocument();
        expect(
            screen.getByLabelText("Fallback challenge code"),
        ).toBeInTheDocument();
    });

    it("disables confirmation after expiry and reports replay", async () => {
        const { user } = renderHandover();
        await screen.findByRole("heading", {
            name: "Issue a handover challenge",
        });
        await user.click(
            screen.getByRole("button", { name: "Issue challenge" }),
        );
        await screen.findByRole("heading", { name: "COLLECTION challenge" });
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/handovers/confirmations`,
                () =>
                    problem(
                        409,
                        "The completed handover challenge cannot be reused",
                        "HANDOVER_CHALLENGE_REPLAYED",
                    ),
            ),
        );
        await user.type(
            screen.getByLabelText("Fallback challenge code"),
            mockQrPayload,
        );
        await user.click(
            screen.getByRole("button", { name: "Confirm handover" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "This challenge has already been used",
            }),
        ).toBeInTheDocument();
    });
});
