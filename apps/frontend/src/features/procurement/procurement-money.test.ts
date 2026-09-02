import { describe, expect, it } from "vitest";

import { moneySummaryLines, moneyText } from "./procurement-money";

describe("procurement money display", () => {
    it("renders backend amounts without adding them in the client", () => {
        expect(moneyText("ZAR", 1050)).toBe("ZAR 1050");
        expect(moneyText("ZAR", 23)).toBe("ZAR 23");
        expect(moneyText("ZAR", 1073)).toBe("ZAR 1073");
        expect(
            moneySummaryLines({
                currency: "ZAR",
                subtotal: 1050,
                taxAmount: 23,
                total: 1073,
            }),
        ).toEqual({
            currency: "ZAR",
            subtotal: "ZAR 1050",
            tax: "ZAR 23",
            total: "ZAR 1073",
        });
    });
});
