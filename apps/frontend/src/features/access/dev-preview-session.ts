import type { TokenResponse } from "../../shared/api/generated";

export const devPreviewTokens: TokenResponse = {
    userId: "00000000-0000-4000-8000-000000000010",
    tokenType: "Bearer",
    accessToken: "mock-access-token",
    expiresInSeconds: 900,
    refreshToken: "mock-refresh-token",
    roles: ["BUSINESS_OWNER"],
};

export function isDevSignInBypassEnabled(): boolean {
    return import.meta.env.DEV && import.meta.env.MODE === "development";
}
