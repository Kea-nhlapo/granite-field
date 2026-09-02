import { defineConfig, devices } from "@playwright/test";

const port = 4177;
const baseURL = `http://127.0.0.1:${port}`;

export default defineConfig({
    testDir: "./e2e",
    fullyParallel: false,
    forbidOnly: Boolean(process.env.CI),
    retries: process.env.CI ? 1 : 0,
    workers: 1,
    reporter: process.env.CI ? "github" : "list",
    outputDir: "test-results",
    timeout: 60_000,
    expect: { timeout: 15_000 },
    use: {
        baseURL,
        trace: "off",
        video: "off",
        screenshot: "only-on-failure",
        extraHTTPHeaders: {},
    },
    webServer: {
        command: `npx vite --host 127.0.0.1 --port ${port}`,
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
        env: {
            VITE_API_MODE: "mock",
            VITE_API_BASE_URL: "http://localhost:8080",
        },
    },
    projects: [
        {
            name: "desktop-chromium",
            use: {
                ...devices["Desktop Chrome"],
                viewport: { width: 1280, height: 800 },
            },
        },
        {
            name: "mobile-chromium",
            use: {
                ...devices["Pixel 7"],
                viewport: { width: 390, height: 844 },
                isMobile: true,
                hasTouch: true,
            },
        },
    ],
});
