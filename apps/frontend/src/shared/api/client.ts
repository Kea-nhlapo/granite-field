import { ApiError } from "./errors";
import type {
    BusinessLookup,
    DocumentReview,
    GuestQuoteReceipt,
    HandoverChallenge,
    HandoverReceipt,
    InsuranceCase,
    Invitation,
    MismatchEvidence,
    Order,
    Quote,
    RiskCase,
    RouteOption,
    Session,
    Shipment,
    StockRequest,
} from "./generated";
import { isMockApiEnabled, runtimeConfig } from "../lib/runtime-config";
import { mocks } from "./mocks";

async function request(path: string, init?: RequestInit): Promise<Response> {
    let response: Response;
    try {
        response = await fetch(`${runtimeConfig.apiBaseUrl}${path}`, {
            credentials: "include",
            ...init,
            headers: {
                Accept: "application/json",
                "Content-Type": "application/json",
                ...init?.headers,
            },
        });
    } catch {
        throw new ApiError(
            "SERVER_ERROR",
            "The service could not be reached.",
            503,
        );
    }
    if (!response.ok) {
        let code = "SERVER_ERROR";
        let message = "Request failed.";
        try {
            const body = (await response.json()) as {
                code?: string;
                message?: string;
            };
            code = body.code ?? code;
            message = body.message ?? message;
        } catch {
            /* ignore */
        }
        throw new ApiError(code, message, response.status);
    }
    return response;
}

async function readJson<T>(response: Response): Promise<T> {
    if (response.status === 204) {
        return undefined as T;
    }
    return (await response.json()) as T;
}

export const api = {
    async login(email: string, password: string): Promise<Session> {
        if (isMockApiEnabled()) {
            return mocks.login(email, password);
        }
        await request("/auth/login", {
            method: "POST",
            body: JSON.stringify({ email, password }),
        });
        return api.getSession();
    },
    async logout(): Promise<void> {
        if (isMockApiEnabled()) {
            mocks.logout();
            return;
        }
        await request("/auth/logout", { method: "POST" });
    },
    async getSession(): Promise<Session> {
        if (isMockApiEnabled()) {
            return mocks.getSession();
        }
        return readJson<Session>(await request("/auth/session"));
    },
    async refreshSession(): Promise<void> {
        if (isMockApiEnabled()) {
            mocks.refreshSession();
            return;
        }
        await request("/auth/refresh", { method: "POST" });
    },
    lookupBusiness(registrationNumber: string): BusinessLookup {
        return mocks.lookupBusiness(registrationNumber);
    },
    confirmBusinessProfile(
        registrationNumber: string,
        legalName: string,
    ): BusinessLookup {
        return mocks.confirmBusinessProfile(registrationNumber, legalName);
    },
    getInvitation(token: string): Invitation {
        return mocks.getInvitation(token);
    },
    submitGuestQuote(token: string): GuestQuoteReceipt {
        return mocks.submitGuestQuote(token);
    },
    uploadDocument(fileName: string) {
        return mocks.uploadDocument(fileName);
    },
    getDocument(id: string): DocumentReview {
        return mocks.getDocument(id);
    },
    correctExtraction(lineId: string, value: string): DocumentReview {
        return mocks.correctExtraction(lineId, value);
    },
    getMismatch(id: string): MismatchEvidence {
        return mocks.getMismatch(id);
    },
    createStockRequest(request: StockRequest): StockRequest {
        return mocks.createStockRequest(request);
    },
    getQuote(id: string): Quote {
        return mocks.getQuote(id);
    },
    confirmOrder(quoteId: string, idempotencyKey: string): Order {
        return mocks.confirmOrder(quoteId, idempotencyKey);
    },
    getConsolidation() {
        return mocks.getConsolidation();
    },
    getCapacityMatches() {
        return mocks.getCapacityMatches();
    },
    getRouteOptions() {
        return mocks.getRouteOptions();
    },
    selectRoute(routeId: string, cargoProfile: string): RouteOption {
        return mocks.selectRoute(routeId, cargoProfile);
    },
    getShipment(): Shipment {
        return mocks.getShipment();
    },
    createHandoverChallenge(
        shipmentId: string,
        kind: "COLLECTION" | "DELIVERY",
    ): HandoverChallenge {
        return mocks.createHandoverChallenge(shipmentId, kind);
    },
    confirmHandover(input: {
        challengeId: string;
        quantity: string;
        fallback: boolean;
        disputeNote?: string;
    }): HandoverReceipt {
        return mocks.confirmHandover(input);
    },
    getRiskCase(): RiskCase {
        return mocks.getRiskCase();
    },
    getInsuranceCase(): InsuranceCase {
        return mocks.getInsuranceCase();
    },
};
