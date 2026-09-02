import type { ApiProblem } from "../../shared/api/generated";

export function problemMessage(
    error: ApiProblem | undefined,
    fallback: string,
) {
    return error?.title?.trim() || fallback;
}

export function isRetryableOnboardingProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return status === 500 || status === 502 || status === 503;
}
