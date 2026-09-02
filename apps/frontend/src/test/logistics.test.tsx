import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("logistics", () => {
    it("explains consolidation, trade-offs, and hard failures", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: /Routes/ }));
        await user.click(screen.getByRole("button", { name: "Consolidation" }));
        expect(screen.getByText(/Combined 950 kg/)).toBeInTheDocument();
        expect(screen.getByText(/Blocked/)).toBeInTheDocument();
        expect(screen.getByText(/Trade-off/)).toBeInTheDocument();
        expect(screen.getByText(/not named in exclusions/)).toBeInTheDocument();
    });
});
