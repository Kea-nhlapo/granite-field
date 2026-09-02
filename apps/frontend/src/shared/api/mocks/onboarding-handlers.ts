import { http, HttpResponse } from "msw";

import type {
    BusinessProfileResponse,
    DocumentResponse,
    FileMetadataResponse,
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
let documentGets = 0;
let confirmed = false;

export function resetOnboardingMocks() {
    onboardingGets = 0;
    documentGets = 0;
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
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/files`,
        async ({ request, params }) => {
            const scenario = scenarioOf(request);
            if (scenario === "validation") {
                return problem(
                    400,
                    "The uploaded file is not supported",
                    "FILE_UNSUPPORTED",
                );
            }
            if (scenario === "forbidden") {
                return problem(403, "Access denied", "ACCESS_DENIED");
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The business was not found",
                    "BUSINESS_NOT_FOUND",
                );
            }

            const form = await request.formData();
            const file = form.get("file");
            const uploaded = uploadedFile(file);
            if (!uploaded) {
                return problem(400, "The uploaded file is empty", "FILE_EMPTY");
            }

            const metadata: FileMetadataResponse = {
                fileId: mockFileId,
                businessId: mockBusinessId,
                category: "COMPANY_DOCUMENT",
                originalFilename: uploaded.name,
                contentType: uploaded.type || "application/pdf",
                sizeBytes: uploaded.size,
                scanStatus: "CLEAN",
                storageStatus: "AVAILABLE",
                createdAt: "2026-09-02T12:06:00Z",
            };
            return HttpResponse.json(metadata, { status: 201 });
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents`,
        async ({ request, params }) => {
            const scenario = scenarioOf(request);
            if (scenario === "server-error") {
                return problem(
                    500,
                    "Request could not be completed",
                    "INTERNAL_ERROR",
                );
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The business was not found",
                    "BUSINESS_NOT_FOUND",
                );
            }
            const body = (await request.json()) as {
                storedFileId?: string;
                requestId?: string;
                type?: string;
            };
            if (
                !body.storedFileId ||
                !body.requestId ||
                body.type !== "COMPANY_DOCUMENT"
            ) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            documentGets = 0;
            return HttpResponse.json(queuedDocument(), { status: 202 });
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/:documentId`,
        ({ params }) => {
            if (
                String(params.businessId) !== mockBusinessId ||
                String(params.documentId) !== mockDocumentId
            ) {
                return problem(
                    404,
                    "The document was not found",
                    "DOCUMENT_NOT_FOUND",
                );
            }
            documentGets += 1;
            if (documentGets < 2) {
                return HttpResponse.json({
                    ...queuedDocument(),
                    state: "PROCESSING",
                } satisfies DocumentResponse);
            }
            return HttpResponse.json(parsedDocument());
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/:documentId/confirmations`,
        async ({ request, params }) => {
            if (
                String(params.businessId) !== mockBusinessId ||
                String(params.documentId) !== mockDocumentId
            ) {
                return problem(
                    404,
                    "The document was not found",
                    "DOCUMENT_NOT_FOUND",
                );
            }
            const body = (await request.json()) as {
                requestId?: string;
                fields?: Array<{ path?: string; value?: string }>;
            };
            if (!body.requestId || !body.fields?.length) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            return HttpResponse.json({
                ...parsedDocument(),
                state: "CONFIRMED",
            } satisfies DocumentResponse);
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

function uploadedFile(value: FormDataEntryValue | null): {
    name: string;
    type: string;
    size: number;
} | null {
    if (value == null || typeof value === "string") {
        return null;
    }
    const size = "size" in value ? Number(value.size) : 0;
    if (!Number.isFinite(size) || size <= 0) {
        return null;
    }
    return {
        name:
            "name" in value && typeof value.name === "string"
                ? value.name
                : "company-document.pdf",
        type:
            "type" in value && typeof value.type === "string" ? value.type : "",
        size,
    };
}

function queuedDocument(): DocumentResponse {
    return {
        documentId: mockDocumentId,
        businessId: mockBusinessId,
        storedFileId: mockFileId,
        type: "COMPANY_DOCUMENT",
        state: "QUEUED",
        createdAt: "2026-09-02T12:06:30Z",
    };
}

function parsedDocument(): DocumentResponse {
    return {
        ...queuedDocument(),
        state: "PARSED",
        extraction: {
            extractionId: "00000000-0000-4000-8000-000000000025",
            provider: "mock-parser",
            fields: [
                {
                    path: "legalName",
                    value: pendingOnboarding.legalName,
                    confidence: 0.91,
                },
                {
                    path: "tradingName",
                    value: pendingOnboarding.tradingName,
                    confidence: 0.84,
                },
                {
                    path: "registeredAddress",
                    value: pendingOnboarding.registeredAddress,
                    confidence: 0.88,
                },
            ],
        },
    };
}
