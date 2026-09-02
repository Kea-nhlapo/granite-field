import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("procurement", () => {
    it("confirms an order snapshot without duplicating retries", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: "Request Stock" }));
        await user.click(screen.getByRole("button", { name: "Request quote" }));
        expect(await screen.findByText(/Quote QUO-1001/)).toBeInTheDocument();
        expect(screen.getByText(/ZAR 14480.00/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Confirm order" }));
        expect(
            await screen.findByText(/Order ORD-2026-9012 confirmed/),
        ).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Confirm order" }));
        expect(
            screen.getAllByText(/Order ORD-2026-9012 confirmed/),
        ).toHaveLength(1);
    });
});
