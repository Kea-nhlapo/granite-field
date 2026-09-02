import type { ApiProblem } from "../../shared/api/generated";

export function restrictedProblemTitle(
    error: ApiProblem | undefined,
    fallback: string,
) {
    if (error?.status === 401 || error?.code === "UNAUTHORIZED") {
        return "Sign in is required";
    }
    if (error?.status === 403 || error?.code === "ACCESS_DENIED") {
        return "Access denied";
    }
    return error?.title?.trim() || fallback;
}

export function isRetryableRestrictedProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return (
        status === 0 ||
        status === 429 ||
        status === 500 ||
        status === 502 ||
        status === 503
    );
}
