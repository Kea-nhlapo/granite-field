import {
    procurementConfirmQuote,
    procurementCreateRequest,
    procurementGetOrder,
    procurementGetQuote,
    procurementGetRequest,
} from "../../shared/api/app-api";
import type { CreateProductRequest } from "../../shared/api/generated";

export function createProductRequest(
    businessId: string,
    body: CreateProductRequest,
) {
    return procurementCreateRequest({
        body,
        path: { businessId },
    });
}

export function loadProductRequest(businessId: string, requestId: string) {
    return procurementGetRequest({
        path: { businessId, requestId },
    });
}

export function loadQuote(businessId: string, quoteId: string) {
    return procurementGetQuote({
        path: { businessId, quoteId },
    });
}

export function confirmQuote(
    businessId: string,
    quoteId: string,
    confirmationRequestId: string,
) {
    return procurementConfirmQuote({
        body: { requestId: confirmationRequestId },
        path: { businessId, quoteId },
    });
}

export function loadOrder(businessId: string, orderId: string) {
    return procurementGetOrder({
        path: { businessId, orderId },
    });
}
