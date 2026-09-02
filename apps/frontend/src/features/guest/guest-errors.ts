import type { ApiProblem } from "../../shared/api/generated";

export function problemMessage(
    error: ApiProblem | undefined,
    fallback: string,
) {
    return error?.title?.trim() || fallback;
}

export function isUnavailableInvitation(error: ApiProblem | undefined) {
    return error?.code === "SUPPLIER_INVITATION_UNAVAILABLE";
}

export function isRetryableGuestProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return (
        status === 429 ||
        status === 500 ||
        status === 502 ||
        status === 503 ||
        error?.code === "SUPPLIER_INVITATION_STATE_CHANGED"
    );
}
