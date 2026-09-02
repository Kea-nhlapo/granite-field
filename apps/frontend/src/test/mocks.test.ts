import { describe, expect, it } from "vitest";

import { ApiError } from "../shared/api/errors";
import { formatMoney } from "../shared/api/generated";
import { mocks } from "../shared/api/mocks";

describe("contract mocks", () => {
    it("covers success, empty, validation, forbidden, expired, and server errors", () => {
        mocks.reset();
        expect(() => mocks.getSession()).toThrow(ApiError);
        mocks.login("naledi@khanyisa.co.za", "stockroom");
        expect(mocks.getSession().role).toBe("BUSINESS");
        expect(() => mocks.lookupBusiness("")).toThrow(/registration number/);
        expect(() => mocks.getQuote("expired")).toThrow(/no longer valid/);
        expect(() => mocks.getRiskCase()).toThrow(/do not have access/);
        mocks.setCapacityEmpty(true);
        expect(mocks.getCapacityMatches().matches).toHaveLength(0);
        mocks.setHandoverError("SERVER_ERROR");
        expect(() =>
            mocks.confirmHandover({
                challengeId: "ch-1",
                quantity: "1",
                fallback: true,
            }),
        ).toThrow(/offline/);
        expect(formatMoney({ currency: "ZAR", minor: 1448000 })).toBe(
            "ZAR 14480.00",
        );
    });
});
