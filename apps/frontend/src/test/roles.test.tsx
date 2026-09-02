import { cleanup, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";

import { mocks } from "../shared/api/mocks";
import { renderApp } from "./render-app";

afterEach(() => {
    mocks.reset();
});

describe("restricted partner views", () => {
    it("keeps internal-risk views away from ordinary users", async () => {
        mocks.login("naledi@khanyisa.co.za", "stockroom");
        renderApp("/app/risk");
        expect(
            await screen.findByRole("heading", {
                name: "You cannot open this area",
            }),
        ).toBeInTheDocument();
    });

    it("shows permitted risk evidence to an internal-risk user", async () => {
        mocks.login("risk@internal.example", "stockroom");
        renderApp("/app/risk");
        expect(
            await screen.findByText("Driver phone changed 3× in 30 days"),
        ).toBeInTheDocument();
        expect(screen.getByText(/device-registry/)).toBeInTheDocument();
    });

    it("shows insurer evidence only to the insurer role", async () => {
        mocks.login("insurer@cover.example", "stockroom");
        renderApp("/app/insurance");
        expect(await screen.findByText(/Collection QR/)).toBeInTheDocument();
        cleanup();
        mocks.reset();
        mocks.login("naledi@khanyisa.co.za", "stockroom");
        renderApp("/app/insurance");
        expect(
            await screen.findByRole("heading", {
                name: "You cannot open this area",
            }),
        ).toBeInTheDocument();
    });
});
