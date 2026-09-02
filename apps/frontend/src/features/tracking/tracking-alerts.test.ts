import { describe, expect, it } from "vitest";

import { buildTimeline } from "./tracking-alerts";
import type { TrackingShipment } from "./tracking-types";

describe("tracking alerts", () => {
    it("emits cautious wording for every indicator type", () => {
        const shipment: TrackingShipment = {
            shipmentId: "ship",
            status: "DELAYED",
            transitionHistory: [
                {
                    toStatus: "DELAYED",
                    occurredAt: "2026-09-02T10:00:00Z",
                    reason: "Window missed",
                },
            ],
            currentAssignment: {
                startedAt: "2026-09-02T09:00:00Z",
                routeGeometry: [
                    { latitude: -26.0, longitude: 28.2 },
                    { latitude: -26.05, longitude: 28.23 },
                ],
            },
        };
        const items = buildTimeline(shipment, [
            {
                readingId: "r1",
                deviceId: "d1",
                recordedAt: "2026-09-02T10:05:00Z",
                latitude: -26.01,
                longitude: 28.21,
                fuelLitres: 280,
                speedKilometresPerHour: 40,
                networkStatus: "CONNECTED",
                sealOpen: false,
            },
            {
                readingId: "r2",
                deviceId: "d2",
                recordedAt: "2026-09-02T10:10:00Z",
                latitude: -26.4,
                longitude: 28.8,
                fuelLitres: 260,
                speedKilometresPerHour: 0,
                networkStatus: "OFFLINE",
                sealOpen: true,
            },
        ]);
        const kinds = items.map((item) => item.kind);
        expect(kinds).toContain("shipment");
        expect(kinds).toContain("handover");
        expect(kinds).toContain("offline");
        expect(kinds).toContain("deviation");
        expect(kinds).toContain("fuel-loss");
        expect(kinds).toContain("seal");
        expect(kinds).toContain("device-change");
        expect(
            items.every((item) => !/confirmed theft/i.test(item.summary)),
        ).toBe(true);
        expect(
            items.some((item) => /requires review/i.test(item.summary)),
        ).toBe(true);
    });
});
