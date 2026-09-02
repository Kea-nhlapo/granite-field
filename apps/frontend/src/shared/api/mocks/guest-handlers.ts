import { http, HttpResponse } from "msw";

import type {
    GuestInvitationResponse,
    InvitationResponse,
    SupplierProfileResponse,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf } from "./mock-http";

export const mockGuestInvitationId = "00000000-0000-4000-8000-000000000031";
export const mockGuestSupplierProfileId =
    "00000000-0000-4000-8000-000000000032";
export const mockGuestBuyerBusinessId = "00000000-0000-4000-8000-000000000001";
export const mockGuestRequestId = "00000000-0000-4000-8000-000000000033";

const guestInvitation: GuestInvitationResponse = {
    buyerBusinessId: mockGuestBuyerBusinessId,
    expiresAt: "2026-09-09T12:00:00Z",
    invitationId: mockGuestInvitationId,
    purpose: "QUOTE_RESPONSE",
    requestId: mockGuestRequestId,
    supplierProfileId: mockGuestSupplierProfileId,
};

let recordedReference: string | undefined;

export function resetGuestMocks() {
    recordedReference = undefined;
}

export const guestHandlers = [
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/:token`,
        ({ request }) => {
            const scenario = scenarioOf(request);
            const unavailable = guestUnavailable(scenario);
            if (unavailable) {
                return unavailable;
            }
            if (scenario === "rate-limited") {
                return problem(
                    429,
                    "Too many invitation attempts; try again later",
                    "SUPPLIER_INVITATION_RATE_LIMITED",
                );
            }
            if (scenario === "server-error") {
                return problem(
                    500,
                    "Request could not be completed",
                    "INTERNAL_ERROR",
                );
            }
            return HttpResponse.json(guestInvitation);
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/:token/responses`,
        async ({ request }) => {
            const scenario = scenarioOf(request);
            const unavailable = guestUnavailable(scenario);
            if (unavailable) {
                return unavailable;
            }
            if (scenario === "conflict") {
                return problem(
                    409,
                    "The supplier invitation changed while the request was being processed",
                    "SUPPLIER_INVITATION_STATE_CHANGED",
                );
            }
            if (scenario === "server-error") {
                return problem(
                    500,
                    "Request could not be completed",
                    "INTERNAL_ERROR",
                );
            }
            if (scenario === "rate-limited") {
                return problem(
                    429,
                    "Too many invitation attempts; try again later",
                    "SUPPLIER_INVITATION_RATE_LIMITED",
                );
            }

            const body = (await request.json()) as {
                requestId?: string;
                responseReference?: string;
            };
            if (
                !body.requestId ||
                !body.responseReference ||
                body.requestId !== mockGuestRequestId
            ) {
                return problem(
                    404,
                    "This supplier invitation is unavailable",
                    "SUPPLIER_INVITATION_UNAVAILABLE",
                );
            }

            if (
                recordedReference &&
                recordedReference !== body.responseReference
            ) {
                return problem(
                    404,
                    "This supplier invitation is unavailable",
                    "SUPPLIER_INVITATION_UNAVAILABLE",
                );
            }

            recordedReference = body.responseReference;
            const response: InvitationResponse = {
                invitationId: mockGuestInvitationId,
                requestId: mockGuestRequestId,
                respondedAt: "2026-09-02T13:00:00Z",
                responseReference: recordedReference,
                status: "RESPONDED",
            };
            return HttpResponse.json(response);
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/supplier-profiles/:supplierProfileId/conversion`,
        async ({ request, params }) => {
            const scenario = scenarioOf(request);
            if (scenario === "forbidden") {
                return problem(
                    403,
                    "The account does not control this temporary supplier profile",
                    "SUPPLIER_CONTROL_NOT_PROVEN",
                );
            }
            if (scenario === "conflict") {
                return problem(
                    409,
                    "This temporary supplier has already been converted by another account",
                    "SUPPLIER_PROFILE_ALREADY_CLAIMED",
                );
            }
            if (
                String(params.supplierProfileId) !== mockGuestSupplierProfileId
            ) {
                return problem(
                    404,
                    "The supplier was not found",
                    "SUPPLIER_PROFILE_NOT_FOUND",
                );
            }
            const body = (await request.json()) as {
                invitationToken?: string;
            };
            if (!body.invitationToken?.trim()) {
                return problem(
                    400,
                    "Request validation failed",
                    "INVALID_REQUEST",
                );
            }
            if (!recordedReference) {
                return problem(
                    403,
                    "The account does not control this temporary supplier profile",
                    "SUPPLIER_CONTROL_NOT_PROVEN",
                );
            }
            const profile: SupplierProfileResponse = {
                claimedUserId: "00000000-0000-4000-8000-000000000012",
                convertedAt: "2026-09-02T13:05:00Z",
                createdAt: "2026-09-02T12:00:00Z",
                status: "REGISTERED",
                supplierEmail: "supplier@example.com",
                supplierProfileId: mockGuestSupplierProfileId,
            };
            return HttpResponse.json(profile);
        },
    ),
];

function guestUnavailable(scenario: string) {
    if (
        scenario === "expired" ||
        scenario === "revoked" ||
        scenario === "used" ||
        scenario === "invalid"
    ) {
        return problem(
            404,
            "This supplier invitation is unavailable",
            "SUPPLIER_INVITATION_UNAVAILABLE",
        );
    }
    return undefined;
}
