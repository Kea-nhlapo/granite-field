import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_ROUTING !== "1")(
    "local backend routing",
    () => {
        it("calls generated routing and scoring operations", async () => {
            const {
                authLogin,
                routeScoringGet,
                routeScoringScore,
                routingCalculate,
                routingGet,
            } = await import("../../shared/api/app-api");
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
            expect(routingCalculate).toBeTypeOf("function");
            expect(routingGet).toBeTypeOf("function");
            expect(routeScoringScore).toBeTypeOf("function");
            expect(routeScoringGet).toBeTypeOf("function");
        });
    },
);
