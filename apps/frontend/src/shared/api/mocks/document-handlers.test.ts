import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import {
    documentComparisonCompare,
    documentGet,
    documentRegister,
    fileStorageUpload,
} from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, resetDocumentMocks } from "./handlers";
import {
    mockInvoiceDocumentId,
    mockInvoiceFileId,
    mockPurchaseOrderDocumentId,
} from "./document-handlers";
import { mockBusinessId } from "./onboarding-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetDocumentMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("document review mocks", () => {
    test("upload, register, poll, and compare use generated document operations", async () => {
        setApiAccessToken("mock-access-token");
        const uploaded = await fileStorageUpload({
            body: {
                file: new File(["%PDF-1 invoice"], "invoice.pdf", {
                    type: "application/pdf",
                }),
            },
            path: { businessId: mockBusinessId },
            query: { category: "INVOICE" },
        });
        expect(uploaded.data?.fileId).toBe(mockInvoiceFileId);

        const registered = await documentRegister({
            body: {
                requestId: "00000000-0000-4000-8000-000000000060",
                storedFileId: mockInvoiceFileId,
                type: "INVOICE",
            },
            path: { businessId: mockBusinessId },
        });
        expect(registered.response?.status).toBe(202);
        expect(registered.data?.state).toBe("QUEUED");

        await documentGet({
            path: {
                businessId: mockBusinessId,
                documentId: mockInvoiceDocumentId,
            },
        });
        const parsed = await documentGet({
            path: {
                businessId: mockBusinessId,
                documentId: mockInvoiceDocumentId,
            },
        });
        expect(parsed.data?.state).toBe("PARSED");
        expect(
            parsed.data?.extraction?.fields?.some(
                (field) =>
                    field.path === "quantity" && field.confidence === 0.42,
            ),
        ).toBe(true);

        const compared = await documentComparisonCompare({
            body: {
                comparedDocumentId: mockInvoiceDocumentId,
                referenceDocumentId: mockPurchaseOrderDocumentId,
                requestId: "00000000-0000-4000-8000-000000000061",
            },
            path: { businessId: mockBusinessId },
        });
        expect(compared.response?.status).toBe(201);
        expect(compared.data?.mismatches?.length).toBeGreaterThan(0);
        expect(JSON.stringify(compared.data).toLowerCase()).not.toContain(
            "fraud",
        );
    });
});
