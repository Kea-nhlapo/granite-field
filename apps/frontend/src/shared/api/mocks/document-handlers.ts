import { http, HttpResponse } from "msw";

import type {
    ComparisonResponse,
    DocumentResponse,
    FileMetadataResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, uploadedFile } from "./mock-http";
import {
    mockBusinessId,
    mockDocumentId,
    mockFileId,
} from "./onboarding-handlers";

export const mockInvoiceFileId = "00000000-0000-4000-8000-000000000041";
export const mockInvoiceDocumentId = "00000000-0000-4000-8000-000000000042";
export const mockPurchaseOrderDocumentId =
    "00000000-0000-4000-8000-000000000043";
export const mockComparisonId = "00000000-0000-4000-8000-000000000044";

const reviewNeededConfidence = 0.42;

let companyDocumentGets = 0;
let invoiceDocumentGets = 0;
let confirmedInvoiceFields: Array<{ path: string; value: string }> | undefined;

export function resetDocumentMocks() {
    companyDocumentGets = 0;
    invoiceDocumentGets = 0;
    confirmedInvoiceFields = undefined;
}

export const documentHandlers = [
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

            const category =
                new URL(request.url).searchParams.get("category") ??
                "COMPANY_DOCUMENT";
            const metadata: FileMetadataResponse = {
                fileId: category === "INVOICE" ? mockInvoiceFileId : mockFileId,
                businessId: mockBusinessId,
                category: category as FileMetadataResponse["category"],
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
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/comparisons`,
        async ({ request, params }) => {
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The business was not found",
                    "BUSINESS_NOT_FOUND",
                );
            }
            const body = (await request.json()) as {
                requestId?: string;
                referenceDocumentId?: string;
                comparedDocumentId?: string;
            };
            if (
                !body.requestId ||
                body.referenceDocumentId !== mockPurchaseOrderDocumentId ||
                body.comparedDocumentId !== mockInvoiceDocumentId
            ) {
                return problem(
                    400,
                    "Those documents cannot be compared",
                    "DOCUMENT_COMPARISON_SOURCE_UNSUPPORTED",
                );
            }
            return HttpResponse.json(comparisonResponse(), { status: 201 });
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/comparisons/:comparisonId`,
        ({ params }) => {
            if (
                String(params.businessId) !== mockBusinessId ||
                String(params.comparisonId) !== mockComparisonId
            ) {
                return problem(
                    404,
                    "The document comparison was not found",
                    "DOCUMENT_COMPARISON_NOT_FOUND",
                );
            }
            return HttpResponse.json(comparisonResponse());
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
            if (!body.storedFileId || !body.requestId) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            if (
                body.type === "COMPANY_DOCUMENT" &&
                body.storedFileId === mockFileId
            ) {
                companyDocumentGets = 0;
                return HttpResponse.json(queuedCompanyDocument(), {
                    status: 202,
                });
            }
            if (
                body.type === "INVOICE" &&
                body.storedFileId === mockInvoiceFileId
            ) {
                invoiceDocumentGets = 0;
                confirmedInvoiceFields = undefined;
                return HttpResponse.json(queuedInvoiceDocument(), {
                    status: 202,
                });
            }
            return problem(400, "Request validation failed", "INVALID_REQUEST");
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/:documentId`,
        ({ request, params }) => {
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The document was not found",
                    "DOCUMENT_NOT_FOUND",
                );
            }
            const documentId = String(params.documentId);
            if (documentId === mockDocumentId) {
                companyDocumentGets += 1;
                if (companyDocumentGets < 2) {
                    return HttpResponse.json({
                        ...queuedCompanyDocument(),
                        state: "PROCESSING",
                    } satisfies DocumentResponse);
                }
                return HttpResponse.json(parsedCompanyDocument());
            }
            if (documentId === mockPurchaseOrderDocumentId) {
                return HttpResponse.json(confirmedPurchaseOrder());
            }
            if (documentId === mockInvoiceDocumentId) {
                const scenario = scenarioOf(request);
                if (scenario === "parse-failed") {
                    return HttpResponse.json({
                        ...queuedInvoiceDocument(),
                        state: "FAILED",
                    } satisfies DocumentResponse);
                }
                invoiceDocumentGets += 1;
                if (invoiceDocumentGets < 2) {
                    return HttpResponse.json({
                        ...queuedInvoiceDocument(),
                        state: "PROCESSING",
                    } satisfies DocumentResponse);
                }
                if (confirmedInvoiceFields) {
                    return HttpResponse.json(confirmedInvoice());
                }
                return HttpResponse.json(parsedInvoice());
            }
            return problem(
                404,
                "The document was not found",
                "DOCUMENT_NOT_FOUND",
            );
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/:documentId/confirmations`,
        async ({ request, params }) => {
            if (String(params.businessId) !== mockBusinessId) {
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
                    "Confirmation fields must contain unique, non-empty paths and values",
                    "INVALID_DOCUMENT_CONFIRMATION",
                );
            }
            const documentId = String(params.documentId);
            if (documentId === mockDocumentId) {
                return HttpResponse.json({
                    ...parsedCompanyDocument(),
                    state: "CONFIRMED",
                } satisfies DocumentResponse);
            }
            if (documentId === mockInvoiceDocumentId) {
                confirmedInvoiceFields = body.fields.map((field) => ({
                    path: field.path ?? "",
                    value: field.value ?? "",
                }));
                return HttpResponse.json(confirmedInvoice());
            }
            return problem(
                404,
                "The document was not found",
                "DOCUMENT_NOT_FOUND",
            );
        },
    ),
];

