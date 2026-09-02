import { defineConfig } from "@hey-api/openapi-ts";

export default defineConfig({
    input: "../../packages/api-contracts/openapi/trademesh-v1.json",
    output: {
        path: "src/shared/api/generated",
        postProcess: ["oxlint", "prettier"],
    },
    plugins: ["@hey-api/client-fetch", "@hey-api/sdk"],
});
