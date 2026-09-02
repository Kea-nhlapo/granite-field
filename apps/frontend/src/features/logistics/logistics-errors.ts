import type { ApiProblem } from "../../shared/api/generated";

export function problemMessage(
    error: ApiProblem | undefined,
    fallback: string,
) {
    return error?.title?.trim() || fallback;
}

export function isRetryableLogisticsProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return (
        status === 0 ||
        status === 429 ||
        status === 500 ||
        status === 502 ||
        status === 503
    );
}

export function isForbiddenLogistics(error: ApiProblem | undefined) {
    return error?.status === 403 || error?.code === "ACCESS_DENIED";
}

export function isStaleLogistics(error: ApiProblem | undefined) {
    return (
        error?.status === 409 ||
        error?.code === "AGGREGATION_REQUEST_CONFLICT" ||
        error?.code === "DEMAND_DELIVERY_WINDOWS_DO_NOT_OVERLAP" ||
        error?.code === "CAPACITY_MATCH_REQUEST_CONFLICT"
    );
}
