import { http, HttpResponse } from "msw";

import type {
    ApiProblem,
    GuestInvitationResponse,
    PublicSummaryResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";

export const mockScenarioHeader = "X-Mock-Scenario";

export const handlers = [
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/public/businesses/:businessId/trust`,
        ({ params, request }) => {
            const scenario =
                request.headers.get(mockScenarioHeader) ?? "success";
            if (scenario === "validation") {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            if (scenario === "forbidden") {
                return problem(403, "Access denied", "ACCESS_DENIED");
            }
            if (scenario === "server-error") {
                return problem(
                    500,
                    "Request could not be completed",
                    "INTERNAL_ERROR",
                );
            }

            const response: PublicSummaryResponse =
                scenario === "empty"
                    ? {
                          businessId: String(params.businessId),
                          calculatedAt: "2026-09-02T12:00:00Z",
                          calculationVersion: "trust-summary/v1",
                          completedTransactionCount: 0,
                          deliverySuccessRate: 0,
                          historyBand: "NO_COMPLETED_HISTORY",
                          rating: {
                              outOf: 5,
                              ratingCount: 0,
                              status: "NO_RATINGS",
                          },
                          verifiedBadges: [],
                      }
                    : {
                          businessId: String(params.businessId),
                          calculatedAt: "2026-09-02T12:00:00Z",
                          calculationVersion: "trust-summary/v1",
                          completedTransactionCount: 97,
                          deliverySuccessRate: 98,
                          historyBand: "ESTABLISHED_COMPLETED_HISTORY",
                          rating: {
                              average: 4.8,
                              outOf: 5,
                              ratingCount: 63,
                              status: "AVAILABLE",
                          },
                          verifiedBadges: [
                              "CIPC_VERIFIED",
                              "IDENTITY_VERIFIED",
                          ],
                      };
            return HttpResponse.json(response);
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/:token`,
        ({ request }) => {
            if (request.headers.get(mockScenarioHeader) === "expired-link") {
                return problem(
                    404,
                    "Supplier invitation request failed",
                    "SUPPLIER_INVITATION_UNAVAILABLE",
                );
            }
            const response: GuestInvitationResponse = {
                buyerBusinessId: "00000000-0000-4000-8000-000000000001",
                expiresAt: "2026-09-09T12:00:00Z",
                invitationId: "00000000-0000-4000-8000-000000000002",
                purpose: "QUOTE_RESPONSE",
                requestId: "00000000-0000-4000-8000-000000000003",
                supplierProfileId: "00000000-0000-4000-8000-000000000004",
            };
            return HttpResponse.json(response);
        },
    ),
];

function problem(status: number, title: string, code: string) {
    const response: ApiProblem = {
        code,
        detail: `${title}.`,
        instance: "/api",
        requestId: "00000000-0000-4000-8000-000000000099",
        status,
        title,
        type: "about:blank",
    };
    return HttpResponse.json(response, { status });
}
