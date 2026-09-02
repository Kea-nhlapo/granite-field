import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { renderApp } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("guest supplier invitation", () => {
    it("lets a guest submit a quote without entering the app", async () => {
        const { user } = renderApp("/invite/SB-INV-7XK9M2");
        await user.click(
            await screen.findByRole("button", { name: "Submit quote" }),
        );
        expect(screen.getByText(/Quote QUO-1001 received/)).toBeInTheDocument();
        expect(document.body.textContent).not.toContain("SB-INV-7XK9M2");
    });

    it("blocks expired invitations", async () => {
        renderApp("/invite/expired");
        expect(
            await screen.findByText(/this invitation is expired/i),
        ).toBeInTheDocument();
    });
});
