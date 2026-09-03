import { http, HttpResponse } from "msw";

import type {
    ApiProblem,
    ChallengeResponse,
    GuestInvitationResponse,
    IssuedChallengeResponse,
    PublicSummaryResponse,
    TokenResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";

export const mockScenarioHeader = "X-Mock-Scenario";

export const ownerTokens: TokenResponse = {
    userId: "00000000-0000-4000-8000-000000000010",
    tokenType: "Bearer",
    accessToken: "mock-access-token",
    expiresInSeconds: 900,
    refreshToken: "mock-refresh-token",
    roles: ["BUSINESS_OWNER"],
};

export const analystTokens: TokenResponse = {
    ...ownerTokens,
    userId: "00000000-0000-4000-8000-000000000011",
    accessToken: "mock-analyst-access-token",
    refreshToken: "mock-analyst-refresh-token",
    roles: ["INTERNAL_RISK_ANALYST"],
};

export const handlers = [
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/public/businesses/:businessId/trust`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
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
            const scenario = scenarioOf(request);
            if (scenario === "expired-link") {
                return problem(
                    404,
                    "Supplier invitation request failed",
                    "SUPPLIER_INVITATION_UNAVAILABLE",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
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
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/auth/login`,
        async ({ request }) => {
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
            const body = (await request.json()) as {
                email?: string;
                password?: string;
            };
            if (!body.email || !body.password) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            if (body.email === "analyst@example.com") {
                return HttpResponse.json(analystTokens);
            }
            return HttpResponse.json(ownerTokens);
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/auth/refresh`,
        async ({ request }) => {
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
            const body = (await request.json()) as { refreshToken?: string };
            if (!body.refreshToken) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            if (
                body.refreshToken === analystTokens.refreshToken ||
                body.refreshToken === "mock-analyst-refresh-token-rotated"
            ) {
                return HttpResponse.json({
                    ...analystTokens,
                    accessToken: "mock-analyst-access-token-rotated",
                    refreshToken: "mock-analyst-refresh-token-rotated",
                } satisfies TokenResponse);
            }
            return HttpResponse.json({
                ...ownerTokens,
                accessToken: "mock-access-token-rotated",
                refreshToken: "mock-refresh-token-rotated",
            } satisfies TokenResponse);
        },
    ),
    http.post(`${runtimeConfig.apiBaseUrl}/api/auth/logout`, ({ request }) => {
        const scenario = scenarioOf(request);
        const error = standardError(scenario);
        if (error) {
            return error;
        }
        return new HttpResponse(null, { status: 204 });
    }),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/delivery/:shipmentId/qr`,
        ({ params }) => {
            const challenge: IssuedChallengeResponse = {
                challenge: {
                    challengeId: "00000000-0000-4000-8000-000000000041",
                    shipmentId: String(params.shipmentId),
                    type: "DELIVERY",
                    state: "PENDING",
                    expectedQuantity: 20,
                    unitOfMeasure: "CASE",
                    expiresAt: "2026-09-03T15:30:00Z",
                },
                qrPayload: "tmh1.mock-signed-one-time-token",
            };
            return HttpResponse.json(challenge);
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/delivery/:shipmentId/scan`,
        ({ params }) => {
            const challenge: ChallengeResponse = {
                challengeId: "00000000-0000-4000-8000-000000000041",
                shipmentId: String(params.shipmentId),
                type: "DELIVERY",
                state: "COMPLETED",
                expectedQuantity: 20,
                unitOfMeasure: "CASE",
                completedAt: "2026-09-03T15:10:00Z",
            };
            return HttpResponse.json(challenge);
        },
    ),
];

function scenarioOf(request: Request) {
    return request.headers.get(mockScenarioHeader) ?? "success";
}

function standardError(scenario: string) {
    if (scenario === "validation") {
        return problem(400, "Request validation failed", "INVALID_REQUEST");
    }
    if (scenario === "forbidden") {
        return problem(403, "Access denied", "ACCESS_DENIED");
    }
    if (scenario === "server-error") {
        return problem(500, "Request could not be completed", "INTERNAL_ERROR");
    }
    return undefined;
}

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
