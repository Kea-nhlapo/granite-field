import { http, HttpResponse } from "msw";

import type {
    CreateProductRequest,
    ConfirmQuoteRequest,
    OrderResponse,
    ProductRequestResponse,
    QuoteResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockBusinessId } from "./onboarding-handlers";

export const mockQuoteId = "00000000-0000-4000-8000-000000000051";
export const mockOrderId = "00000000-0000-4000-8000-000000000052";
export const mockSupplierProfileId = "00000000-0000-4000-8000-000000000053";
export const mockQuoteSourceDocumentId = "00000000-0000-4000-8000-000000000054";

type StoredRequest = ProductRequestResponse;
type StoredQuote = QuoteResponse;
type StoredOrder = OrderResponse;

const requests = new Map<string, StoredRequest>();
const quotes = new Map<string, StoredQuote>();
const orders = new Map<string, StoredOrder>();
const requestsByClientId = new Map<string, StoredRequest>();
const ordersByConfirmationId = new Map<string, StoredOrder>();

export function resetProcurementMocks() {
    requests.clear();
    quotes.clear();
    orders.clear();
    requestsByClientId.clear();
    ordersByConfirmationId.clear();
}

function quoteMoney() {
    return {
        currency: "ZAR",
        subtotal: 1050,
        taxAmount: 23,
        total: 1073,
    };
}

function quotedItems(
    request: StoredRequest,
): NonNullable<QuoteResponse["items"]> {
    const items = request.items ?? [];
    return items.map((item, index) => {
        const unitPrice = index === 0 ? 40 : 25;
        const lineTotal = index === 0 ? 800 : 250;
        return {
            id: crypto.randomUUID(),
            requestItemId: item.id,
            description: item.description,
            quantity: item.quantity,
            unitOfMeasure: item.unitOfMeasure,
            unitPrice,
            lineTotal,
        };
    });
}

function seedQuote(request: StoredRequest, scenario: string): StoredQuote {
    const quote: StoredQuote = {
        id: mockQuoteId,
        requestId: request.id,
        buyerBusinessId: request.buyerBusinessId,
        supplierProfileId: mockSupplierProfileId,
        sourceDocumentId: mockQuoteSourceDocumentId,
        status: scenario === "expired-quote" ? "EXPIRED" : "ACTIVE",
        money: quoteMoney(),
        validUntil:
            scenario === "expired-quote"
                ? "2020-01-01T00:00:00Z"
                : "2026-12-31T22:00:00Z",
        items: quotedItems(request),
        createdByUserId: "00000000-0000-4000-8000-000000000010",
        createdAt: "2026-09-02T12:00:00Z",
    };
    quotes.set(mockQuoteId, quote);
    return quote;
}

function toOrder(quote: StoredQuote, request: StoredRequest): StoredOrder {
    return {
        id: mockOrderId,
        requestId: request.id,
        sourceQuoteId: quote.id,
        buyerBusinessId: request.buyerBusinessId,
        supplierProfileId: quote.supplierProfileId,
        sourceDocumentId: quote.sourceDocumentId,
        status: "CONFIRMED",
        money: quote.money,
        destination: request.destination,
        deliveryWindow: request.deliveryWindow,
        items: (quote.items ?? []).map((item) => ({
            id: crypto.randomUUID(),
            sourceRequestItemId: item.requestItemId,
            description: item.description,
            quantity: item.quantity,
            unitOfMeasure: item.unitOfMeasure,
            unitPrice: item.unitPrice,
            lineTotal: item.lineTotal,
        })),
        confirmedByUserId: "00000000-0000-4000-8000-000000000010",
        confirmedAt: "2026-09-02T12:05:00Z",
    };
}

