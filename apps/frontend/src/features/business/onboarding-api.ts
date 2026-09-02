import {
    businessConfirmRegisteredOnboarding,
    businessGetRegisteredOnboarding,
    businessStartRegisteredOnboarding,
    documentConfirm,
    documentGet,
    documentRegister,
    fileStorageUpload,
} from "../../shared/api/app-api";
import type {
    ApiProblem,
    BusinessProfileResponse,
    DocumentResponse,
    RegisteredOnboardingResponse,
} from "../../shared/api/generated";

const pollIntervalMs = 120;
const pollAttempts = 20;

export async function startRegisteredOnboarding(registrationNumber: string) {
    return businessStartRegisteredOnboarding({
        body: { registrationNumber },
    });
}

export async function loadRegisteredOnboarding(onboardingId: string) {
    return businessGetRegisteredOnboarding({
        path: { onboardingId },
    });
}

export async function confirmRegisteredOnboarding(onboardingId: string) {
    return businessConfirmRegisteredOnboarding({
        path: { onboardingId },
    });
}

export async function waitForRegistryDetails(
    onboardingId: string,
    signal?: AbortSignal,
): Promise<{
    draft?: RegisteredOnboardingResponse;
    error?: ApiProblem;
}> {
    for (let attempt = 0; attempt < pollAttempts; attempt += 1) {
        if (signal?.aborted) {
            return { error: abortedProblem() };
        }
        const result = await loadRegisteredOnboarding(onboardingId);
        if (result.error) {
            return { error: result.error as ApiProblem };
        }
        if (result.data?.legalName) {
            return { draft: result.data };
        }
        await delay(pollIntervalMs, signal);
    }
    return {
        error: {
            code: "EXTERNAL_PROVIDER_UNAVAILABLE",
            detail: "The company registry did not finish in time.",
            instance: "/api/businesses/onboarding/registered",
            requestId: "",
            status: 503,
            title: "The company registry is still processing this lookup",
            type: "about:blank",
        },
    };
}

export async function uploadCompanyDocument(businessId: string, file: File) {
    return fileStorageUpload({
        body: { file },
        path: { businessId },
        query: { category: "COMPANY_DOCUMENT" },
    });
}

export async function registerCompanyDocument(
    businessId: string,
    storedFileId: string,
) {
    return documentRegister({
        body: {
            requestId: crypto.randomUUID(),
            storedFileId,
            type: "COMPANY_DOCUMENT",
        },
        path: { businessId },
    });
}

export async function waitForParsedDocument(
    businessId: string,
    documentId: string,
    signal?: AbortSignal,
): Promise<{ document?: DocumentResponse; error?: ApiProblem }> {
    for (let attempt = 0; attempt < pollAttempts; attempt += 1) {
        if (signal?.aborted) {
            return { error: abortedProblem() };
        }
        const result = await documentGet({
            path: { businessId, documentId },
        });
        if (result.error) {
            return { error: result.error as ApiProblem };
        }
        if (result.data?.state === "FAILED") {
            return {
                error: {
                    code: "DOCUMENT_PROCESSING_FAILED",
                    detail: "The company document could not be read.",
                    instance: "/api/businesses/documents",
                    requestId: "",
                    status: 500,
                    title: "Document processing failed",
                    type: "about:blank",
                },
            };
        }
        if (
            result.data?.state === "PARSED" ||
            result.data?.state === "CONFIRMED"
        ) {
            return { document: result.data };
        }
        await delay(pollIntervalMs, signal);
    }
    return {
        error: {
            code: "EXTERNAL_PROVIDER_UNAVAILABLE",
            detail: "Document processing did not finish in time.",
            instance: "/api/businesses/documents",
            requestId: "",
            status: 503,
            title: "Document processing is taking too long",
            type: "about:blank",
        },
    };
}

export async function confirmCompanyDocument(
    businessId: string,
    documentId: string,
    fields: Array<{ path: string; value: string }>,
) {
    return documentConfirm({
        body: {
            fields,
            requestId: crypto.randomUUID(),
        },
        path: { businessId, documentId },
    });
}

export type {
    BusinessProfileResponse,
    DocumentResponse,
    RegisteredOnboardingResponse,
};

function abortedProblem(): ApiProblem {
    return {
        code: "INTERNAL_ERROR",
        detail: "The request was cancelled.",
        instance: "/api",
        requestId: "",
        status: 500,
        title: "The request was cancelled",
        type: "about:blank",
    };
}

function delay(ms: number, signal?: AbortSignal) {
    return new Promise<void>((resolve, reject) => {
        const timer = window.setTimeout(() => {
            signal?.removeEventListener("abort", onAbort);
            resolve();
        }, ms);
        const onAbort = () => {
            window.clearTimeout(timer);
            reject(new DOMException("Aborted", "AbortError"));
        };
        if (signal?.aborted) {
            onAbort();
            return;
        }
        signal?.addEventListener("abort", onAbort, { once: true });
    }).catch(() => undefined);
}
