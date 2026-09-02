import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import { applyTokenResponse, clearSession } from "../access/session";
import { SessionProvider } from "../access/SessionProvider";
import {
    mockComparisonId,
    mockInvoiceDocumentId,
    mockPurchaseOrderDocumentId,
} from "../../shared/api/mocks/document-handlers";
import {
    analystTokens,
    handlers,
    ownerTokens,
    resetDocumentMocks,
} from "../../shared/api/mocks/handlers";
import { mockBusinessId } from "../../shared/api/mocks/onboarding-handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetDocumentMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function documentsPath(documentId?: string) {
    return documentId
        ? `/app/documents/${mockBusinessId}/${documentId}`
        : `/app/documents/${mockBusinessId}`;
}

function renderDocuments(path = documentsPath()) {
    applyTokenResponse(ownerTokens);
    const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
    return {
        router,
        user: userEvent.setup(),
        ...render(
            <FluentAppProvider>
                <SessionProvider>
                    <RouterProvider router={router} />
                </SessionProvider>
            </FluentAppProvider>,
        ),
    };
}

function invoicePdf() {
    return new File(["%PDF-1 invoice"], "invoice.pdf", {
        type: "application/pdf",
    });
}

describe("document review", () => {
    it("uploads, polls, corrects, confirms, and shows mismatch evidence", async () => {
        const { router, user } = renderDocuments();

        expect(
            await screen.findByRole("heading", { name: "Review an invoice" }),
        ).toBeInTheDocument();
        await user.upload(
            screen.getByLabelText(/invoice document/i),
            invoicePdf(),
        );
        await user.click(
            screen.getByRole("button", { name: "Upload invoice" }),
        );

        expect(
            await screen.findByRole("heading", {
                name: "Review extracted fields",
            }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            documentsPath(mockInvoiceDocumentId),
        );
        expect(screen.getByText(/needs review/i)).toBeInTheDocument();
        expect(screen.getByText(/not treated as certain/i)).toBeInTheDocument();
        expect(screen.getByText(/Extracted value: 130/)).toBeInTheDocument();

        const quantity = screen.getByLabelText(/quantity/i);
        await user.clear(quantity);
        await user.type(quantity, "100");
        await user.click(
            screen.getByRole("button", { name: "Save corrections" }),
        );

        expect(
            await screen.findByRole("heading", { name: "Invoice confirmed" }),
        ).toBeInTheDocument();
        expect(screen.getByDisplayValue("100")).toBeInTheDocument();
        expect(screen.getByText(/Extracted value: 130/)).toBeInTheDocument();

        await user.click(
            screen.getByRole("button", {
                name: "Compare with purchase order",
            }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Comparison indicators",
            }),
        ).toBeInTheDocument();
        expect(
            screen.getAllByText(new RegExp(mockPurchaseOrderDocumentId)).length,
        ).toBeGreaterThan(0);
        expect(
            screen.getAllByText(new RegExp(mockInvoiceDocumentId)).length,
        ).toBeGreaterThan(0);
        expect(screen.getByText(/DOCUMENT_PRICE_MISMATCH/)).toBeInTheDocument();
        expect(document.body.textContent?.toLowerCase()).not.toContain("fraud");
        expect(document.body.textContent?.toLowerCase()).not.toContain("theft");
        expect(mockComparisonId).toBeTruthy();
    });

    it("rejects an unsupported file before upload", async () => {
        const { user } = renderDocuments();
        await screen.findByRole("heading", { name: "Review an invoice" });
        const input = screen.getByLabelText(
            /invoice document/i,
        ) as HTMLInputElement;
        fireEvent.change(input, {
            target: {
                files: [
                    new File(["plain"], "notes.txt", { type: "text/plain" }),
                ],
            },
        });
        await user.click(
            screen.getByRole("button", { name: "Upload invoice" }),
        );
        expect(await screen.findByRole("alert")).toHaveTextContent(
            /PDF, JPG, or PNG invoice document/i,
        );
    });

    it("retries after a parsing failure", async () => {
        server.use(
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/:documentId`,
                () =>
                    HttpResponse.json({
                        documentId: mockInvoiceDocumentId,
                        state: "FAILED",
                        type: "INVOICE",
                    }),
            ),
        );
        const { user } = renderDocuments(documentsPath(mockInvoiceDocumentId));
        expect(
            await screen.findByRole("heading", {
                name: "Document processing failed",
            }),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Try again" }));
        expect(
            await screen.findByRole("heading", { name: "Review an invoice" }),
        ).toBeInTheDocument();
    });

    it("blocks analysts from document review", async () => {
        applyTokenResponse(analystTokens);
        const router = createMemoryRouter(appRoutes, {
            initialEntries: [documentsPath()],
        });
        render(
            <FluentAppProvider>
                <SessionProvider>
                    <RouterProvider router={router} />
                </SessionProvider>
            </FluentAppProvider>,
        );
        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
    });
});
