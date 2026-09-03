const localApiBaseUrl = "http://localhost:8080";

type BrowserLocation = Pick<Location, "hostname" | "origin">;

export function resolveDefaultApiBaseUrl(
    location: BrowserLocation | undefined,
): string {
    if (!location) {
        return localApiBaseUrl;
    }

    const isLocal =
        location.hostname === "localhost" || location.hostname === "127.0.0.1";

    return isLocal ? localApiBaseUrl : location.origin;
}

const defaultApiBaseUrl = resolveDefaultApiBaseUrl(
    typeof window === "undefined" ? undefined : window.location,
);

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
