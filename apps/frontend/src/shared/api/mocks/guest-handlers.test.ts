import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import { supplierSubmitResponse, supplierViewGuest } from "../app-api";
import { handlers, mockScenarioHeader, resetGuestMocks } from "./handlers";
import { mockGuestRequestId } from "./guest-handlers";

const server = setupServer(...handlers);
const guestToken = ["g1", "g2", "g3", "g4", "g5", "opaque"].join("");

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetGuestMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

describe("guest invitation mocks", () => {
    test("the generated view SDK returns invitation details without a token field", async () => {
        const result = await supplierViewGuest({
            path: { token: guestToken },
        });
        expect(result.error).toBeUndefined();
        expect(result.data?.purpose).toBe("QUOTE_RESPONSE");
        expect(result.data?.requestId).toBe(mockGuestRequestId);
        expect(JSON.stringify(result.data)).not.toContain(guestToken);
        expect(result.data).not.toHaveProperty("invitationToken");
    });

    test.each([
        ["expired", 404, "SUPPLIER_INVITATION_UNAVAILABLE"],
        ["revoked", 404, "SUPPLIER_INVITATION_UNAVAILABLE"],
        ["used", 404, "SUPPLIER_INVITATION_UNAVAILABLE"],
        ["invalid", 404, "SUPPLIER_INVITATION_UNAVAILABLE"],
        ["rate-limited", 429, "SUPPLIER_INVITATION_RATE_LIMITED"],
        ["server-error", 500, "INTERNAL_ERROR"],
    ] as const)(
        "maps the %s view scenario to %s %s",
        async (scenario, status, code) => {
            const result = await supplierViewGuest({
                headers: { [mockScenarioHeader]: scenario },
                path: { token: guestToken },
            });
            expect(result.data).toBeUndefined();
            expect(result.error).toMatchObject({ status, code });
            expect(JSON.stringify(result.error)).not.toContain(guestToken);
        },
    );

    test("submit is idempotent for the same response reference", async () => {
        const responseReference = "00000000-0000-4000-8000-000000000044";
        const first = await supplierSubmitResponse({
            body: {
                requestId: mockGuestRequestId,
                responseReference,
            },
            path: { token: guestToken },
        });
        const retry = await supplierSubmitResponse({
            body: {
                requestId: mockGuestRequestId,
                responseReference,
            },
            path: { token: guestToken },
        });
        expect(first.data?.status).toBe("RESPONDED");
        expect(retry.data?.status).toBe("RESPONDED");
        expect(retry.data?.responseReference).toBe(responseReference);

        const second = await supplierSubmitResponse({
            body: {
                requestId: mockGuestRequestId,
                responseReference: "00000000-0000-4000-8000-000000000045",
            },
            path: { token: guestToken },
        });
        expect(second.error).toMatchObject({
            code: "SUPPLIER_INVITATION_UNAVAILABLE",
        });
    });
});
