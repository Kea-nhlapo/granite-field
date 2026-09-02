import { http, HttpResponse } from "msw";

import type {
    CalculateRoutesRequest,
    CandidateResponse,
    CandidateScoreResponse,
    RouteAssessmentResponse,
    RouteCalculationResponse,
    ScoreRoutesRequest,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockBusinessId } from "./onboarding-handlers";

export const mockRouteCalculationId = "00000000-0000-4000-8000-000000000071";
export const mockRouteAssessmentId = "00000000-0000-4000-8000-000000000072";
export const mockFastestCandidateId = "00000000-0000-4000-8000-000000000073";
export const mockLowestCostCandidateId = "00000000-0000-4000-8000-000000000074";
export const mockSafestCandidateId = "00000000-0000-4000-8000-000000000075";

const calculations = new Map<string, RouteCalculationResponse>();
const assessments = new Map<string, RouteAssessmentResponse>();

export function resetRoutingMocks() {
    calculations.clear();
    assessments.clear();
}

const geometryA = [
    { latitude: -25.997, longitude: 28.226 },
    { latitude: -26.01, longitude: 28.24 },
    { latitude: -26.05, longitude: 28.23 },
];
const geometryB = [
    { latitude: -25.997, longitude: 28.226 },
    { latitude: -26.02, longitude: 28.21 },
    { latitude: -26.05, longitude: 28.23 },
];
const geometryC = [
    { latitude: -25.997, longitude: 28.226 },
    { latitude: -26.0, longitude: 28.25 },
    { latitude: -26.05, longitude: 28.23 },
];

function candidates(): CandidateResponse[] {
    return [
        {
            candidateId: mockFastestCandidateId,
            sequence: 1,
            label: "N12 corridor",
            geometry: geometryA,
            distanceMetres: 42000,
            durationSeconds: 2400,
            tollEstimateZar: 85,
        },
        {
            candidateId: mockLowestCostCandidateId,
            sequence: 2,
            label: "R21 service road",
            geometry: geometryB,
            distanceMetres: 51000,
            durationSeconds: 3100,
            tollEstimateZar: 0,
        },
        {
            candidateId: mockSafestCandidateId,
            sequence: 3,
            label: "M1 lit arterial",
            geometry: geometryC,
            distanceMetres: 47000,
            durationSeconds: 2700,
            tollEstimateZar: 40,
        },
    ];
}

function factor(
    name: FactorScoreResponse["factor"],
    rawValue: number | undefined,
    dataAvailable: boolean,
    unit: string,
): FactorScoreResponse {
    return {
        factor: name,
        rawValue,
        rawUnit: unit,
        normalizedValue: dataAvailable ? 0.3 : 0.85,
        weight: 0.1,
        contribution: dataAvailable ? 0.03 : 0.085,
        dataAvailable,
    };
}

type FactorScoreResponse = NonNullable<
    CandidateScoreResponse["factors"]
>[number];

function scoreCandidate(
    candidateId: string,
    label: string,
    options: NonNullable<CandidateScoreResponse["options"]>,
    reasons: string[],
    missingRoadQuality: boolean,
    confidence: number,
): CandidateScoreResponse {
    return {
        candidateId,
        label,
        totalScore: options.includes("RECOMMENDED") ? 0.22 : 0.41,
        confidence,
        options,
        reasons,
        factors: [
            factor("TIME", 2400, true, "SECONDS"),
            factor("DISTANCE", 42000, true, "METRES"),
            factor("FUEL", 12, true, "LITRES"),
            factor("TOLLS", 85, true, "ZAR"),
            factor("SAFETY_EXPOSURE", 18, true, "PERCENT"),
            factor(
                "ROAD_QUALITY",
                missingRoadQuality ? undefined : 72,
                !missingRoadQuality,
                "PERCENT",
            ),
            factor("CONNECTIVITY", 64, true, "PERCENT"),
        ],
    };
}

