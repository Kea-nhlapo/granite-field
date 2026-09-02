import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import { routeScoringScore, routingCalculate, routingGet } from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, resetRoutingMocks } from "./handlers";
import { mockBusinessId } from "./onboarding-handlers";
import {
    mockRouteAssessmentId,
    mockRouteCalculationId,
    mockSafestCandidateId,
} from "./routing-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetRoutingMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("routing mocks", () => {
    test("calculate and score send cargo profile and weights", async () => {
        setApiAccessToken("mock-access-token");
        const calculated = await routingCalculate({
            body: {
                requestId: "00000000-0000-4000-8000-000000000080",
                origin: { latitude: -25.997, longitude: 28.226 },
                destination: { latitude: -26.05, longitude: 28.23 },
                waypoints: [],
                vehicleLimits: {
                    maximumWeightKg: 8000,
                    maximumHeightMetres: 4,
                    maximumWidthMetres: 2.5,
                    maximumLengthMetres: 12,
                },
                avoidances: [],
            },
            path: { businessId: mockBusinessId },
        });
        expect(calculated.response?.status).toBe(201);
        expect(calculated.data?.calculationId).toBe(mockRouteCalculationId);

        const loaded = await routingGet({
            path: {
                businessId: mockBusinessId,
                calculationId: mockRouteCalculationId,
            },
        });
        expect(loaded.data?.candidates?.length).toBe(3);

        const scored = await routeScoringScore({
            body: {
                requestId: "00000000-0000-4000-8000-000000000081",
                cargoProfile: "HIGH_VALUE_ELECTRONICS",
                weightOverrides: { TIME: 0.2, SAFETY_EXPOSURE: 0.35 },
            },
            path: {
                businessId: mockBusinessId,
                calculationId: mockRouteCalculationId,
            },
        });
        expect(scored.response?.status).toBe(201);
        expect(scored.data?.assessmentId).toBe(mockRouteAssessmentId);
        expect(scored.data?.cargoProfile).toBe("HIGH_VALUE_ELECTRONICS");
        expect(scored.data?.recommendedCandidateId).toBe(mockSafestCandidateId);
        expect(scored.data?.weights?.SAFETY_EXPOSURE).toBe(0.35);
    });
});
