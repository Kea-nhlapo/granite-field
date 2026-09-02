import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("business onboarding", () => {
    it("onboards a new business from registry lookup", async () => {
        const { user } = await signInAs("new@khanyisa.co.za");
        expect(
            await screen.findByLabelText("Registration number"),
        ).toBeInTheDocument();
        await user.type(
            screen.getByLabelText("Registration number"),
            "2024/003821/07",
        );
        await user.click(screen.getByRole("button", { name: "Look up" }));
        expect(
            await screen.findByText(/Unconfirmed registry values/),
        ).toBeInTheDocument();
        await user.click(
            screen.getByRole("button", { name: "Accept and continue" }),
        );
        expect(
            await screen.findByRole("heading", { name: /Mama Nkosi/i }),
        ).toBeInTheDocument();
    });

    it("shows lookup errors without putting the number on the URL", async () => {
        const { user } = await signInAs("new@khanyisa.co.za");
        await screen.findByLabelText("Registration number");
        await user.type(
            screen.getByLabelText("Registration number"),
            "missing",
        );
        await user.click(screen.getByRole("button", { name: "Look up" }));
        expect(await screen.findByRole("alert")).toHaveTextContent(
            "No business matched that registration number.",
        );
        expect(window.location.href).not.toContain("missing");
    });
});
