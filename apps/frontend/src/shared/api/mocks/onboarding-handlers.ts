import { http, HttpResponse } from "msw";

import type {
    BusinessProfileResponse,
    RegisteredOnboardingResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf } from "./mock-http";

export const mockOnboardingId = "00000000-0000-4000-8000-000000000021";
export const mockBusinessId = "00000000-0000-4000-8000-000000000022";
export const mockFileId = "00000000-0000-4000-8000-000000000023";
export const mockDocumentId = "00000000-0000-4000-8000-000000000024";

const pendingOnboarding: RegisteredOnboardingResponse = {
    onboardingId: mockOnboardingId,
    legalName: "Mahlako General Trading (Pty) Ltd",
    tradingName: "Mahlako General Store",
    registeredAddress: "42 Madiba Street, Tembisa, Gauteng",
    state: "PENDING_CONFIRMATION",
    trusted: false,
    createdAt: "2026-09-02T12:00:00Z",
};

const confirmedProfile: BusinessProfileResponse = {
    businessId: mockBusinessId,
    legalName: pendingOnboarding.legalName,
    tradingName: pendingOnboarding.tradingName,
    registeredAddress: pendingOnboarding.registeredAddress,
    verificationStatus: "REGISTRY_VERIFIED",
    lifecycleStatus: "ACTIVE",
    trusted: true,
    createdAt: "2026-09-02T12:05:00Z",
};

let onboardingGets = 0;
let confirmed = false;

export function resetOnboardingMocks() {
    onboardingGets = 0;
    confirmed = false;
}

export const onboardingHandlers = [
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/onboarding/registered`,
        async ({ request }) => {
            const scenario = scenarioOf(request);
            const startError = onboardingStartError(scenario);
            if (startError) {
                return startError;
            }

            const body = (await request.json()) as {
                registrationNumber?: string;
            };
            if (!body.registrationNumber?.trim()) {
                return problem(
                    400,
                    "Use a 12-digit South African company registration number",
                    "INVALID_REGISTRATION_NUMBER",
                );
            }

            confirmed = false;
            onboardingGets = 0;
            return HttpResponse.json(
                scenario === "processing"
                    ? {
                          onboardingId: mockOnboardingId,
                          state: "PENDING_CONFIRMATION",
                          trusted: false,
                          createdAt: pendingOnboarding.createdAt,
                      }
                    : pendingOnboarding,
                { status: 201 },
            );
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/onboarding/registered/:onboardingId`,
        ({ request, params }) => {
            const scenario = scenarioOf(request);
            if (scenario === "not-found") {
                return problem(
                    404,
                    "The registered-business onboarding was not found",
                    "ONBOARDING_NOT_FOUND",
                );
            }
            if (scenario === "forbidden") {
                return problem(
                    403,
                    "Only the account that started this onboarding may continue it",
                    "ONBOARDING_ACCESS_DENIED",
                );
            }
            if (String(params.onboardingId) !== mockOnboardingId) {
                return problem(
                    404,
                    "The registered-business onboarding was not found",
                    "ONBOARDING_NOT_FOUND",
                );
            }

            onboardingGets += 1;
            if (confirmed) {
                return HttpResponse.json({
                    ...pendingOnboarding,
                    state: "CONFIRMED",
                    trusted: true,
                    businessId: mockBusinessId,
                    confirmedAt: confirmedProfile.createdAt,
                } satisfies RegisteredOnboardingResponse);
            }
            if (scenario === "processing" && onboardingGets < 2) {
                return HttpResponse.json({
                    onboardingId: mockOnboardingId,
                    state: "PENDING_CONFIRMATION",
                    trusted: false,
                    createdAt: pendingOnboarding.createdAt,
                } satisfies RegisteredOnboardingResponse);
            }
            return HttpResponse.json(pendingOnboarding);
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/onboarding/registered/:onboardingId/confirmation`,
        ({ request, params }) => {
            const scenario = scenarioOf(request);
            if (scenario === "duplicate") {
                return problem(
                    409,
                    "This company registration number is already being onboarded or has been confirmed",
                    "REGISTRATION_ALREADY_ONBOARDED",
                );
            }
            if (scenario === "forbidden") {
                return problem(
                    403,
                    "Only the account that started this onboarding may continue it",
                    "ONBOARDING_ACCESS_DENIED",
                );
            }
            if (String(params.onboardingId) !== mockOnboardingId) {
                return problem(
                    404,
                    "The registered-business onboarding was not found",
                    "ONBOARDING_NOT_FOUND",
                );
            }
            confirmed = true;
            return HttpResponse.json(confirmedProfile);
        },
    ),
];

function onboardingStartError(scenario: string) {
    if (scenario === "validation") {
        return problem(
            400,
            "Use a 12-digit South African company registration number",
            "INVALID_REGISTRATION_NUMBER",
        );
    }
    if (scenario === "not-found") {
        return problem(
            404,
            "The company registry did not return this business",
            "COMPANY_NOT_FOUND",
        );
    }
    if (scenario === "duplicate") {
        return problem(
            409,
            "This company registration number is already being onboarded or has been confirmed",
            "REGISTRATION_ALREADY_ONBOARDED",
        );
    }
    if (scenario === "forbidden") {
        return problem(
            403,
            "The caller is not allowed to perform this action",
            "ACCESS_DENIED",
        );
    }
    if (scenario === "provider-failure") {
        return problem(
            502,
            "An external provider rejected the request",
            "EXTERNAL_PROVIDER_FAILED",
        );
    }
    if (scenario === "provider-unavailable") {
        return problem(
            503,
            "An external provider is temporarily unavailable",
            "EXTERNAL_PROVIDER_UNAVAILABLE",
        );
    }
    if (scenario === "server-error") {
        return problem(500, "Request could not be completed", "INTERNAL_ERROR");
    }
    return undefined;
}
