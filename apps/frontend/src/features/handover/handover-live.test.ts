import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_HANDOVER !== "1")(
    "local backend handover",
    () => {
        it("calls generated handover operations", async () => {
            const { authLogin, handoverConfirm, handoverGet, handoverIssue } =
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
            expect(handoverIssue).toBeTypeOf("function");
            expect(handoverGet).toBeTypeOf("function");
            expect(handoverConfirm).toBeTypeOf("function");
        });
    },
);
