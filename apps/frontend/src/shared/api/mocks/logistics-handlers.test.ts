import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import {
    capacityMatchingSearch,
    demandAggregationGet,
    demandAggregationSuggest,
} from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, resetLogisticsMocks } from "./handlers";
import { mockCapacitySearchId, mockSuggestionId } from "./logistics-handlers";
import { mockBusinessId } from "./onboarding-handlers";
import { mockOrderId } from "./procurement-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetLogisticsMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("logistics mocks", () => {
    test("suggest and search use generated aggregation and capacity operations", async () => {
        setApiAccessToken("mock-access-token");
        const suggested = await demandAggregationSuggest({
            body: {
                requestId: "00000000-0000-4000-8000-000000000090",
                anchorOrderId: mockOrderId,
            },
            path: { businessId: mockBusinessId },
        });
        expect(suggested.data?.suggestionId).toBe(mockSuggestionId);
        expect(suggested.data?.includedOrderCount).toBe(2);

        const loaded = await demandAggregationGet({
            path: {
                businessId: mockBusinessId,
                suggestionId: mockSuggestionId,
            },
        });
        expect(loaded.data?.orders?.some((order) => order.included)).toBe(true);

        const searched = await capacityMatchingSearch({
            body: {
                requestId: "00000000-0000-4000-8000-000000000091",
                demandGroupSuggestionId: mockSuggestionId,
                requiredCapacity: { weightKg: 80, volumeCubicMetres: 6 },
                cargoTraits: ["DRY_GOODS", "FOOD_GRADE"],
            },
            path: { businessId: mockBusinessId },
        });
        expect(searched.response?.status).toBe(201);
        expect(searched.data?.searchId).toBe(mockCapacitySearchId);
        expect(searched.data?.status).toBe("MATCHED");
    });
});
