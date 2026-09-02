const defaultApiBaseUrl = "http://localhost:8080";

function removeTrailingSlash(value: string) {
    return value.replace(/\/$/, "");
}

export function isMockApiEnabled() {
    return import.meta.env.VITE_USE_MOCKS !== "false";
}

export const runtimeConfig = Object.freeze({
    apiBaseUrl: removeTrailingSlash(
        import.meta.env.VITE_API_BASE_URL?.trim() || defaultApiBaseUrl,
    ),
});
