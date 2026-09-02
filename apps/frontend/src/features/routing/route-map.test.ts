import { describe, expect, it } from "vitest";

import { polylinePoints } from "./route-map";

describe("route map", () => {
    it("projects geometry into an svg polyline", () => {
        expect(
            polylinePoints([
                { latitude: -26, longitude: 28 },
                { latitude: -26.1, longitude: 28.2 },
            ]),
        ).toContain(",");
    });
});
