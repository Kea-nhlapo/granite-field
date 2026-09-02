import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { http, HttpResponse } from "msw";
import { setupServer } from "msw/node";
import { createMemoryRouter, RouterProvider } from "react-router-dom";
import { afterAll, afterEach, beforeAll, describe, expect, it } from "vitest";

import { appRoutes } from "../../app/app-routes";
import { FluentAppProvider } from "../../app/FluentAppProvider";
import { applyTokenResponse, clearSession } from "../access/session";
import { SessionProvider } from "../access/SessionProvider";
import {
    handlers,
    ownerTokens,
    resetOnboardingMocks,
} from "../../shared/api/mocks/handlers";
import { problem } from "../../shared/api/mocks/mock-http";
import { mockOnboardingId } from "../../shared/api/mocks/onboarding-handlers";
import { runtimeConfig } from "../../shared/lib/runtime-config";

const server = setupServer(...handlers);
const sampleRegistration = ["2024", "123456", "07"].join("/");

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    clearSession();
    resetOnboardingMocks();
    server.resetHandlers();
});
afterAll(() => server.close());

function renderOnboarding(path = "/app/onboarding") {
    applyTokenResponse(ownerTokens);
    const router = createMemoryRouter(appRoutes, { initialEntries: [path] });
    return {
        router,
        user: userEvent.setup(),
        ...render(
            <FluentAppProvider>
                <SessionProvider>
                    <RouterProvider router={router} />
                </SessionProvider>
            </FluentAppProvider>,
        ),
    };
}

function companyPdf() {
    return new File(["%PDF-1 company document"], "company.pdf", {
        type: "application/pdf",
    });
}