function queuedCompanyDocument(): DocumentResponse {
    return {
        documentId: mockDocumentId,
        businessId: mockBusinessId,
        storedFileId: mockFileId,
        type: "COMPANY_DOCUMENT",
        state: "QUEUED",
        createdAt: "2026-09-02T12:06:30Z",
    };
}

function parsedCompanyDocument(): DocumentResponse {
    return {
        ...queuedCompanyDocument(),
        state: "PARSED",
        extraction: {
            extractionId: "00000000-0000-4000-8000-000000000025",
            provider: "mock-parser",
            fields: [
                {
                    path: "legalName",
                    value: "Mahlako General Trading (Pty) Ltd",
                    confidence: 0.91,
                },
                {
                    path: "tradingName",
                    value: "Mahlako General Store",
                    confidence: 0.84,
                },
                {
                    path: "registeredAddress",
                    value: "42 Madiba Street, Tembisa, Gauteng",
                    confidence: 0.88,
                },
            ],
        },
    };
}

function queuedInvoiceDocument(): DocumentResponse {
    return {
        documentId: mockInvoiceDocumentId,
        businessId: mockBusinessId,
        storedFileId: mockInvoiceFileId,
        type: "INVOICE",
        state: "QUEUED",
        createdAt: "2026-09-02T14:00:00Z",
    };
}

function invoiceExtraction(): NonNullable<DocumentResponse["extraction"]> {
    return {
        extractionId: "00000000-0000-4000-8000-000000000045",
        provider: "mock-parser",
        fields: [
            {
                path: "quantity",
                value: "130",
                confidence: reviewNeededConfidence,
            },
            { path: "unitPrice", value: "11.00", confidence: 0.93 },
            { path: "supplier.name", value: "XYZ", confidence: 0.9 },
            { path: "customer.name", value: "Other Store", confidence: 0.88 },
            { path: "destination", value: "Alexandra", confidence: 0.87 },
            { path: "documentDate", value: "2026-09-05", confidence: 0.86 },
        ],
    };
}

function parsedInvoice(): DocumentResponse {
    return {
        ...queuedInvoiceDocument(),
        state: "PARSED",
        extraction: invoiceExtraction(),
    };
}

