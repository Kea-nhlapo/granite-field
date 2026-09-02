import {
    capacityMatchingGet,
    capacityMatchingRelease,
    capacityMatchingReserve,
    capacityMatchingSearch,
    demandAggregationGet,
    demandAggregationSuggest,
} from "../../shared/api/app-api";
import type { SearchRequest } from "../../shared/api/generated";

export function suggestDemandGroup(businessId: string, anchorOrderId: string) {
    return demandAggregationSuggest({
        body: {
            anchorOrderId,
            requestId: crypto.randomUUID(),
        },
        path: { businessId },
    });
}

export function loadSuggestion(businessId: string, suggestionId: string) {
    return demandAggregationGet({
        path: { businessId, suggestionId },
    });
}

export function searchCapacity(
    businessId: string,
    body: Omit<SearchRequest, "requestId"> & { requestId?: string },
) {
    return capacityMatchingSearch({
        body: {
            ...body,
            requestId: body.requestId ?? crypto.randomUUID(),
        },
        path: { businessId },
    });
}

export function loadCapacitySearch(businessId: string, searchId: string) {
    return capacityMatchingGet({
        path: { businessId, searchId },
    });
}

export function reserveCapacity(
    businessId: string,
    searchId: string,
    offerId: string,
) {
    return capacityMatchingReserve({
        body: {
            offerId,
            requestId: crypto.randomUUID(),
        },
        path: { businessId, searchId },
    });
}

export function releaseCapacity(businessId: string, searchId: string) {
    return capacityMatchingRelease({
        path: { businessId, searchId },
    });
}
