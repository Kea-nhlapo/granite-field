import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_LOGISTICS !== "1")(
    "local backend logistics",
    () => {
        it("calls generated aggregation and capacity operations", async () => {
            const {
                authLogin,
                capacityMatchingGet,
                capacityMatchingSearch,
                demandAggregationGet,
                demandAggregationSuggest,
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
            expect(demandAggregationSuggest).toBeTypeOf("function");
            expect(demandAggregationGet).toBeTypeOf("function");
            expect(capacityMatchingSearch).toBeTypeOf("function");
            expect(capacityMatchingGet).toBeTypeOf("function");
        });
    },
);
