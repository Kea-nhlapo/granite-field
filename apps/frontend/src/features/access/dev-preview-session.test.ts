import { afterEach, describe, expect, it, vi } from "vitest";

import { getApiAccessToken } from "../../shared/api/client";
import * as devPreviewSession from "./dev-preview-session";
import { refreshTokenStorageKey } from "./refresh-token-storage";
import { clearSession, getSessionSnapshot, restoreSession } from "./session";

describe("dev sign-in bypass", () => {
    afterEach(() => {
        vi.restoreAllMocks();
        clearSession();
    });

    it("enters the workspace session without a stored refresh token", async () => {
        vi.spyOn(devPreviewSession, "isDevSignInBypassEnabled").mockReturnValue(
            true,
        );

        const restored = await restoreSession();

        expect(restored?.userId).toBe(
            devPreviewSession.devPreviewTokens.userId,
        );
        expect(restored?.roles.has("BUSINESS_OWNER")).toBe(true);
        expect(getSessionSnapshot()?.userId).toBe(restored?.userId);
        expect(getApiAccessToken()).toBe(
            devPreviewSession.devPreviewTokens.accessToken,
        );
        expect(sessionStorage.getItem(refreshTokenStorageKey)).toBe(
            devPreviewSession.devPreviewTokens.refreshToken,
        );
    });
});
