import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";
import { defineConfig } from "vitest/config";

const tabsterEsm = fileURLToPath(
    new URL("./node_modules/tabster/dist/esm/index.js", import.meta.url),
);

export default defineConfig({
    plugins: [react()],
    resolve: {
        alias: {
            tabster: tabsterEsm,
        },
    },
    optimizeDeps: {
        holdUntilCrawlEnd: false,
        include: [
            "scheduler",
            "tabster",
            "react",
            "react-dom",
            "@fluentui/react-components",
        ],
    },
    test: {
        environment: "jsdom",
        setupFiles: ["./src/test/setup.ts"],
        restoreMocks: true,
        alias: {
            tabster: tabsterEsm,
        },
        server: {
            deps: {
                inline: [/@fluentui\//, /@griffel\//],
            },
        },
    },
});
