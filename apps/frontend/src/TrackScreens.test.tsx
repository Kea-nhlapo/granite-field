import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import {
    afterAll,
    afterEach,
    beforeAll,
    describe,
    expect,
    test,
    vi,
} from "vitest";

import { MotionProvider } from "./motion";
import {
    buildDeliveryHandoverLink,
    QRScreen,
    readDeliveryHandoverLink,
} from "./TrackScreens";
import { runtimeConfig } from "./shared/lib/runtime-config";

const businessId = "00000000-0000-4000-8000-000000000001";
const shipmentId = "00000000-0000-4000-8000-000000000002";
const orderId = "00000000-0000-4000-8000-000000000003";
const counterpartyId = "00000000-0000-4000-8000-000000000004";
const challengeId = "00000000-0000-4000-8000-000000000005";
const token = "tmh1.signed-one-time-token";

const server = setupServer();

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    server.resetHandlers();
    window.history.replaceState(null, "", "/");
    vi.restoreAllMocks();
});
afterAll(() => server.close());

function renderQrScreen() {
    return render(
        <MotionProvider>
            <QRScreen onBack={() => undefined} />
        </MotionProvider>,
    );
}

describe("delivery QR integration", () => {
    test("issues a real QR challenge through the Spring API contract", async () => {
        let requestBody: unknown;
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/delivery/:shipmentId/qr`,
                async ({ params, request }) => {
                    expect(params.shipmentId).toBe(shipmentId);
                    requestBody = await request.json();
                    return HttpResponse.json({
                        challenge: {
                            challengeId,
                            shipmentId,
                            state: "PENDING",
                            type: "DELIVERY",
                            expectedQuantity: 20,
                            expiresAt: "2026-09-03T15:30:00Z",
                        },
                        qrPayload: token,
                    });
                },
            ),
        );

        renderQrScreen();
        const user = userEvent.setup();
        await user.type(screen.getByLabelText("Business ID"), businessId);
        await user.type(screen.getByLabelText("Shipment ID"), shipmentId);
        await user.type(screen.getByLabelText("Delivery order ID"), orderId);
        await user.type(
            screen.getByLabelText("Receiving user ID"),
            counterpartyId,
        );
        await user.click(
            screen.getByRole("button", { name: "Issue secure QR code" }),
        );

        expect(
            await screen.findByTitle("Secure delivery QR code"),
        ).toBeInTheDocument();
        expect(requestBody).toEqual({
            businessId,
            deliveryOrderId: orderId,
            counterpartyUserId: counterpartyId,
        });
        expect(screen.getByText("Server issued")).toBeInTheDocument();
    });

    test("confirms a scanned token with location and quantity before showing success", async () => {
        const link = buildDeliveryHandoverLink(shipmentId, token, 20);
        window.history.replaceState(null, "", new URL(link).hash);

        Object.defineProperty(window.navigator, "geolocation", {
            configurable: true,
            value: {
                getCurrentPosition: vi.fn((success: PositionCallback) =>
                    success({
                        coords: {
                            latitude: -26.2041,
                            longitude: 28.0473,
                        },
                    } as GeolocationPosition),
                ),
            },
        });

        let requestBody: Record<string, unknown> | undefined;
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/delivery/:shipmentId/scan`,
                async ({ params, request }) => {
                    expect(params.shipmentId).toBe(shipmentId);
                    requestBody = (await request.json()) as Record<
                        string,
                        unknown
                    >;
                    return HttpResponse.json({
                        challengeId,
                        shipmentId,
                        state: "COMPLETED",
                        type: "DELIVERY",
                        expectedQuantity: 20,
                    });
                },
            ),
        );

        renderQrScreen();
        expect(
            screen.queryByText("Delivery verified by TradeMesh."),
        ).not.toBeInTheDocument();

        const user = userEvent.setup();
        await user.click(
            screen.getByRole("button", { name: "Confirm delivery" }),
        );

        expect(
            await screen.findByText("Delivery verified by TradeMesh."),
        ).toBeInTheDocument();
        await waitFor(() => {
            expect(requestBody).toMatchObject({
                token,
                capturedQty: 20,
                gpsLat: -26.2041,
                gpsLng: 28.0473,
            });
            expect(requestBody?.requestId).toEqual(expect.any(String));
        });
    });

    test("rejects malformed handover links before they reach the backend", () => {
        expect(
            readDeliveryHandoverLink(
                "#handoverShipmentId=not-a-uuid&handoverToken=tmh1.invalid",
            ),
        ).toBeNull();
    });
});
