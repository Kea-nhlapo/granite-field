import { describe, expect, it } from "vitest";

import { rolesFromList } from "../access/roles";
import {
    canRequestPreciseTelemetry,
    displayCoordinate,
} from "./tracking-location";

describe("tracking location precision", () => {
    it("allows precise telemetry only for authorized business roles", () => {
        expect(
            canRequestPreciseTelemetry(rolesFromList(["BUSINESS_OWNER"])),
        ).toBe(true);
        expect(
            canRequestPreciseTelemetry(
                rolesFromList(["INTERNAL_RISK_ANALYST"]),
            ),
        ).toBe(false);
        expect(displayCoordinate(-26.1044, true)).toBe("-26.1044");
        expect(displayCoordinate(-26.1044, false)).toBe("-26.1");
    });
});
