import { http, HttpResponse } from "msw";

import type { EvidencePackageResponse } from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockShipmentId } from "./tracking-handlers";

export const mockInsuranceCaseId = "00000000-0000-4000-8000-0000000000d1";

function hasInsurerAccess(request: Request) {
    const authorization = request.headers.get("Authorization") ?? "";
    return authorization.includes("mock-insurer-access-token");
}

export const insuranceHandlers = [
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/insurance/cases/:caseId/evidence`,
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
            if (!hasInsurerAccess(request)) {
                return problem(403, "Access denied", "ACCESS_DENIED");
            }
            if (String(params.caseId) !== mockInsuranceCaseId) {
                return problem(
                    404,
                    "The insurance case was not found",
                    "INSURANCE_CASE_NOT_FOUND",
                );
            }
            return HttpResponse.json({
                insuranceCase: {
                    caseId: mockInsuranceCaseId,
                    shipmentId: mockShipmentId,
                    purpose: "CLAIM_REVIEW",
                },
                shipment: {
                    shipmentId: mockShipmentId,
                    status: "DISPUTED",
                    createdAt: "2026-09-02T08:00:00Z",
                    updatedAt: "2026-09-02T11:00:00Z",
                },
                sourceDocuments: [
                    {
                        documentId: "00000000-0000-4000-8000-0000000000d2",
                        documentType: "INVOICE",
                        documentState: "ACCEPTED",
                        documentCreatedAt: "2026-09-02T08:30:00Z",
                    },
                ],
                actualRoute: {
                    points: [
                        {
                            readingId: "00000000-0000-4000-8000-000000000093",
                            recordedAt: "2026-09-02T09:40:00Z",
                        },
                    ],
                    possiblyTruncated: true,
                },
                handovers: [
                    {
                        challengeId: "00000000-0000-4000-8000-0000000000a1",
                        type: "COLLECTION",
                        state: "COMPLETED",
                        expectedLocationLabel: "Johannesburg",
                        completedAt: "2026-09-02T09:20:00Z",
                    },
                ],
                riskIndicators: [
                    {
                        indicatorId: "00000000-0000-4000-8000-0000000000c1",
                        rule: "ROUTE_DEVIATION",
                        state: "INVESTIGATING",
                        firstObservedAt: "2026-09-02T09:40:00Z",
                    },
                ],
                missingEvidence: ["SEAL_PHOTO"],
            } satisfies EvidencePackageResponse);
        },
    ),
];
