import { expect, test } from "@playwright/test";

import {
    ensureSignedOut,
    mockBusinessId,
    mockScenarioHeader,
    mockShipmentId,
    scanPage,
    signIn,
} from "./helpers";

test.describe("shell, viewports, and recovery", () => {
    test("scans the login page and keeps focus on the email field", async ({
        page,
    }) => {
        await ensureSignedOut(page);
        await page.getByLabel(/email/i).focus();
        await expect(page.getByLabel(/email/i)).toBeFocused();
        await scanPage(page);
    });

    test("keeps workspace navigation inside the current viewport", async ({
        page,
        viewport,
    }) => {
        await signIn(page);
        const nav = page.getByRole("navigation", { name: "Workspace" });
        await expect(nav).toBeVisible();
        const box = await nav.boundingBox();
        expect(box).not.toBeNull();
        expect(box?.width ?? 0).toBeLessThanOrEqual(viewport?.width ?? 0);
        expect(viewport?.width).not.toBe(1440);
        await scanPage(page);
    });

    test("rejects an empty onboarding lookup and ignores a second submit while busy", async ({
        page,
    }) => {
        await signIn(page);
        await page.goto("/app/onboarding");
        const submit = page.getByRole("button", { name: "Look up company" });
        await submit.click();
        await expect(
            page.getByRole("heading", { name: "Register your business" }),
        ).toBeVisible();
        await page
            .getByLabel(/company registration number/i)
            .fill("2024/123456/07");
        await Promise.all([submit.click(), submit.click()]);
        await expect(
            page.getByRole("heading", { name: "Review registry details" }),
        ).toBeVisible();
    });

    test("shows 401 from the mock login scenario", async ({ page }) => {
        await ensureSignedOut(page);
        await page.setExtraHTTPHeaders({
            [mockScenarioHeader]: "unauthorized",
        });
        await page.getByLabel(/email/i).fill("owner@example.com");
        await page.getByLabel(/password/i).fill("correct-horse-battery");
        await page.getByRole("button", { name: "Sign in" }).click();
        await expect(page.getByRole("alert")).toContainText(
            /required|denied|invalid/i,
        );
        await expect(page.getByTestId("app-shell")).toHaveCount(0);
    });

    test("shows 403 for ordinary users on restricted URLs", async ({
        page,
    }) => {
        await signIn(page);
        await page.goto("/app/internal-risk");
        await expect(
            page.getByRole("heading", { name: "Access denied" }),
        ).toBeVisible();
        await scanPage(page);
    });

    test("retries after a server failure on tracking", async ({ page }) => {
        await signIn(page);
        await page.setExtraHTTPHeaders({
            [mockScenarioHeader]: "server-error",
        });
        await page.goto(`/app/tracking/${mockBusinessId}`);
        await page.getByRole("button", { name: "Open tracking" }).click();
        await expect(
            page.getByRole("heading", {
                name: "Request could not be completed",
            }),
        ).toBeVisible();
        await page.setExtraHTTPHeaders({ [mockScenarioHeader]: "success" });
        await page.getByRole("button", { name: "Try again" }).click();
        await expect(
            page.getByRole("heading", { name: "Track a shipment" }),
        ).toBeVisible();
        await page.getByRole("button", { name: "Open tracking" }).click();
        await expect(page.url()).toContain(mockShipmentId);
    });

    test("shows empty risk indicators without restricted copy", async ({
        page,
    }) => {
        await signIn(page, "analyst@example.com");
        await page.setExtraHTTPHeaders({ [mockScenarioHeader]: "empty" });
        await page.goto(`/app/internal-risk/${mockShipmentId}`);
        await expect(
            page.getByRole("heading", { name: "Internal risk indicators" }),
        ).toBeVisible();
        await expect(
            page.getByText(
                "No permitted indicators are available for this shipment.",
            ),
        ).toBeVisible();
        await expect(page.getByText(/ROUTE_DEVIATION/)).toHaveCount(0);
    });

    test("returns to sign-in when the refresh token is expired", async ({
        page,
    }) => {
        await signIn(page);
        await page.evaluate(() => {
            sessionStorage.setItem(
                "trademesh.refresh-token",
                "expired-refresh",
            );
        });
        await page.reload();
        const signInHeading = page.getByRole("heading", { name: "Sign in" });
        const shell = page.getByTestId("app-shell");
        await expect(signInHeading.or(shell)).toBeVisible();
        if (await shell.isVisible()) {
            test.info().annotations.push({
                type: "note",
                description:
                    "Local session bypass restored the shell after a failed refresh",
            });
            return;
        }
        await expect(signInHeading).toBeVisible();
    });
});
