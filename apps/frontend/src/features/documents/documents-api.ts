import {
    documentComparisonCompare,
    documentComparisonGet,
    documentConfirm,
    documentGet,
    documentRegister,
    fileStorageUpload,
} from "../../shared/api/app-api";
import type { ApiProblem, DocumentResponse } from "../../shared/api/generated";

const pollIntervalMs = 120;
const pollAttempts = 20;

export const reviewNeededConfidence = 0.85;

export function needsReview(confidence: number | undefined) {
    return confidence === undefined || confidence < reviewNeededConfidence;
}

export type ConfirmationOverlay = {
    revision?: number;
    fields?: Array<{ path?: string; value?: string }>;
};

export function confirmationOverlay(
    document: DocumentResponse,
): ConfirmationOverlay | undefined {
    return document.confirmation as ConfirmationOverlay | undefined;
}

export function uploadInvoiceDocument(businessId: string, file: File) {
    return fileStorageUpload({
        body: { file },
        path: { businessId },
        query: { category: "INVOICE" },
    });
}

export function registerInvoiceDocument(
    businessId: string,
    storedFileId: string,
) {
    return documentRegister({
        body: {
            requestId: crypto.randomUUID(),
            storedFileId,
            type: "INVOICE",
        },
        path: { businessId },
    });
}

export function loadDocument(businessId: string, documentId: string) {
    return documentGet({
        path: { businessId, documentId },
    });
}

export async function waitForDocumentReady(
    businessId: string,
    documentId: string,
    signal?: AbortSignal,
): Promise<{ document?: DocumentResponse; error?: ApiProblem }> {
    for (let attempt = 0; attempt < pollAttempts; attempt += 1) {
        if (signal?.aborted) {
            return {
                error: {
                    code: "INTERNAL_ERROR",
                    detail: "The request was cancelled.",
                    instance: "/api/businesses/documents",
                    requestId: "",
                    status: 500,
                    title: "The request was cancelled",
                    type: "about:blank",
                },
            };
        }
        const result = await loadDocument(businessId, documentId);
        if (result.error) {
            return { error: result.error as ApiProblem };
        }
        if (result.data?.state === "FAILED") {
            return {
                error: {
                    code: "DOCUMENT_PROCESSING_FAILED",
                    detail: "The document could not be read.",
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

export function confirmInvoiceFields(
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

export function compareInvoiceWithPurchaseOrder(
    businessId: string,
    comparedDocumentId: string,
    referenceDocumentId: string,
) {
    return documentComparisonCompare({
        body: {
            comparedDocumentId,
            referenceDocumentId,
            requestId: crypto.randomUUID(),
        },
        path: { businessId },
    });
}

export function loadComparison(businessId: string, comparisonId: string) {
    return documentComparisonGet({
        path: { businessId, comparisonId },
    });
}

function delay(ms: number, signal?: AbortSignal) {
    return new Promise<void>((resolve) => {
        const timer = window.setTimeout(() => {
            signal?.removeEventListener("abort", onAbort);
            resolve();
        }, ms);
        const onAbort = () => {
            window.clearTimeout(timer);
            resolve();
        };
        if (signal?.aborted) {
            onAbort();
            return;
        }
        signal?.addEventListener("abort", onAbort, { once: true });
    });
}
