import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_TRACKING !== "1")(
    "local backend tracking",
    () => {
        it("calls generated shipment and telemetry operations", async () => {
            const { authLogin, shipmentGet, telemetryHistory, telemetryLive } =
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
            expect(shipmentGet).toBeTypeOf("function");
            expect(telemetryHistory).toBeTypeOf("function");
            expect(telemetryLive).toBeTypeOf("function");
        });
    },
);
