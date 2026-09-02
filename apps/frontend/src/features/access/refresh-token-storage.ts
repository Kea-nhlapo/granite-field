export const refreshTokenStorageKey = "trademesh.refresh-token";

export function readRefreshToken(): string | undefined {
    if (typeof sessionStorage === "undefined") {
        return undefined;
    }

    return sessionStorage.getItem(refreshTokenStorageKey)?.trim() || undefined;
}

export function writeRefreshToken(token: string) {
    sessionStorage.setItem(refreshTokenStorageKey, token);
}

export function clearRefreshToken() {
    if (typeof sessionStorage === "undefined") {
        return;
    }

    sessionStorage.removeItem(refreshTokenStorageKey);
}

export function localStorageHasRefreshToken(): boolean {
    if (typeof localStorage === "undefined") {
        return false;
    }

    return localStorage.getItem(refreshTokenStorageKey) !== null;
}
