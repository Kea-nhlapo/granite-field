const defaultApiBaseUrl = "http://localhost:8080";

export type ApiMode = "live" | "mock";

function removeTrailingSlash(value: string) {
    return value.replace(/\/$/, "");
}

function apiMode(value: string | undefined): ApiMode {
    return value?.trim().toLowerCase() === "mock" ? "mock" : "live";
}

export const runtimeConfig = Object.freeze({
    apiBaseUrl: removeTrailingSlash(
        import.meta.env.VITE_API_BASE_URL?.trim() || defaultApiBaseUrl,
    ),
    apiMode: apiMode(import.meta.env.VITE_API_MODE),
});
