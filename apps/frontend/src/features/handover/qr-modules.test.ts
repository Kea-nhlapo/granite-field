import { describe, expect, it } from "vitest";

import { qrModules } from "./qr-modules";

describe("qr modules", () => {
    it("encodes the payload as a matrix with finder patterns", () => {
        const first = qrModules("tmh_one");
        const second = qrModules("tmh_two");
        expect(first.length).toBe(29);
        expect(first[0]?.[0]).toBe(true);
        expect(first.join("|")).not.toBe(second.join("|"));
    });
});
