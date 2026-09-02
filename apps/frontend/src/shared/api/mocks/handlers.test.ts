import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import { authLogin, authLogout, authRefresh } from "../app-api";
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
            `${runtimeConfig.apiBaseUrl}/api/supplier-invitations/guest/${["opaque", "guest"].join("")}`,
            { headers: { [mockScenarioHeader]: "expired" } },
        );

        expect(response.status).toBe(404);
        await expect(response.json()).resolves.toMatchObject({
            code: "SUPPLIER_INVITATION_UNAVAILABLE",
        });
    });

    test("the generated login SDK uses mock TokenResponse shapes", async () => {
        const result = await authLogin({
            body: { email: "owner@example.com", password: "correct-horse" },
        });

        expect(result.error).toBeUndefined();
        expect(result.data).toMatchObject({
            tokenType: "Bearer",
            accessToken: "mock-access-token",
            roles: ["BUSINESS_OWNER"],
        });
    });

    test("login maps unauthorized and forbidden ApiProblem bodies", async () => {
        const unauthorized = await authLogin({
            body: { email: "owner@example.com", password: "correct-horse" },
            headers: { [mockScenarioHeader]: "unauthorized" },
        });
        expect(unauthorized.data).toBeUndefined();
        expect(unauthorized.error).toMatchObject({
            status: 401,
            code: "UNAUTHORIZED",
        });

        const forbidden = await authLogin({
            body: { email: "owner@example.com", password: "correct-horse" },
            headers: { [mockScenarioHeader]: "forbidden" },
        });
        expect(forbidden.error).toMatchObject({
            status: 403,
            code: "ACCESS_DENIED",
        });
    });

    test("refresh rotates tokens and logout returns no content", async () => {
        const refreshed = await authRefresh({
            body: { refreshToken: "mock-refresh-token" },
        });
        expect(refreshed.error).toBeUndefined();
        expect(refreshed.data?.accessToken).toBe("mock-access-token-rotated");
        expect(refreshed.data?.refreshToken).toBe("mock-refresh-token-rotated");

        const loggedOut = await authLogout({
            body: { refreshToken: "mock-refresh-token-rotated" },
        });
        expect(loggedOut.error).toBeUndefined();
        expect(loggedOut.response?.status).toBe(204);
    });
});
