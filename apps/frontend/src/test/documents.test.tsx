import { screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { signInAs } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("document review", () => {
    it("reviews document mismatches without accusing anyone", async () => {
        const { user } = await signInAs();
        await screen.findByRole("heading", { name: /Mama Nkosi/i });
        await user.click(screen.getByRole("button", { name: /Orders/ }));
        await user.click(screen.getByRole("button", { name: "+ Upload" }));
        await user.click(
            screen.getByRole("button", { name: "Upload invoice" }),
        );
        expect(
            await screen.findByText("Mismatch evidence"),
        ).toBeInTheDocument();
        expect(screen.getByText(/not an accusation/)).toBeInTheDocument();
        await user.click(
            screen.getAllByRole("button", { name: "Correct line" })[0]!,
        );
        expect(
            screen.getByText(/Original extracted value: 50/),
        ).toBeInTheDocument();
        expect(screen.getByText(/Current value: 30/)).toBeInTheDocument();
    });
});
