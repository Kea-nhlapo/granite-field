import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import { shipmentGet, telemetryHistory, telemetryLive } from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, resetTrackingMocks } from "./handlers";
import { mockBusinessId } from "./onboarding-handlers";
import { mockShipmentId } from "./tracking-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetTrackingMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("tracking mocks", () => {
    test("shipment get, history, and live use generated operations", async () => {
        setApiAccessToken("mock-access-token");
        const shipment = await shipmentGet({
            path: {
                businessId: mockBusinessId,
                shipmentId: mockShipmentId,
            },
        });
        expect(shipment.response?.status).toBe(200);
        expect(shipment.data?.shipmentId).toBe(mockShipmentId);

        const history = await telemetryHistory({
            path: {
                businessId: mockBusinessId,
                shipmentId: mockShipmentId,
            },
            query: { limit: 100 },
        });
        expect(history.data?.readings?.length).toBe(2);

        const live = await telemetryLive({
            path: {
                businessId: mockBusinessId,
                shipmentId: mockShipmentId,
            },
        });
        expect(live.data?.shipmentId).toBe(mockShipmentId);
        expect(live.data?.latitude).toBeDefined();
    });
});
