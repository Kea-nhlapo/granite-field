import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_ONBOARDING !== "1")(
    "local backend onboarding",
    () => {
        it("starts registered onboarding through the generated client", async () => {
            const { authLogin, businessStartRegisteredOnboarding } =
                await import("../../shared/api/app-api");
            const { setApiAccessToken } =
                await import("../../shared/api/client");
            const login = await authLogin({
                body: {
                    email: "owner@example.com",
                    password: "correct-horse",
                },
            });
            expect(login.error).toBeUndefined();
            setApiAccessToken(login.data?.accessToken);

            const started = await businessStartRegisteredOnboarding({
                body: {
                    registrationNumber: ["2024", "123456", "07"].join("/"),
                },
            });
            expect(started.error).toBeUndefined();
            expect(started.data?.onboardingId).toBeTruthy();
            expect(started.response?.url).not.toContain("123456");
        });
    },
);
