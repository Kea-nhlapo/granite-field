import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("handover", () => {
    it("confirms a QR handover with an accessible fallback", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: "Track →" }));
        await user.click(
            screen.getByRole("button", { name: "Open handover QR" }),
        );
        await user.click(
            screen.getByRole("button", { name: "Request short-lived QR" }),
        );
        expect(screen.getByText("SB-2026-9901")).toBeInTheDocument();
        await user.click(
            screen.getByRole("checkbox", {
                name: /Camera unavailable/,
            }),
        );
        await user.click(
            screen.getByRole("button", { name: "Confirm handover" }),
        );
        expect(
            await screen.findByText(/Server-confirmed receipt REF-F9K2/),
        ).toBeInTheDocument();
    });
});
