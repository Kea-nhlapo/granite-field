import { http, HttpResponse } from "msw";

import type {
    LivePositionResponse,
    ReadingHistoryResponse,
    ReadingResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockBusinessId } from "./onboarding-handlers";
import type { TrackingShipment } from "../../../features/tracking/tracking-types";

export const mockShipmentId = "00000000-0000-4000-8000-000000000091";
export const mockReadingIds = [
    "00000000-0000-4000-8000-000000000092",
    "00000000-0000-4000-8000-000000000093",
    "00000000-0000-4000-8000-000000000094",
    "00000000-0000-4000-8000-000000000095",
] as const;

const approvedRoute = [
    { latitude: -25.997, longitude: 28.226 },
    { latitude: -26.02, longitude: 28.22 },
    { latitude: -26.05, longitude: 28.23 },
];

let liveTick = 0;
let failLiveRemaining = 0;
let injectStale = false;

export function resetTrackingMocks() {
    liveTick = 0;
    failLiveRemaining = 0;
    injectStale = false;
}

export function failNextLiveCalls(count: number) {
    failLiveRemaining = count;
}

export function injectStaleLiveOnce() {
    injectStale = true;
}

function shipment(): TrackingShipment {
    return {
        shipmentId: mockShipmentId,
        requestedByBusinessId: mockBusinessId,
        status: "DELAYED",
        loadOrders: [
            {
                orderId: "00000000-0000-4000-8000-000000000096",
                destinationLabel: "City delivery",
                latitude: -26.05,
                longitude: 28.23,
            },
        ],
        currentAssignment: {
            assignmentId: "00000000-0000-4000-8000-000000000097",
            driverDisplayName: "Authorized driver",
            startedAt: "2026-09-02T09:00:00Z",
            routeGeometry: approvedRoute,
            reason: "Approved corridor",
        },
        assignmentHistory: [],
        transitionHistory: [
            {
                fromStatus: "COLLECTED",
                toStatus: "IN_TRANSIT",
                occurredAt: "2026-09-02T09:05:00Z",
                reason: "Departed origin",
            },
            {
                fromStatus: "IN_TRANSIT",
                toStatus: "DELAYED",
                occurredAt: "2026-09-02T10:00:00Z",
                reason: "Window at risk",
            },
        ],
        createdAt: "2026-09-02T08:00:00Z",
        updatedAt: "2026-09-02T10:00:00Z",
    };
}

function historyReadings(): ReadingResponse[] {
    return [
        {
            readingId: mockReadingIds[0],
            deviceId: "00000000-0000-4000-8000-000000000098",
            recordedAt: "2026-09-02T09:10:00Z",
            latitude: -25.997,
            longitude: 28.226,
            speedKilometresPerHour: 40,
            fuelLitres: 280,
            sealOpen: false,
            networkStatus: "CONNECTED",
        },
        {
            readingId: mockReadingIds[1],
            deviceId: "00000000-0000-4000-8000-000000000099",
            recordedAt: "2026-09-02T09:40:00Z",
            latitude: -26.4,
            longitude: 28.8,
            speedKilometresPerHour: 0,
            fuelLitres: 268,
            sealOpen: true,
            networkStatus: "OFFLINE",
        },
    ];
}

function livePosition(tick: number): LivePositionResponse {
    if (tick <= 0) {
        return {
            shipmentId: mockShipmentId,
            deviceId: "00000000-0000-4000-8000-000000000099",
            readingId: mockReadingIds[1],
            recordedAt: "2026-09-02T09:40:00Z",
            latitude: -26.4,
            longitude: 28.8,
            speedKilometresPerHour: 0,
            networkStatus: "OFFLINE",
        };
    }
    return {
        shipmentId: mockShipmentId,
        deviceId: "00000000-0000-4000-8000-000000000099",
        readingId: mockReadingIds[2],
        recordedAt: "2026-09-02T10:05:00Z",
        latitude: -26.03,
        longitude: 28.225,
        speedKilometresPerHour: 35,
        networkStatus: "CONNECTED",
    };
}

export const trackingHandlers = [
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId/telemetry/live`,
        ({ params, request }) => {
            const error = standardError(scenarioOf(request));
            if (error) {
                return error;
            }
            if (String(params.shipmentId) !== mockShipmentId) {
                return problem(
                    404,
                    "The shipment was not found",
                    "SHIPMENT_NOT_FOUND",
                );
            }
            if (failLiveRemaining > 0) {
                failLiveRemaining -= 1;
                return problem(
                    503,
                    "Live telemetry is temporarily unavailable",
                    "SERVICE_UNAVAILABLE",
                );
            }
            if (injectStale) {
                injectStale = false;
                return HttpResponse.json({
                    ...livePosition(1),
                    readingId: mockReadingIds[3],
                    recordedAt: "2026-09-02T08:00:00Z",
                    latitude: -26.9,
                    longitude: 28.9,
                } satisfies LivePositionResponse);
            }
            const tick = liveTick;
            liveTick += 1;
            return HttpResponse.json(livePosition(tick));
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId/telemetry`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (scenario === "empty") {
                const empty: ReadingHistoryResponse = {
                    readings: [],
                    units: { coordinates: "WGS84 decimal degrees" },
                };
                return HttpResponse.json(empty);
            }
            if (String(params.shipmentId) !== mockShipmentId) {
                return problem(
                    404,
                    "The shipment was not found",
                    "SHIPMENT_NOT_FOUND",
                );
            }
            return HttpResponse.json({
                readings: historyReadings(),
                units: {
                    coordinates: "WGS84 decimal degrees",
                    speed: "kilometres per hour",
                },
            } satisfies ReadingHistoryResponse);
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId`,
        ({ params, request }) => {
            const error = standardError(scenarioOf(request));
            if (error) {
                return error;
            }
            if (String(params.shipmentId) !== mockShipmentId) {
                return problem(
                    404,
                    "The shipment was not found",
                    "SHIPMENT_NOT_FOUND",
                );
            }
            return HttpResponse.json(shipment());
        },
    ),
];