function confirmedInvoice(): DocumentResponse {
    return {
        ...parsedInvoice(),
        state: "CONFIRMED",
        confirmation: {
            confirmationId: "00000000-0000-4000-8000-000000000046",
            actorUserId: "00000000-0000-4000-8000-000000000010",
            observedAt: "2026-09-02T14:10:00Z",
            receivedAt: "2026-09-02T14:10:00Z",
            party: "INITIATOR",
            revision: 1,
            fields: confirmedInvoiceFields,
        } as DocumentResponse["confirmation"],
    };
}

function confirmedPurchaseOrder(): DocumentResponse {
    return {
        documentId: mockPurchaseOrderDocumentId,
        businessId: mockBusinessId,
        storedFileId: mockFileId,
        type: "PURCHASE_ORDER",
        state: "CONFIRMED",
        createdAt: "2026-09-02T13:00:00Z",
        extraction: {
            extractionId: "00000000-0000-4000-8000-000000000047",
            provider: "mock-parser",
            fields: [
                { path: "quantity", value: "100", confidence: 0.97 },
                { path: "unitPrice", value: "10.00", confidence: 0.96 },
                { path: "supplier.name", value: "ABC", confidence: 0.95 },
                { path: "customer.name", value: "Kea Store", confidence: 0.94 },
                { path: "destination", value: "Tembisa", confidence: 0.94 },
                { path: "documentDate", value: "2026-09-04", confidence: 0.93 },
            ],
        },
    };
}

function comparisonResponse(): ComparisonResponse {
    const invoiceValues = Object.fromEntries(
        (confirmedInvoiceFields ?? invoiceExtraction().fields ?? []).map(
            (field) => [field.path, field.value],
        ),
    );
    const po = Object.fromEntries(
        (confirmedPurchaseOrder().extraction?.fields ?? []).map((field) => [
            field.path,
            field.value,
        ]),
    );
    const mismatches = [
        mismatch("quantity", "DOCUMENT_QUANTITY_MISMATCH", po, invoiceValues),
        mismatch("unitPrice", "DOCUMENT_PRICE_MISMATCH", po, invoiceValues),
        mismatch(
            "supplier.name",
            "DOCUMENT_SUPPLIER_MISMATCH",
            po,
            invoiceValues,
        ),
        mismatch(
            "customer.name",
            "DOCUMENT_CUSTOMER_MISMATCH",
            po,
            invoiceValues,
        ),
        mismatch(
            "destination",
            "DOCUMENT_DESTINATION_MISMATCH",
            po,
            invoiceValues,
        ),
        mismatch("documentDate", "DOCUMENT_DATE_MISMATCH", po, invoiceValues),
    ].filter((item) => item !== undefined);

    return {
        comparisonId: mockComparisonId,
        businessId: mockBusinessId,
        ruleSetVersion: "document-comparison/v1",
        createdAt: "2026-09-02T14:12:00Z",
        createdByUserId: "00000000-0000-4000-8000-000000000010",
        reference: {
            documentId: mockPurchaseOrderDocumentId,
            documentType: "PURCHASE_ORDER",
            confirmationRevision: 1,
        },
        compared: {
            documentId: mockInvoiceDocumentId,
            documentType: "INVOICE",
            confirmationRevision: confirmedInvoiceFields ? 1 : 0,
        },
        mismatches,
    };
}

function mismatch(
    path: string,
    rule: NonNullable<ComparisonResponse["mismatches"]>[number]["rule"],
    reference: Record<string, string | undefined>,
    compared: Record<string, string | undefined>,
) {
    if (reference[path] === compared[path]) {
        return undefined;
    }
    return {
        indicatorId: `00000000-0000-4000-8000-00000000005${path.length}`,
        rule,
        ruleVersion: 1,
        fieldPath: path,
        severity: "MEDIUM" as const,
        explanation: "The confirmed values differ between these documents.",
        createdAt: "2026-09-02T14:12:00Z",
        reference: {
            documentId: mockPurchaseOrderDocumentId,
            confirmedValue: reference[path],
        },
        compared: {
            documentId: mockInvoiceDocumentId,
            confirmedValue: compared[path],
        },
    };
}
