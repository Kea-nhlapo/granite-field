import {
    authLogin,
    authLogout,
    authRefresh,
    authRegister,
} from "../../shared/api/app-api";
import { setApiAccessToken } from "../../shared/api/client";
import type {
    ApiProblem,
    RegisterRequest,
    TokenResponse,
} from "../../shared/api/generated";
import {
    clearRefreshToken,
    readRefreshToken,
    writeRefreshToken,
} from "./refresh-token-storage";
import { rolesFromList, type AppRole } from "./roles";

export type Session = {
    userId: string;
    roles: ReadonlySet<AppRole>;
    tokenType: string;
    expiresInSeconds: number;
};

const listeners = new Set<() => void>();

let currentSession: Session | null = null;
let refreshInFlight: Promise<Session | null> | undefined;

function notify() {
    for (const listener of listeners) {
        listener();
    }
}

export function subscribeSession(onStoreChange: () => void) {
    listeners.add(onStoreChange);
    return () => {
        listeners.delete(onStoreChange);
    };
}

export function getSessionSnapshot() {
    return currentSession;
}

export function dropMemorySession() {
    currentSession = null;
    setApiAccessToken(undefined);
    notify();
}

export function clearSession() {
    currentSession = null;
    setApiAccessToken(undefined);
    clearRefreshToken();
    notify();
}

export function applyTokenResponse(tokens: TokenResponse): Session | null {
    const userId = tokens.userId?.trim();
    const accessToken = tokens.accessToken?.trim();
    const refreshToken = tokens.refreshToken?.trim();

    if (!userId || !accessToken || !refreshToken) {
        clearSession();
        return null;
    }

    setApiAccessToken(accessToken);
    writeRefreshToken(refreshToken);
    currentSession = {
        expiresInSeconds: tokens.expiresInSeconds ?? 0,
        roles: rolesFromList(tokens.roles),
        tokenType: tokens.tokenType?.trim() || "Bearer",
        userId,
    };
    notify();
    return currentSession;
}

export async function loginWithPassword(
    email: string,
    password: string,
): Promise<{ session: Session | null; error?: ApiProblem }> {
    const result = await authLogin({
        body: { email, password },
    });

    if (result.error) {
        return { error: result.error as ApiProblem, session: null };
    }

    if (!result.data) {
        return {
            error: {
                code: "UNAUTHORIZED",
                detail: "Authentication is required.",
                instance: "/api/auth/login",
                requestId: "",
                status: 401,
                title: "Authentication is required",
                type: "about:blank",
            },
            session: null,
        };
    }

    return { session: applyTokenResponse(result.data) };
}

export async function registerWithPassword(
    email: string,
    password: string,
    accountType: RegisterRequest["accountType"],
): Promise<{ session: Session | null; error?: ApiProblem }> {
    const result = await authRegister({
        body: { accountType, email, password },
    });
    if (result.error) {
        return { error: result.error as ApiProblem, session: null };
    }
    if (!result.data) {
        return {
            error: {
                code: "REGISTRATION_FAILED",
                detail: "The account could not be created.",
                instance: "/api/auth/register",
                requestId: "",
                status: 500,
                title: "Account creation failed",
                type: "about:blank",
            },
            session: null,
        };
    }
    return { session: applyTokenResponse(result.data) };
}

async function refreshSessionOnce(): Promise<Session | null> {
    const refreshToken = readRefreshToken();
    if (!refreshToken) {
        clearSession();
        return null;
    }

    const result = await authRefresh({
        body: { refreshToken },
    });

    if (result.error || !result.data) {
        clearSession();
        return null;
    }

    return applyTokenResponse(result.data);
}

export function refreshSession(): Promise<Session | null> {
    if (!refreshInFlight) {
        refreshInFlight = refreshSessionOnce().finally(() => {
            refreshInFlight = undefined;
        });
    }

    return refreshInFlight;
}

export async function restoreSession(): Promise<Session | null> {
    if (currentSession) {
        return currentSession;
    }

    if (!readRefreshToken()) {
        return null;
    }

    return refreshSession();
}

export async function logoutSession(): Promise<void> {
    const refreshToken = readRefreshToken();

    try {
        if (refreshToken) {
            await authLogout({
                body: { refreshToken },
            });
        }
    } finally {
        clearSession();
    }
}
