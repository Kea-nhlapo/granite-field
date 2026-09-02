import {
    handoverConfirm,
    handoverGet,
    handoverIssue,
} from "../../shared/api/app-api";
import type {
    ConfirmHandoverRequest,
    IssueChallengeRequest,
} from "../../shared/api/generated";

export function issueChallenge(
    businessId: string,
    shipmentId: string,
    body: IssueChallengeRequest,
) {
    return handoverIssue({
        body,
        path: { businessId, shipmentId },
    });
}

export function loadChallenge(
    businessId: string,
    shipmentId: string,
    challengeId: string,
) {
    return handoverGet({
        path: { businessId, shipmentId, challengeId },
    });
}

export function confirmHandover(body: ConfirmHandoverRequest) {
    return handoverConfirm({ body });
}
