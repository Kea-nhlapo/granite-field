import type { ApiProblem } from "../../shared/api/generated";

export function problemMessage(
    error: ApiProblem | undefined,
    fallback: string,
) {
    return error?.title?.trim() || fallback;
}

export function isRetryableProcurementProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return (
        status === 0 ||
        status === 429 ||
        status === 500 ||
        status === 502 ||
        status === 503
    );
}

export function isForbiddenProcurement(error: ApiProblem | undefined) {
    return error?.status === 403 || error?.code === "ACCESS_DENIED";
}

export function isProcurementConflict(error: ApiProblem | undefined) {
    return (
        error?.code === "PROCUREMENT_STATE_CONFLICT" || error?.status === 409
    );
}
