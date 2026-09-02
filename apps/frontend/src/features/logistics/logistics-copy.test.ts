import { describe, expect, it } from "vitest";

import { exclusionReasonText } from "./logistics-copy";

describe("logistics exclusion copy", () => {
    it("keeps exclusion reasons free of other-business identity", () => {
        expect(exclusionReasonText("SUPPLIER_OR_PICKUP_MISMATCH")).toBe(
            "Pickup or supplier is not compatible with this group.",
        );
        expect(exclusionReasonText("CARGO_NOT_COMPATIBLE")).not.toMatch(
            /Mahlako|Pty|Ltd/i,
        );
    });
});
