import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { notificationPreferenceGet } from "../../shared/api/app-api";
import { handlers, ownerTokens } from "../../shared/api/mocks/handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";
import { installSessionRefreshInterceptor } from "./session-interceptor";
import { applyTokenResponse, clearSession } from "./session";

const server = setupServer(...handlers);

beforeAll(() => {
    installSessionRefreshInterceptor();
    server.listen({ onUnhandledRequest: "error" });
});
afterEach(() => {
    clearSession();
    server.resetHandlers();
});
afterAll(() => server.close());

describe("session refresh interceptor", () => {
    it("retries a 401 once after rotating tokens", async () => {
        applyTokenResponse(ownerTokens);
        let protectedCalls = 0;
        let refreshCalls = 0;

        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/auth/refresh`,
                async ({ request }) => {
                    refreshCalls += 1;
                    const body = (await request.json()) as {
                        refreshToken?: string;
                    };
                    expect(body.refreshToken).toBe("mock-refresh-token");
                    return HttpResponse.json({
                        ...ownerTokens,
                        accessToken: "mock-access-token-rotated",
                        refreshToken: "mock-refresh-token-rotated",
                    });
                },
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/notification-preferences`,
                ({ request }) => {
                    protectedCalls += 1;
                    const authorization = request.headers.get("Authorization");
                    if (authorization === "Bearer mock-access-token") {
                        return HttpResponse.json(
                            {
                                code: "UNAUTHORIZED",
                                detail: "Authentication is required.",
                                instance: "/api/notification-preferences",
                                requestId:
                                    "00000000-0000-4000-8000-000000000099",
                                status: 401,
                                title: "Authentication is required",
                                type: "about:blank",
                            },
                            { status: 401 },
                        );
                    }
                    if (authorization === "Bearer mock-access-token-rotated") {
                        return HttpResponse.json({ preferences: [] });
                    }
                    return new HttpResponse(null, { status: 500 });
                },
            ),
        );

        const result = await notificationPreferenceGet();

        expect(result.error).toBeUndefined();
        expect(protectedCalls).toBe(2);
        expect(refreshCalls).toBe(1);
    });

    it("does not refresh on 403", async () => {
        applyTokenResponse(ownerTokens);
        let refreshCalls = 0;

        server.use(
            http.post(`${runtimeConfig.apiBaseUrl}/api/auth/refresh`, () => {
                refreshCalls += 1;
                return HttpResponse.json(ownerTokens);
            }),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/notification-preferences`,
                () =>
                    HttpResponse.json(
                        {
                            code: "ACCESS_DENIED",
                            detail: "Access denied.",
                            instance: "/api/notification-preferences",
                            requestId: "00000000-0000-4000-8000-000000000099",
                            status: 403,
                            title: "Access denied",
                            type: "about:blank",
                        },
                        { status: 403 },
                    ),
            ),
        );

        const result = await notificationPreferenceGet();

        expect(result.error).toMatchObject({ status: 403 });
        expect(refreshCalls).toBe(0);
    });
});
