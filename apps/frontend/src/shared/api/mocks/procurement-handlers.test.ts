import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import {
    procurementConfirmQuote,
    procurementCreateRequest,
    procurementGetOrder,
    procurementGetQuote,
    procurementGetRequest,
} from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, resetProcurementMocks } from "./handlers";
import { mockBusinessId } from "./onboarding-handlers";
import { mockOrderId, mockQuoteId } from "./procurement-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetProcurementMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("procurement mocks", () => {
    test("create, get quote, confirm, and get order use generated operations", async () => {
        setApiAccessToken("mock-access-token");
        const created = await procurementCreateRequest({
            body: {
                requestId: "00000000-0000-4000-8000-000000000080",
                destinationLabel: "Tembisa, Gauteng",
                deliveryWindowStart: "2026-10-01T06:00:00Z",
                deliveryWindowEnd: "2026-10-02T06:00:00Z",
                items: [
                    {
                        itemId: "00000000-0000-4000-8000-000000000081",
                        description: "20 cases soft drinks",
                        quantity: 20,
                        unitOfMeasure: "CASE",
                    },
                    {
                        itemId: "00000000-0000-4000-8000-000000000082",
                        description: "10 bags maize meal",
                        quantity: 10,
                        unitOfMeasure: "EACH",
                    },
                ],
            },
            path: { businessId: mockBusinessId },
        });
        expect(created.response?.status).toBe(201);
        expect(created.data?.id).toMatch(
            /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i,
        );

        const request = await procurementGetRequest({
            path: {
                businessId: mockBusinessId,
                requestId: created.data?.id ?? "",
            },
        });
        expect(request.data?.status).toBe("QUOTED");

        const quote = await procurementGetQuote({
            path: { businessId: mockBusinessId, quoteId: mockQuoteId },
        });
        expect(quote.data?.id).toBe(mockQuoteId);
        expect(quote.data?.money).toEqual({
            currency: "ZAR",
            subtotal: 1050,
            taxAmount: 23,
            total: 1073,
        });

        const confirmationRequestId = "00000000-0000-4000-8000-000000000083";
        const first = await procurementConfirmQuote({
            body: { requestId: confirmationRequestId },
            path: { businessId: mockBusinessId, quoteId: mockQuoteId },
        });
        const retry = await procurementConfirmQuote({
            body: { requestId: confirmationRequestId },
            path: { businessId: mockBusinessId, quoteId: mockQuoteId },
        });
        expect(first.data?.id).toBe(mockOrderId);
        expect(retry.data?.id).toBe(first.data?.id);

        const order = await procurementGetOrder({
            path: { businessId: mockBusinessId, orderId: mockOrderId },
        });
        expect(order.data?.money?.total).toBe(1073);
        expect(order.data?.status).toBe("CONFIRMED");
    });
});
