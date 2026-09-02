import { describe, expect, it } from "vitest";

import { runtimeConfig } from "./runtime-config";

describe("runtimeConfig", () => {
    it("defaults API mode to live so mocks are an explicit choice", () => {
        expect(runtimeConfig.apiMode).toBe("live");
        expect(runtimeConfig.apiBaseUrl).toBe("http://localhost:8080");
    });
});
