import type { ApiProblem } from "../../shared/api/generated";

export function problemMessage(
    error: ApiProblem | undefined,
    fallback: string,
) {
    return error?.title?.trim() || error?.detail?.trim() || fallback;
}

export function isRetryableHandoverProblem(error: ApiProblem | undefined) {
    const status = error?.status;
    return (
        status === 0 ||
        status === 429 ||
        status === 500 ||
        status === 502 ||
        status === 503
    );
}

export function handoverFailureTitle(error: ApiProblem | undefined) {
    switch (error?.code) {
        case "HANDOVER_CHALLENGE_EXPIRED":
            return "This challenge has expired";
        case "HANDOVER_CHALLENGE_REPLAYED":
            return "This challenge has already been used";
        case "HANDOVER_PARTICIPANT_MISMATCH":
            return "This account is not an expected participant";
        case "HANDOVER_TOKEN_INVALID":
            return "This challenge does not match the shipment";
        case "HANDOVER_OUTSIDE_LOCATION_TOLERANCE":
            return "The confirmation location is outside the allowed area";
        case "HANDOVER_OFFLINE_NOT_ALLOWED":
            return "Offline confirmation was not accepted";
        case "HANDOVER_PARTY_ALREADY_CONFIRMED":
            return "This party has already confirmed";
        case "ACCESS_DENIED":
            return "Access denied";
        default:
            return problemMessage(error, "Handover could not be completed");
    }
}