function assessmentFor(body: ScoreRoutesRequest): RouteAssessmentResponse {
    const timeOnly = body.weightOverrides?.TIME === 1;
    const recommended = timeOnly
        ? mockFastestCandidateId
        : body.cargoProfile === "HIGH_VALUE_ELECTRONICS"
          ? mockSafestCandidateId
          : mockLowestCostCandidateId;
    const reason =
        recommended === mockSafestCandidateId
            ? "Best weighted fit for the high value electronics profile."
            : recommended === mockFastestCandidateId
              ? "Fastest option after time-only weights."
              : "Lowest combined cost for dry goods.";
    return {
        assessmentId: mockRouteAssessmentId,
        calculationId: mockRouteCalculationId,
        requestedByBusinessId: mockBusinessId,
        cargoProfile: body.cargoProfile,
        algorithmVersion: "route-score/v1",
        scoreScale: "0 is best; 1 is worst",
        weights: {
            TIME: body.weightOverrides?.TIME ?? 0.2,
            DISTANCE: body.weightOverrides?.DISTANCE ?? 0.15,
            SAFETY_EXPOSURE: body.weightOverrides?.SAFETY_EXPOSURE ?? 0.35,
            CONNECTIVITY: body.weightOverrides?.CONNECTIVITY ?? 0.1,
        },
        recommendedCandidateId: recommended,
        options: {
            FASTEST: mockFastestCandidateId,
            LOWEST_COST: mockLowestCostCandidateId,
            SAFEST: mockSafestCandidateId,
            BEST_CONNECTIVITY: mockSafestCandidateId,
            RECOMMENDED: recommended,
        },
        candidates: [
            scoreCandidate(
                mockFastestCandidateId,
                "N12 corridor",
                recommended === mockFastestCandidateId
                    ? ["FASTEST", "RECOMMENDED"]
                    : ["FASTEST"],
                recommended === mockFastestCandidateId
                    ? [reason]
                    : ["Shortest duration."],
                false,
                0.94,
            ),
            scoreCandidate(
                mockLowestCostCandidateId,
                "R21 service road",
                recommended === mockLowestCostCandidateId
                    ? ["LOWEST_COST", "RECOMMENDED"]
                    : ["LOWEST_COST"],
                recommended === mockLowestCostCandidateId
                    ? [reason]
                    : ["Avoids tolls."],
                true,
                0.85,
            ),
            scoreCandidate(
                mockSafestCandidateId,
                "M1 lit arterial",
                recommended === mockSafestCandidateId
                    ? ["SAFEST", "BEST_CONNECTIVITY", "RECOMMENDED"]
                    : ["SAFEST", "BEST_CONNECTIVITY"],
                recommended === mockSafestCandidateId
                    ? [reason]
                    : ["Lower safety exposure."],
                false,
                0.91,
            ),
        ],
        createdAt: "2026-09-02T12:30:00Z",
    };
}

export const routingHandlers = [
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/routing/calculations/:calculationId/assessments`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "stale") {
                return problem(
                    409,
                    "The request ID has already been used with different scoring input",
                    "ROUTE_SCORE_REQUEST_CONFLICT",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (!calculations.has(String(params.calculationId))) {
                return problem(
                    404,
                    "The route calculation was not found",
                    "ROUTE_CALCULATION_NOT_FOUND",
                );
            }
            const body = (await request.json()) as ScoreRoutesRequest;
            if (!body.requestId || !body.cargoProfile) {
                return problem(
                    400,
                    "The route score request is invalid",
                    "INVALID_ROUTE_SCORE_REQUEST",
                );
            }
            if (
                body.cargoProfile !== "HIGH_VALUE_ELECTRONICS" &&
                body.cargoProfile !== "LOW_VALUE_DRY_GOODS" &&
                body.cargoProfile !== "BALANCED"
            ) {
                return problem(
                    400,
                    "The selected cargo profile is not configured",
                    "UNKNOWN_CARGO_PROFILE",
                );
            }
            const created = assessmentFor(body);
            assessments.set(mockRouteAssessmentId, created);
            return HttpResponse.json(created, { status: 201 });
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/routing/calculations`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The route calculation was not found",
                    "ROUTE_CALCULATION_NOT_FOUND",
                );
            }
            const body = (await request.json()) as CalculateRoutesRequest;
            if (
                !body.requestId ||
                body.origin?.latitude === undefined ||
                body.destination?.latitude === undefined
            ) {
                return problem(
                    400,
                    "The route points, vehicle limits, or avoidance options are invalid",
                    "INVALID_ROUTE_REQUEST",
                );
            }
            const created: RouteCalculationResponse = {
                calculationId: mockRouteCalculationId,
                requestedByBusinessId: mockBusinessId,
                requestId: body.requestId,
                recalculationOfId: body.recalculationOfId,
                origin: body.origin,
                destination: body.destination,
                waypoints: body.waypoints,
                vehicleLimits: body.vehicleLimits,
                avoidances: body.avoidances,
                providerName: "mock-router",
                providerVersion: "v1",
                fallbackUsed: false,
                candidates: scenario === "empty" ? [] : candidates(),
                createdAt: "2026-09-02T12:20:00Z",
            };
            calculations.set(mockRouteCalculationId, created);
            return HttpResponse.json(created, { status: 201 });
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/routing/calculations/:calculationId`,
        ({ params, request }) => {
            const error = standardError(scenarioOf(request));
            if (error) {
                return error;
            }
            const found = calculations.get(String(params.calculationId));
            if (!found) {
                return problem(
                    404,
                    "The route calculation was not found",
                    "ROUTE_CALCULATION_NOT_FOUND",
                );
            }
            return HttpResponse.json(found);
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/routing/assessments/:assessmentId`,
        ({ params, request }) => {
            const error = standardError(scenarioOf(request));
            if (error) {
                return error;
            }
            const found = assessments.get(String(params.assessmentId));
            if (!found) {
                return problem(
                    404,
                    "The route assessment was not found",
                    "ROUTE_ASSESSMENT_NOT_FOUND",
                );
            }
            return HttpResponse.json(found);
        },
    ),
];
