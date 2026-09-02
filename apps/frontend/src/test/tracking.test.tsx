import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("shipment tracking", () => {
    it("tracks a shipment with cautious deviation language", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: "Track →" }));
        expect(screen.getByText(/Approved path/)).toBeInTheDocument();
        expect(screen.getByText(/Actual path/)).toBeInTheDocument();
        expect(
            screen.getByText(/Possible route deviation/),
        ).toBeInTheDocument();
        expect(screen.getByText(/Approximate area/)).toBeInTheDocument();
    });
});
