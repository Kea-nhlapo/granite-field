import { http, HttpResponse } from "msw";

import type {
    ChallengeResponse,
    ConfirmHandoverRequest,
    IssueChallengeRequest,
    IssuedChallengeResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockShipmentId } from "./tracking-handlers";

export const mockChallengeId = "00000000-0000-4000-8000-0000000000a1";
export const mockCounterpartyUserId = "00000000-0000-4000-8000-000000000010";
export const mockQrPayload = "tmh_issued-challenge-token";
export const mockDeliveryOrderId = "00000000-0000-4000-8000-000000000096";

type StoredChallenge = ChallengeResponse & { qrPayload: string };

const challenges = new Map<string, StoredChallenge>();

export function resetHandoverMocks() {
    challenges.clear();
}

function locationFor(type: IssueChallengeRequest["type"]) {
    return type === "DELIVERY"
        ? { label: "Midrand", latitude: -25.9992, longitude: 28.1263 }
        : { label: "Johannesburg", latitude: -26.2041, longitude: 28.0473 };
}

function challengeFrom(
    body: IssueChallengeRequest,
    shipmentId: string,
): StoredChallenge {
    return {
        challengeId: mockChallengeId,
        shipmentId,
        type: body.type,
        deliveryOrderId: body.deliveryOrderId,
        state: "PENDING",
        initiatorUserId: "00000000-0000-4000-8000-000000000010",
        counterpartyUserId: body.counterpartyUserId,
        expectedLocation: locationFor(body.type),
        locationToleranceMetres: 150,
        expiresAt: "2099-09-02T12:00:00Z",
        confirmations: [],
        qrPayload: mockQrPayload,
    };
}

function publicChallenge(stored: StoredChallenge): ChallengeResponse {
    const { qrPayload: _hidden, ...challenge } = stored;
    return challenge;
}

export const handoverHandlers = [
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId/handovers/challenges`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.shipmentId) !== mockShipmentId) {
                return problem(
                    404,
                    "The handover was not found",
                    "HANDOVER_NOT_FOUND",
                );
            }
            const body = (await request.json()) as IssueChallengeRequest;
            if (!body.type || !body.counterpartyUserId) {
                return problem(
                    400,
                    "The handover request is invalid",
                    "INVALID_HANDOVER_REQUEST",
                );
            }
            const created = challengeFrom(body, String(params.shipmentId));
            if (scenario === "expired") {
                created.expiresAt = "2020-01-01T00:00:00Z";
                created.state = "EXPIRED";
            }
            challenges.set(mockChallengeId, created);
            const response: IssuedChallengeResponse = {
                challenge: publicChallenge(created),
                qrPayload: created.qrPayload,
            };
            return HttpResponse.json(response, { status: 201 });
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/shipments/:shipmentId/handovers/challenges/:challengeId`,
        ({ params, request }) => {
            const error = standardError(scenarioOf(request));
            if (error) {
                return error;
            }
            const found = challenges.get(String(params.challengeId));
            if (!found) {
                return problem(
                    404,
                    "The handover was not found",
                    "HANDOVER_NOT_FOUND",
                );
            }
            return HttpResponse.json(publicChallenge(found));
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/handovers/confirmations`,
        async ({ request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "replay") {
                return problem(
                    409,
                    "The completed handover challenge cannot be reused",
                    "HANDOVER_CHALLENGE_REPLAYED",
                );
            }
            if (scenario === "wrong-party") {
                return problem(
                    403,
                    "This account is not an expected participant in the handover",
                    "HANDOVER_PARTICIPANT_MISMATCH",
                );
            }
            if (scenario === "location") {
                return problem(
                    422,
                    "The confirmation location is outside the allowed handover area",
                    "HANDOVER_OUTSIDE_LOCATION_TOLERANCE",
                );
            }
            if (scenario === "offline") {
                return problem(
                    409,
                    "Offline handover confirmation is not accepted; reconnect and submit again",
                    "HANDOVER_OFFLINE_NOT_ALLOWED",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            const body = (await request.json()) as ConfirmHandoverRequest;
            const stored = [...challenges.values()].find(
                (item) => item.qrPayload === body.qrPayload,
            );
            if (!stored) {
                return problem(
                    404,
                    "The handover challenge is unavailable",
                    "HANDOVER_TOKEN_INVALID",
                );
            }
            if (stored.state === "EXPIRED") {
                return problem(
                    410,
                    "The handover challenge has expired",
                    "HANDOVER_CHALLENGE_EXPIRED",
                );
            }
            if (body.captureMode === "OFFLINE") {
                return problem(
                    409,
                    "Offline handover confirmation is not accepted; reconnect and submit again",
                    "HANDOVER_OFFLINE_NOT_ALLOWED",
                );
            }
            const expected = stored.expectedLocation;
            if (
                expected &&
                (Math.abs((body.latitude ?? 0) - (expected.latitude ?? 0)) >
                    0.05 ||
                    Math.abs(
                        (body.longitude ?? 0) - (expected.longitude ?? 0),
                    ) > 0.05)
            ) {
                return problem(
                    422,
                    "The confirmation location is outside the allowed handover area",
                    "HANDOVER_OUTSIDE_LOCATION_TOLERANCE",
                );
            }
            stored.confirmations = [
                ...(stored.confirmations ?? []),
                {
                    confirmationId: body.commandId,
                    quantityOutcome: body.quantityOutcome,
                    quantityNote: body.quantityNote,
                    observedAt: body.observedAt,
                    latitude: body.latitude,
                    longitude: body.longitude,
                },
            ];
            stored.state =
                body.quantityOutcome === "DISPUTED"
                    ? "DISPUTED"
                    : (stored.confirmations?.length ?? 0) >= 2
                      ? "COMPLETED"
                      : "PENDING";
            stored.completedAt =
                stored.state === "COMPLETED" || stored.state === "DISPUTED"
                    ? body.observedAt
                    : undefined;
            challenges.set(stored.challengeId ?? mockChallengeId, stored);
            return HttpResponse.json(publicChallenge(stored));
        },
    ),
];
