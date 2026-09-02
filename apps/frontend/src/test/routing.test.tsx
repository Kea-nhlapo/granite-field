import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("routing", () => {
    it("compares routes and refuses to mark missing data as safe", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: /Routes/ }));
        expect(screen.getByText("Route summary")).toBeInTheDocument();
        expect(screen.getByText(/not presented as safe/)).toBeInTheDocument();
        await user.click(screen.getByRole("button", { name: "Select A" }));
        expect(
            screen.getByText(/Selected N1 \+ R21 Bypass/),
        ).toBeInTheDocument();
    });
});