describe("registered business onboarding", () => {
    it("looks up, confirms, uploads, and saves document corrections on URL routes", async () => {
        const { router, user } = renderOnboarding();

        await screen.findByRole("heading", { name: "Register your business" });
        await user.type(
            screen.getByLabelText(/company registration number/i),
            sampleRegistration,
        );
        await user.click(
            screen.getByRole("button", { name: "Look up company" }),
        );

        expect(
            await screen.findByRole("heading", {
                name: "Review registry details",
            }),
        ).toBeInTheDocument();
        expect(router.state.location.pathname).toBe(
            `/app/onboarding/${mockOnboardingId}`,
        );
        expect(router.state.location.search).toBe("");
        expect(router.state.location.pathname).not.toContain("123456");
        expect(
            screen.getByText(/unconfirmed until you accept/i),
        ).toBeInTheDocument();
        expect(
            screen.getByDisplayValue("Mahlako General Trading (Pty) Ltd"),
        ).toBeInTheDocument();

        await user.click(
            screen.getByRole("button", { name: "Confirm business profile" }),
        );
        expect(
            await screen.findByRole("heading", {
                name: "Upload a company document",
            }),
        ).toBeInTheDocument();
        expect(screen.getByLabelText(/company document/i)).toHaveAttribute(
            "type",
            "file",
        );

        await user.upload(
            screen.getByLabelText(/company document/i),
            companyPdf(),
        );
        let confirmedFields: Array<{ path?: string; value?: string }> = [];
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/documents/:documentId/confirmations`,
                async ({ request }) => {
                    const body = (await request.json()) as {
                        fields?: Array<{ path?: string; value?: string }>;
                    };
                    confirmedFields = body.fields ?? [];
                    return HttpResponse.json({
                        documentId: "00000000-0000-4000-8000-000000000024",
                        state: "CONFIRMED",
                        confirmation: { fields: confirmedFields },
                    });
                },
            ),
        );
        await user.click(
            screen.getByRole("button", { name: "Upload document" }),
        );

        expect(
            await screen.findByRole("heading", {
                name: "Correct extracted fields",
            }),
        ).toBeInTheDocument();
        const trading = screen.getByLabelText(/tradingName/i);
        await user.clear(trading);
        await user.type(trading, "Mahlako Store");
        await user.click(
            screen.getByRole("button", { name: "Confirm document fields" }),
        );

        expect(
            await screen.findByRole("heading", { name: "Business confirmed" }),
        ).toBeInTheDocument();
        expect(screen.getByText(/Mahlako General Trading/)).toBeInTheDocument();
        expect(confirmedFields).toContainEqual({
            path: "tradingName",
            value: "Mahlako Store",
        });
    });

    it("shows distinct lookup failures and retries", async () => {
        const cases = [
            [
                "COMPANY_NOT_FOUND",
                404,
                "The company registry did not return this business",
            ],
            [
                "REGISTRATION_ALREADY_ONBOARDED",
                409,
                "This company registration number is already being onboarded or has been confirmed",
            ],
            [
                "INVALID_REGISTRATION_NUMBER",
                400,
                "Use a 12-digit South African company registration number",
            ],
            [
                "ACCESS_DENIED",
                403,
                "The caller is not allowed to perform this action",
            ],
            [
                "EXTERNAL_PROVIDER_FAILED",
                502,
                "An external provider rejected the request",
            ],
        ] as const;

        for (const [code, status, title] of cases) {
            server.use(
                http.post(
                    `${runtimeConfig.apiBaseUrl}/api/businesses/onboarding/registered`,
                    () => problem(status, title, code),
                ),
            );
            const { user, unmount } = renderOnboarding();
            await screen.findByRole("heading", {
                name: "Register your business",
            });
            await user.type(
                screen.getByLabelText(/company registration number/i),
                sampleRegistration,
            );
            await user.click(
                screen.getByRole("button", { name: "Look up company" }),
            );
            expect(
                await screen.findByRole("heading", { name: title }),
            ).toBeInTheDocument();
            expect(
                screen.getByRole("button", { name: "Try again" }),
            ).toBeInTheDocument();
            unmount();
            clearSession();
        }
    });

    it("keeps processing until registry details arrive", async () => {
        let registryGets = 0;
        server.use(
            http.post(
                `${runtimeConfig.apiBaseUrl}/api/businesses/onboarding/registered`,
                () =>
                    HttpResponse.json(
                        {
                            onboardingId: mockOnboardingId,
                            state: "PENDING_CONFIRMATION",
                            trusted: false,
                            createdAt: "2026-09-02T12:00:00Z",
                        },
                        { status: 201 },
                    ),
            ),
            http.get(
                `${runtimeConfig.apiBaseUrl}/api/businesses/onboarding/registered/:onboardingId`,
                () => {
                    registryGets += 1;
                    if (registryGets < 2) {
                        return HttpResponse.json({
                            onboardingId: mockOnboardingId,
                            state: "PENDING_CONFIRMATION",
                            trusted: false,
                        });
                    }
                    return HttpResponse.json({
                        onboardingId: mockOnboardingId,
                        legalName: "Mahlako General Trading (Pty) Ltd",
                        tradingName: "Mahlako General Store",
                        registeredAddress: "42 Madiba Street, Tembisa, Gauteng",
                        state: "PENDING_CONFIRMATION",
                        trusted: false,
                    });
                },
            ),
        );
        const { user } = renderOnboarding();
        await screen.findByRole("heading", { name: "Register your business" });
        await user.type(
            screen.getByLabelText(/company registration number/i),
            sampleRegistration,
        );
        await user.click(
            screen.getByRole("button", { name: "Look up company" }),
        );

        expect(
            await screen.findByText("Waiting for the company registry..."),
        ).toBeInTheDocument();
        expect(
            await screen.findByRole(
                "heading",
                { name: "Review registry details" },
                { timeout: 4000 },
            ),
        ).toBeInTheDocument();
    });

    it("blocks analysts from the onboarding route", async () => {
        const { analystTokens } =
            await import("../../shared/api/mocks/handlers");
        applyTokenResponse(analystTokens);
        const router = createMemoryRouter(appRoutes, {
            initialEntries: ["/app/onboarding"],
        });
        render(
            <FluentAppProvider>
                <SessionProvider>
                    <RouterProvider router={router} />
                </SessionProvider>
            </FluentAppProvider>,
        );

        expect(
            await screen.findByRole("heading", { name: "Access denied" }),
        ).toBeInTheDocument();
    });
});
