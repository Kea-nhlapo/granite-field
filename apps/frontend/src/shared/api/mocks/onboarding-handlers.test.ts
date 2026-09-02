import { setupServer } from "msw/node";
import { afterAll, afterEach, beforeAll, describe, expect, test } from "vitest";

import {
    businessConfirmRegisteredOnboarding,
    businessGetRegisteredOnboarding,
    businessStartRegisteredOnboarding,
    fileStorageUpload,
} from "../app-api";
import { setApiAccessToken } from "../client";
import { handlers, mockScenarioHeader, resetOnboardingMocks } from "./handlers";
import {
    mockBusinessId,
    mockFileId,
    mockOnboardingId,
} from "./onboarding-handlers";

const server = setupServer(...handlers);

beforeAll(() => server.listen({ onUnhandledRequest: "error" }));
afterEach(() => {
    resetOnboardingMocks();
    setApiAccessToken(undefined);
    server.resetHandlers();
});
afterAll(() => server.close());

describe("registered onboarding mocks", () => {
    test("the generated start SDK returns registry details without putting the number in the path", async () => {
        setApiAccessToken("mock-access-token");
        const result = await businessStartRegisteredOnboarding({
            body: { registrationNumber: lookupNumber() },
        });

        expect(result.error).toBeUndefined();
        expect(result.response?.status).toBe(201);
        expect(result.data?.onboardingId).toBe(mockOnboardingId);
        expect(result.data?.legalName).toBe(
            "Mahlako General Trading (Pty) Ltd",
        );
        expect(result.data?.state).toBe("PENDING_CONFIRMATION");
        expect(result.data?.trusted).toBe(false);
        expect(result.response?.url).not.toContain("123456");
    });

    test.each([
        ["validation", 400, "INVALID_REGISTRATION_NUMBER"],
        ["not-found", 404, "COMPANY_NOT_FOUND"],
        ["duplicate", 409, "REGISTRATION_ALREADY_ONBOARDED"],
        ["forbidden", 403, "ACCESS_DENIED"],
        ["provider-failure", 502, "EXTERNAL_PROVIDER_FAILED"],
        ["provider-unavailable", 503, "EXTERNAL_PROVIDER_UNAVAILABLE"],
        ["server-error", 500, "INTERNAL_ERROR"],
    ] as const)(
        "maps the %s start scenario to %s %s",
        async (scenario, status, code) => {
            setApiAccessToken("mock-access-token");
            const result = await businessStartRegisteredOnboarding({
                body: { registrationNumber: lookupNumber() },
                headers: { [mockScenarioHeader]: scenario },
            });

            expect(result.data).toBeUndefined();
            expect(result.error).toMatchObject({ status, code });
        },
    );

    test("get and confirm use the onboarding UUID path", async () => {
        setApiAccessToken("mock-access-token");
        await businessStartRegisteredOnboarding({
            body: { registrationNumber: lookupNumber() },
        });

        const loaded = await businessGetRegisteredOnboarding({
            path: { onboardingId: mockOnboardingId },
        });
        expect(loaded.data?.state).toBe("PENDING_CONFIRMATION");

        const confirmed = await businessConfirmRegisteredOnboarding({
            path: { onboardingId: mockOnboardingId },
        });
        expect(confirmed.error).toBeUndefined();
        expect(confirmed.data?.verificationStatus).toBe("REGISTRY_VERIFIED");
        expect(confirmed.data?.businessId).toBeTruthy();
        expect(confirmed.response?.url).toContain(mockOnboardingId);
        expect(confirmed.response?.url).not.toContain("123456");
    });

    test("the generated file upload SDK posts multipart file bytes", async () => {
        setApiAccessToken("mock-access-token");
        const result = await fileStorageUpload({
            body: {
                file: new File(["%PDF-1 company"], "company.pdf", {
                    type: "application/pdf",
                }),
            },
            path: { businessId: mockBusinessId },
            query: { category: "COMPANY_DOCUMENT" },
        });

        expect(result.error).toBeUndefined();
        expect(result.response?.status).toBe(201);
        expect(result.data?.fileId).toBe(mockFileId);
        expect(result.data?.sizeBytes).toBeGreaterThan(0);
        expect(result.data?.category).toBe("COMPANY_DOCUMENT");
    });
});

function lookupNumber() {
    return ["2024", "123456", "07"].join("/");
}
