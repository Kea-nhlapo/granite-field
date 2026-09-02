import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, expect, test } from "vitest";

import { notificationPreferenceGet, trustPublicSummary } from "./app-api";
import { setApiAccessToken } from "./client";
import { handlers } from "./mocks/handlers";
import { runtimeConfig } from "../lib/runtime-config";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

test("the generated SDK uses the configured API host", async () => {
    const result = await trustPublicSummary({
        path: { businessId: "00000000-0000-4000-8000-000000000001" },
    });

    expect(result.data?.completedTransactionCount).toBe(97);
    expect(result.error).toBeUndefined();
});

test("the generated SDK adds bearer auth to protected operations", async () => {
    let authorization: string | null = null;
    server.use(
        http.get(
            `${runtimeConfig.apiBaseUrl}/api/notification-preferences`,
            ({ request }) => {
                authorization = request.headers.get("Authorization");
                return HttpResponse.json({ preferences: [] });
            },
        ),
    );
    setApiAccessToken("test-access-token");

    const result = await notificationPreferenceGet();

    expect(result.error).toBeUndefined();
    expect(authorization).toBe("Bearer test-access-token");
});
