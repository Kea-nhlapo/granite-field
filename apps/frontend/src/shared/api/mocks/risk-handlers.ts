import { http, HttpResponse } from "msw";

import type { IndicatorListResponse } from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockShipmentId } from "./tracking-handlers";

export const mockRiskIndicatorId = "00000000-0000-4000-8000-0000000000c1";

function hasInternalRiskAccess(request: Request) {
    const authorization = request.headers.get("Authorization") ?? "";
    return (
        authorization.includes("mock-analyst-access-token") ||
        authorization.includes("mock-admin-access-token")
    );
}

export const riskHandlers = [
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/internal/risk/shipments/:shipmentId/indicators`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "unauthorized") {
                return problem(
                    401,
                    "Authentication is required",
                    "UNAUTHORIZED",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (!hasInternalRiskAccess(request)) {
                return problem(403, "Access denied", "ACCESS_DENIED");
            }
            if (scenario === "empty") {
                return HttpResponse.json({
                    indicators: [],
                } satisfies IndicatorListResponse);
            }
            if (String(params.shipmentId) !== mockShipmentId) {
                return HttpResponse.json({
                    indicators: [],
                } satisfies IndicatorListResponse);
            }
            return HttpResponse.json({
                indicators: [
                    {
                        indicatorId: mockRiskIndicatorId,
                        shipmentId: mockShipmentId,
                        rule: "ROUTE_DEVIATION",
                        ruleVersion: "risk/v1",
                        severity: "HIGH",
                        explanation:
                            "Possible deviation from the approved corridor — requires review.",
                        state: "INVESTIGATING",
                        firstObservedAt: "2026-09-02T09:40:00Z",
                        lastObservedAt: "2026-09-02T10:05:00Z",
                        evidence: [
                            {
                                type: "TELEMETRY_READING",
                                referenceId:
                                    "00000000-0000-4000-8000-000000000093",
                                observedAt: "2026-09-02T09:40:00Z",
                            },
                        ],
                        reviewHistory: [
                            {
                                transitionId:
                                    "00000000-0000-4000-8000-0000000000c2",
                                toState: "INVESTIGATING",
                                occurredAt: "2026-09-02T10:10:00Z",
                                note: "Corridor mismatch under review.",
                            },
                        ],
                    },
                ],
            } satisfies IndicatorListResponse);
        },
    ),
];