export const procurementHandlers = [
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId/confirmations`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "forbidden") {
                return problem(403, "Access denied", "ACCESS_DENIED");
            }
            if (scenario === "expired-quote") {
                return problem(
                    409,
                    "The procurement record cannot change in its current state",
                    "PROCUREMENT_STATE_CONFLICT",
                );
            }
            if (scenario === "already-confirmed") {
                return problem(
                    409,
                    "The procurement record cannot change in its current state",
                    "PROCUREMENT_STATE_CONFLICT",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(404, "Quote was not found", "QUOTE_NOT_FOUND");
            }
            const quote = quotes.get(String(params.quoteId));
            if (!quote) {
                return problem(404, "Quote was not found", "QUOTE_NOT_FOUND");
            }
            const body = (await request.json()) as ConfirmQuoteRequest;
            if (!body.requestId) {
                return problem(
                    400,
                    "The confirmation request is invalid",
                    "INVALID_QUOTE",
                );
            }
            const existing = ordersByConfirmationId.get(body.requestId);
            if (existing) {
                return HttpResponse.json(existing, { status: 201 });
            }
            if (quote.status === "ACCEPTED" || orders.has(mockOrderId)) {
                return problem(
                    409,
                    "The procurement record cannot change in its current state",
                    "PROCUREMENT_STATE_CONFLICT",
                );
            }
            if (quote.status === "EXPIRED") {
                return problem(
                    409,
                    "The procurement record cannot change in its current state",
                    "PROCUREMENT_STATE_CONFLICT",
                );
            }
            const productRequest = requests.get(quote.requestId ?? "");
            if (!productRequest) {
                return problem(
                    404,
                    "Product request was not found",
                    "PRODUCT_REQUEST_NOT_FOUND",
                );
            }
            const order = toOrder(quote, productRequest);
            orders.set(mockOrderId, order);
            ordersByConfirmationId.set(body.requestId, order);
            quote.status = "ACCEPTED";
            quotes.set(mockQuoteId, quote);
            productRequest.status = "ORDERED";
            requests.set(productRequest.id ?? "", productRequest);
            return HttpResponse.json(order, { status: 201 });
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/requests`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "Product request was not found",
                    "PRODUCT_REQUEST_NOT_FOUND",
                );
            }
            const body = (await request.json()) as CreateProductRequest;
            const invalidItems =
                !body.items?.length ||
                body.items.some(
                    (item) =>
                        !item.description?.trim() ||
                        !item.quantity ||
                        item.quantity <= 0,
                );
            const invalidWindow =
                !body.deliveryWindowStart ||
                !body.deliveryWindowEnd ||
                body.deliveryWindowStart >= body.deliveryWindowEnd;
            if (
                !body.requestId ||
                !body.destinationLabel?.trim() ||
                invalidItems ||
                invalidWindow
            ) {
                return problem(
                    400,
                    "The product request is invalid",
                    "INVALID_PRODUCT_REQUEST",
                );
            }
            const existing = requestsByClientId.get(body.requestId);
            if (existing) {
                return HttpResponse.json(existing, { status: 201 });
            }
            const created: StoredRequest = {
                id: crypto.randomUUID(),
                buyerBusinessId: mockBusinessId,
                status: "QUOTED",
                destination: {
                    label: body.destinationLabel,
                    latitude: body.destinationLatitude,
                    longitude: body.destinationLongitude,
                },
                deliveryWindow: {
                    start: body.deliveryWindowStart,
                    end: body.deliveryWindowEnd,
                },
                items: body.items.map((item) => ({
                    id: item.itemId,
                    productCode: item.productCode,
                    description: item.description,
                    quantity: item.quantity,
                    unitOfMeasure: item.unitOfMeasure,
                })),
                createdByUserId: "00000000-0000-4000-8000-000000000010",
                createdAt: "2026-09-02T12:00:00Z",
                updatedAt: "2026-09-02T12:00:00Z",
            };
            requests.set(created.id ?? "", created);
            requestsByClientId.set(body.requestId, created);
            seedQuote(created, scenario);
            return HttpResponse.json(created, { status: 201 });
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/requests/:requestId`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "Product request was not found",
                    "PRODUCT_REQUEST_NOT_FOUND",
                );
            }
            const found = requests.get(String(params.requestId));
            if (!found) {
                return problem(
                    404,
                    "Product request was not found",
                    "PRODUCT_REQUEST_NOT_FOUND",
                );
            }
            return HttpResponse.json(found);
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/quotes/:quoteId`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "empty") {
                return problem(404, "Quote was not found", "QUOTE_NOT_FOUND");
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(404, "Quote was not found", "QUOTE_NOT_FOUND");
            }
            const found = quotes.get(String(params.quoteId));
            if (!found) {
                return problem(404, "Quote was not found", "QUOTE_NOT_FOUND");
            }
            if (scenario === "expired-quote") {
                return HttpResponse.json({ ...found, status: "EXPIRED" });
            }
            return HttpResponse.json(found);
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/procurement/orders/:orderId`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(404, "Order was not found", "ORDER_NOT_FOUND");
            }
            const found = orders.get(String(params.orderId));
            if (!found) {
                return problem(404, "Order was not found", "ORDER_NOT_FOUND");
            }
            return HttpResponse.json(found);
        },
    ),
];
