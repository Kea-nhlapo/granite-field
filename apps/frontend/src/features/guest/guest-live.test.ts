import { describe, expect, it } from "vitest";

describe.skipIf(import.meta.env.VITE_LIVE_GUEST !== "1")(
    "local backend guest invitation",
    () => {
        it("views a guest invitation through the generated client", async () => {
            const { supplierViewGuest } =
                await import("../../shared/api/app-api");
            const result = await supplierViewGuest({
                path: { token: import.meta.env.VITE_LIVE_GUEST_TOKEN ?? "" },
            });
            expect(result.response?.status).toBeDefined();
            expect(
                JSON.stringify(result.data ?? result.error ?? {}),
            ).not.toMatch(/invitationToken/i);
        });
    },
);
