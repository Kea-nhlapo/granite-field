import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { getApiAccessToken } from "../../shared/api/client";
import { handlers, ownerTokens } from "../../shared/api/mocks/handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";
import { isDevSignInBypassEnabled } from "./dev-preview-session";
import { refreshTokenStorageKey } from "./refresh-token-storage";
import {
    applyTokenResponse,
    clearSession,
    dropMemorySession,
    getSessionSnapshot,
    loginWithPassword,
    logoutSession,
    restoreSession,
} from "./session";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    server.resetHandlers();
});
afterAll(() => server.close());

describe("bearer session", () => {
    it("keeps the access token in memory and the refresh token in sessionStorage", async () => {
        const result = await loginWithPassword(
            "owner@example.com",
            "correct-horse",
        );

        expect(result.error).toBeUndefined();
        expect(result.session?.userId).toBe(ownerTokens.userId);
        expect(result.session?.roles.has("BUSINESS_OWNER")).toBe(true);
        expect(getApiAccessToken()).toBe("mock-access-token");
        expect(sessionStorage.getItem(refreshTokenStorageKey)).toBe(
            "mock-refresh-token",
        );
        expect(localStorage.getItem(refreshTokenStorageKey)).toBeNull();
        expect(result.session && "email" in result.session).toBe(false);
        expect(result.session && "displayName" in result.session).toBe(false);
        expect(result.session && "onboardingComplete" in result.session).toBe(
            false,
        );
    });

    it("restores a session from the rotating refresh token after memory is dropped", async () => {
        applyTokenResponse(ownerTokens);
        dropMemorySession();
        expect(getSessionSnapshot()).toBeNull();
        expect(getApiAccessToken()).toBeUndefined();
        expect(sessionStorage.getItem(refreshTokenStorageKey)).toBe(
            "mock-refresh-token",
        );

        const restored = await restoreSession();

        expect(restored?.userId).toBe(ownerTokens.userId);
        expect(getApiAccessToken()).toBe("mock-access-token-rotated");
        expect(sessionStorage.getItem(refreshTokenStorageKey)).toBe(
            "mock-refresh-token-rotated",
        );
    });

    it("clears local session state when refresh fails", async () => {
        applyTokenResponse(ownerTokens);
        dropMemorySession();
        server.use(
            http.post(`${runtimeConfig.apiBaseUrl}/api/auth/refresh`, () =>
                HttpResponse.json(
                    {
                        code: "UNAUTHORIZED",
                        detail: "Authentication is required.",
                        instance: "/api/auth/refresh",
                        requestId: "00000000-0000-4000-8000-000000000099",
                        status: 401,
                        title: "Authentication is required",
                        type: "about:blank",
                    },
                    { status: 401 },
                ),
            ),
        );

        const restored = await restoreSession();

        expect(restored).toBeNull();
        expect(getSessionSnapshot()).toBeNull();
        expect(getApiAccessToken()).toBeUndefined();
        expect(sessionStorage.getItem(refreshTokenStorageKey)).toBeNull();
    });

    it("does not invent a session when no refresh token exists", async () => {
        expect(isDevSignInBypassEnabled()).toBe(false);
        expect(await restoreSession()).toBeNull();
        expect(getSessionSnapshot()).toBeNull();
        expect(getApiAccessToken()).toBeUndefined();
    });

    it("clears local state on logout even when the server call fails", async () => {
        applyTokenResponse(ownerTokens);
        server.use(
            http.post(`${runtimeConfig.apiBaseUrl}/api/auth/logout`, () =>
                HttpResponse.json(
                    {
                        code: "INTERNAL_ERROR",
                        detail: "Request could not be completed.",
                        instance: "/api/auth/logout",
                        requestId: "00000000-0000-4000-8000-000000000099",
                        status: 500,
                        title: "Request could not be completed",
                        type: "about:blank",
                    },
                    { status: 500 },
                ),
            ),
        );

        await logoutSession();

        expect(getSessionSnapshot()).toBeNull();
        expect(getApiAccessToken()).toBeUndefined();
        expect(sessionStorage.getItem(refreshTokenStorageKey)).toBeNull();
    });
});
