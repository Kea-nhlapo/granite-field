import { describe, expect, it } from "vitest";

import { hasAnyRole, isAppRole, rolesFromList } from "./roles";

describe("application roles", () => {
    it("treats backend roles as a set of known values", () => {
        const roles = rolesFromList([
            "BUSINESS_OWNER",
            "SUPPLIER",
            "BUSINESS_OWNER",
            "not-a-role",
        ]);

        expect(roles.size).toBe(2);
        expect(roles.has("BUSINESS_OWNER")).toBe(true);
        expect(
            hasAnyRole(roles, ["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]),
        ).toBe(false);
        expect(hasAnyRole(roles, ["SUPPLIER"])).toBe(true);
        expect(isAppRole("DRIVER")).toBe(true);
        expect(isAppRole("BUSINESS")).toBe(false);
    });
});
