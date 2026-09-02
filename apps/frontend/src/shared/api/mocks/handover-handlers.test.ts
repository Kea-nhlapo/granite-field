import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import { handoverConfirm, handoverGet, handoverIssue } from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, resetHandoverMocks } from "./handlers";
import {
    mockChallengeId,
    mockCounterpartyUserId,
    mockQrPayload,
} from "./handover-handlers";
import { mockBusinessId } from "./onboarding-handlers";
import { mockShipmentId } from "./tracking-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetHandoverMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("handover mocks", () => {
    test("issue, get, and confirm hide the payload on refetch", async () => {
        setApiAccessToken("mock-access-token");
        const issued = await handoverIssue({
            body: {
                type: "COLLECTION",
                counterpartyUserId: mockCounterpartyUserId,
            },
            path: {
                businessId: mockBusinessId,
                shipmentId: mockShipmentId,
            },
        });
        expect(issued.response?.status).toBe(201);
        expect(issued.data?.qrPayload).toBe(mockQrPayload);
        expect(issued.data?.challenge?.challengeId).toBe(mockChallengeId);

        const loaded = await handoverGet({
            path: {
                businessId: mockBusinessId,
                shipmentId: mockShipmentId,
                challengeId: mockChallengeId,
            },
        });
        expect(loaded.data?.challengeId).toBe(mockChallengeId);
        expect(
            (loaded.data as { qrPayload?: string } | undefined)?.qrPayload,
        ).toBeUndefined();

        const confirmed = await handoverConfirm({
            body: {
                commandId: "00000000-0000-4000-8000-0000000000b1",
                qrPayload: mockQrPayload,
                captureMode: "ONLINE",
                observedAt: "2026-09-02T12:00:00Z",
                latitude: -26.2041,
                longitude: 28.0473,
                quantityOutcome: "MATCHED",
                quantityNote: "20 cases collected",
            },
        });
        expect(confirmed.data?.confirmations?.[0]?.quantityNote).toBe(
            "20 cases collected",
        );
    });
});
