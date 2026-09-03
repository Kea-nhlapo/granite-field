import { describe, expect, it } from "vitest";

import { resolveDefaultApiBaseUrl, runtimeConfig } from "./runtime-config";

describe("runtimeConfig", () => {
    it("defaults API mode to live so mocks are an explicit choice", () => {
        expect(runtimeConfig.apiMode).toBe("live");
        expect(runtimeConfig.apiBaseUrl).toBe("http://localhost:8080");
    });

    it("uses the deployed site's origin outside local development", () => {
        expect(
            resolveDefaultApiBaseUrl({
                hostname: "trademesh.example",
                origin: "https://trademesh.example",
            }),
        ).toBe("https://trademesh.example");
    });
});
