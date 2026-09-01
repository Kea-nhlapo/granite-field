const defaultApiBaseUrl = "http://localhost:8080";

function removeTrailingSlash(value: string) {
    return value.replace(/\/$/, "");
}

export const runtimeConfig = Object.freeze({
    apiBaseUrl: removeTrailingSlash(
        import.meta.env.VITE_API_BASE_URL?.trim() || defaultApiBaseUrl,
    ),
});
