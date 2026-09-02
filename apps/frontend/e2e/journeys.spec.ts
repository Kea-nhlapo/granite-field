import { expect, test } from "@playwright/test";

import {
    ensureSignedOut,
    mockBusinessId,
    mockQuoteId,
    mockShipmentId,
    scanPage,
    signIn,
} from "./helpers";

test.describe("browser journeys", () => {
    test("signs in, restores after reload, and supports history", async ({
        page,
    }) => {
        await signIn(page);
        await page
            .getByRole("navigation", { name: "Workspace" })
            .getByRole("link", { name: "Settings" })
            .click();
        await expect(
            page.getByRole("heading", { name: "Settings" }),
        ).toBeVisible();
        await page.reload();
        await expect(
            page.getByRole("heading", { name: "Settings" }),
        ).toBeVisible();
        await page.goBack();
        await expect(page.getByRole("heading", { name: "Home" })).toBeVisible();
        await page.goForward();
        await expect(
            page.getByRole("heading", { name: "Settings" }),
        ).toBeVisible();
        await scanPage(page);
    });

    test("runs onboarding lookup and review", async ({ page }) => {
        await signIn(page);
        await page.goto("/app/onboarding");
        await page
            .getByLabel(/company registration number/i)
            .fill("2024/123456/07");
        await page.getByRole("button", { name: "Look up company" }).click();
        await expect(
            page.getByRole("heading", { name: "Review registry details" }),
        ).toBeVisible();
        await scanPage(page);
    });

    test("opens a guest quote invitation", async ({ page }) => {
        await page.goto("/supplier-invitations/guest/invitepath");
        await expect(
            page.getByRole("heading", { name: "Reply to this quote request" }),
        ).toBeVisible();
        await scanPage(page);
    });

    test("creates a request, confirms a quote, and shows the order", async ({
        page,
    }) => {
        await signIn(page);
        await page.goto(`/app/procurement/${mockBusinessId}`);
        await expect(
            page.getByRole("heading", { name: "Create a product request" }),
        ).toBeVisible();
        await page.getByLabel(/destination/i).fill("Tembisa, Gauteng");
        await page
            .getByLabel(/delivery window start/i)
            .fill("2026-10-01T08:00");
        await page.getByLabel(/delivery window end/i).fill("2026-10-02T08:00");
        await page.getByLabel(/description/i).fill("20 cases soft drinks");
        await page.getByLabel(/quantity/i).fill("20");
        await page.getByLabel(/^unit$/i).selectOption("CASE");
        await page.getByRole("button", { name: "Add line" }).click();
        await page
            .getByLabel(/description/i)
            .nth(1)
            .fill("10 bags maize meal");
        await page
            .getByLabel(/quantity/i)
            .nth(1)
            .fill("10");
        await page
            .getByLabel(/^unit$/i)
            .nth(1)
            .selectOption("EACH");
        await page.getByRole("button", { name: "Submit request" }).click();
        await expect(
            page.getByRole("heading", { name: "Supplier quote" }),
        ).toBeVisible();
        expect(page.url()).toContain(`/quotes/${mockQuoteId}`);
        await page.getByRole("button", { name: "Review confirmation" }).click();
        await page.getByRole("button", { name: "Confirm quote" }).click();
        await expect(
            page.getByRole("heading", { name: "Confirmed order" }),
        ).toBeVisible();
        await scanPage(page);
    });

    test("compares routes", async ({ page }) => {
        await signIn(page);
        await page.goto(`/app/routing/${mockBusinessId}`);
        await page.getByRole("button", { name: "Calculate and score" }).click();
        await expect(
            page.getByRole("heading", { name: "Route comparison" }),
        ).toBeVisible();
        await scanPage(page);
    });

    test("tracks a shipment", async ({ page }) => {
        await signIn(page);
        await page.goto(`/app/tracking/${mockBusinessId}`);
        await page.getByRole("button", { name: "Open tracking" }).click();
        await expect(
            page.getByRole("heading", { name: "Shipment tracking" }),
        ).toBeVisible();
        expect(page.url()).toContain(mockShipmentId);
        await scanPage(page);
    });

    test("issues a handover challenge", async ({ page }) => {
        await signIn(page);
        await page.goto(`/app/handover/${mockBusinessId}`);
        await page.getByRole("button", { name: "Issue challenge" }).click();
        await expect(
            page.getByRole("heading", { name: "COLLECTION challenge" }),
        ).toBeVisible();
        await expect(page.getByText(/signed payload/i)).toHaveCount(0);
        await scanPage(page);
    });

    test("opens customer trust from home", async ({ page }) => {
        await signIn(page);
        await page.getByRole("link", { name: "Trust and Risk" }).click();
        await expect(
            page.getByRole("heading", { name: "Trust and Risk" }),
        ).toBeVisible();
        await expect(page.getByText(/out of 100/i)).toBeVisible();
        await scanPage(page);
    });

    test("saves settings and switches look", async ({ page }) => {
        await signIn(page);
        await page
            .getByRole("navigation", { name: "Workspace" })
            .getByRole("link", { name: "Settings" })
            .click();
        await expect(
            page.getByRole("heading", { name: "Settings" }),
        ).toBeVisible();
        await page.getByLabel(/^email$/i).fill("lerato@example.com");
        await page.getByLabel(/phone number/i).fill("0821234567");
        await page.getByRole("button", { name: "Save" }).click();
        await expect(page.getByText("Saved")).toBeVisible();
        await page.getByLabel("Dark").click();
        await expect
            .poll(async () =>
                page.evaluate(() => document.documentElement.dataset.theme),
            )
            .toBe("dark");
        await scanPage(page);
    });

    test("shows customer sign up", async ({ page }) => {
        await ensureSignedOut(page);
        await page.getByRole("link", { name: "Create an account" }).click();
        await expect(
            page.getByRole("heading", { name: "Create an account" }),
        ).toBeVisible();
        await expect(page.getByLabel(/first name/i)).toBeVisible();
        await scanPage(page);
    });
});
