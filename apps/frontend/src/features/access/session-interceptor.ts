import { apiClient, getApiAccessToken } from "../../shared/api/client";
import { refreshSession } from "./session";

const retryHeader = "X-Auth-Retry";

let installed = false;

function isAuthRequest(url: string) {
    try {
        const path = new URL(url, "http://localhost").pathname;
        return (
            path === "/api/auth/login" ||
            path === "/api/auth/refresh" ||
            path === "/api/auth/logout"
        );
    } catch {
        return false;
    }
}

export function installSessionRefreshInterceptor() {
    if (installed) {
        return;
    }

    installed = true;

    apiClient.interceptors.response.use(async (response, _request, options) => {
        if (response.status === 403) {
            return response;
        }

        if (response.status !== 401) {
            return response;
        }

        const requestUrl = options.url ?? _request.url;
        if (isAuthRequest(_request.url) || isAuthRequest(String(requestUrl))) {
            return response;
        }

        if (
            options.headers.get(retryHeader) === "1" ||
            _request.headers.get(retryHeader) === "1"
        ) {
            return response;
        }

        const session = await refreshSession();
        if (!session) {
            return response;
        }

        const headers = new Headers(options.headers);
        const accessToken = getApiAccessToken();
        if (accessToken) {
            headers.set("Authorization", `Bearer ${accessToken}`);
        }
        headers.set(retryHeader, "1");

        const fetchImpl = options.fetch ?? globalThis.fetch;
        return fetchImpl(_request.url, {
            body: options.serializedBody,
            headers,
            method: options.method ?? _request.method,
        });
    });
}
