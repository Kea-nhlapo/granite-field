import { http, HttpResponse } from "msw";

import type { PublicSummaryResponse, TokenResponse } from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { guestHandlers } from "./guest-handlers";
import { onboardingHandlers } from "./onboarding-handlers";

export { mockScenarioHeader } from "./mock-http";
export { resetGuestMocks } from "./guest-handlers";
export { resetOnboardingMocks } from "./onboarding-handlers";

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

export const supplierTokens: TokenResponse = {
    ...ownerTokens,
    userId: "00000000-0000-4000-8000-000000000012",
    accessToken: "mock-supplier-access-token",
    refreshToken: "mock-supplier-refresh-token",
    roles: ["SUPPLIER"],
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
        `${runtimeConfig.apiBaseUrl}/api/auth/register`,
        async ({ request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            const body = (await request.json()) as {
                accountType?: string;
                email?: string;
                password?: string;
            };
            if (!body.email || !body.password || !body.accountType) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            if (body.accountType === "SUPPLIER") {
                return HttpResponse.json(supplierTokens, { status: 201 });
            }
            return HttpResponse.json(ownerTokens, { status: 201 });
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
    ...onboardingHandlers,
    ...guestHandlers,
];
