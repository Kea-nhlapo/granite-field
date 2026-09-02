import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { api } from "../shared/api/client";
import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
    vi.unstubAllEnvs();
    vi.unstubAllGlobals();
});

describe("frontend quality", () => {
    it("posts credentials to the local backend when mocks are off", async () => {
        vi.stubEnv("VITE_USE_MOCKS", "false");
        const session = {
            id: "sess_live",
            email: "naledi@khanyisa.co.za",
            role: "BUSINESS",
            displayName: "Mama Nkosi Spaza Supply",
            onboardingComplete: true,
        };
        const fetchMock = vi
            .fn()
            .mockResolvedValueOnce(new Response(null, { status: 204 }))
            .mockResolvedValueOnce(
                new Response(JSON.stringify(session), {
                    status: 200,
                    headers: { "Content-Type": "application/json" },
                }),
            );
        vi.stubGlobal("fetch", fetchMock);
        await api.login("naledi@khanyisa.co.za", "stockroom");
        expect(fetchMock).toHaveBeenCalled();
        const first = fetchMock.mock.calls[0] as [string, RequestInit];
        expect(first[0]).toContain("/auth/login");
        expect(first[1]?.credentials).toBe("include");
    });

    it("keeps a mobile stage at desktop width", async () => {
        Object.defineProperty(window, "innerWidth", {
            configurable: true,
            value: 1440,
        });
        await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        expect(document.querySelector(".mobile-stage")).not.toBeNull();
    });
});
