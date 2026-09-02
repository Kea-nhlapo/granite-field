import AxeBuilder from "@axe-core/playwright";
import { expect, type Page } from "@playwright/test";

export const mockBusinessId = "00000000-0000-4000-8000-000000000022";
export const mockQuoteId = "00000000-0000-4000-8000-000000000051";
export const mockShipmentId = "00000000-0000-4000-8000-000000000091";
export const mockScenarioHeader = "X-Mock-Scenario";

export async function ensureSignedOut(page: Page) {
    await page.goto("/login");
    const shell = page.getByTestId("app-shell");
    const heading = page.getByRole("heading", { name: "Sign in" });
    await expect(shell.or(heading)).toBeVisible();
    if (await shell.isVisible()) {
        await page.getByRole("button", { name: "Sign out" }).click();
    }
    await expect(heading).toBeVisible();
}

export async function signIn(
    page: Page,
    email = "owner@example.com",
    password = "correct-horse-battery",
) {
    await ensureSignedOut(page);
    await page.getByLabel(/email/i).fill(email);
    await page.getByLabel(/password/i).fill(password);
    await page.getByRole("button", { name: "Sign in" }).click();
    await expect(page.getByTestId("app-shell")).toBeVisible();
    await expect(
        page.getByRole("heading", { name: "Workspace" }),
    ).toBeVisible();
}

export async function scanPage(page: Page) {
    const results = await new AxeBuilder({ page })
        .withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"])
        .analyze();
    expect(results.violations, formatViolations(results.violations)).toEqual(
        [],
    );
}

function formatViolations(
    violations: { id: string; help: string; nodes: { html: string }[] }[],
) {
    return violations
        .map(
            (violation) =>
                `${violation.id}: ${violation.help} (${violation.nodes.length})`,
        )
        .join("\n");
}
