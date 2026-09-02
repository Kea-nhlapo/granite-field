import type { ApiProblem } from "../../shared/api/generated";

export function problemMessage(
    error: ApiProblem | undefined,
    fallback: string,
) {
    return error?.title?.trim() || fallback;
}

export function isRetryableRoutingProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return (
        status === 0 ||
        status === 429 ||
        status === 500 ||
        status === 502 ||
        status === 503
    );
}

export function isForbiddenRouting(error: ApiProblem | undefined) {
    return error?.status === 403 || error?.code === "ACCESS_DENIED";
}

export function isStaleRouting(error: ApiProblem | undefined) {
    return (
        error?.code === "ROUTE_REQUEST_CONFLICT" ||
        error?.code === "ROUTE_SCORE_REQUEST_CONFLICT" ||
        error?.status === 409
    );
}

export const optionLabels = {
    FASTEST: "Fastest",
    LOWEST_COST: "Lowest cost",
    SAFEST: "Safest",
    BEST_CONNECTIVITY: "Best connectivity",
    RECOMMENDED: "Recommended",
} as const;

export function optionLabel(option: string) {
    return option in optionLabels
        ? optionLabels[option as keyof typeof optionLabels]
        : option;
}
