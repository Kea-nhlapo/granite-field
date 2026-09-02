import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_PROCUREMENT !== "1")(
    "local backend procurement",
    () => {
        it("creates a request and confirms a quote through the generated client", async () => {
            const {
                authLogin,
                procurementConfirmQuote,
                procurementCreateRequest,
                procurementGetOrder,
                procurementGetQuote,
                procurementGetRequest,
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
            expect(procurementCreateRequest).toBeTypeOf("function");
            expect(procurementGetRequest).toBeTypeOf("function");
            expect(procurementGetQuote).toBeTypeOf("function");
            expect(procurementConfirmQuote).toBeTypeOf("function");
            expect(procurementGetOrder).toBeTypeOf("function");
        });
    },
);
