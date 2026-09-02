import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import { runtimeConfig } from "../../lib/runtime-config";
import { handlers, mockScenarioHeader } from "./handlers";

const server = setupServer(...handlers);
const businessId = "00000000-0000-4000-8000-000000000001";

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("API mock scenarios", () => {
    test.each([
        ["success", 200],
        ["empty", 200],
        ["validation", 400],
        ["forbidden", 403],
        ["server-error", 500],
    ])("returns the %s trust scenario", async (scenario, expectedStatus) => {
        const response = await fetch(
            `${runtimeConfig.apiBaseUrl}/api/public/businesses/${businessId}/trust`,
            { headers: { [mockScenarioHeader]: scenario } },
        );

        expect(response.status).toBe(expectedStatus);
    });

    test("returns a safe expired-link response", async () => {
        const response = await fetch(
            `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/expired-token`,
            { headers: { [mockScenarioHeader]: "expired-link" } },
        );

        expect(response.status).toBe(404);
        await expect(response.json()).resolves.toMatchObject({
            code: "SUPPLIER_INVITATION_UNAVAILABLE",
        });
    });
});
