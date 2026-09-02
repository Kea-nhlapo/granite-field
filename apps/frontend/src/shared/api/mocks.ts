import { ApiError } from "./errors";
import type {
    BusinessLookup,
    CapacityMatch,
    Consolidation,
    DocumentReview,
    GuestQuoteReceipt,
    HandoverChallenge,
    HandoverReceipt,
    InsuranceCase,
    Invitation,
    InvitationStatus,
    MismatchEvidence,
    Order,
    PublicRole,
    Quote,
    RiskCase,
    RouteOption,
    Session,
    Shipment,
    StockRequest,
} from "./generated";

type Store = {
    session: Session | null;
    quoteSubmitted: boolean;
    orderKeys: Map<string, Order>;
    capacityEmpty: boolean;
    shipmentTicks: number;
    handoverError: string | null;
    challengeUsed: boolean;
    document: DocumentReview | null;
};

const store: Store = {
    session: null,
    quoteSubmitted: false,
    orderKeys: new Map(),
    capacityEmpty: false,
    shipmentTicks: 0,
    handoverError: null,
    challengeUsed: false,
    document: null,
};

const users: Record<string, { password: string; session: Session }> = {
    "naledi@khanyisa.co.za": {
        password: "stockroom",
        session: {
            id: "sess_business_1",
            email: "naledi@khanyisa.co.za",
            role: "BUSINESS",
            displayName: "Mama Nkosi Spaza Supply",
            onboardingComplete: true,
        },
    },
    "new@khanyisa.co.za": {
        password: "stockroom",
        session: {
            id: "sess_new_1",
            email: "new@khanyisa.co.za",
            role: "BUSINESS",
            displayName: "New Registrant",
            onboardingComplete: false,
        },
    },
    "risk@internal.example": {
        password: "stockroom",
        session: {
            id: "sess_risk_1",
            email: "risk@internal.example",
            role: "INTERNAL_RISK",
            displayName: "Risk desk",
            onboardingComplete: true,
        },
    },
    "insurer@cover.example": {
        password: "stockroom",
        session: {
            id: "sess_ins_1",
            email: "insurer@cover.example",
            role: "INSURER",
            displayName: "Cover desk",
            onboardingComplete: true,
        },
    },
};

function requireSession(role?: PublicRole): Session {
    if (!store.session) {
        throw new ApiError("UNAUTHORIZED", "Sign in to continue.", 401);
    }
    if (role && store.session.role !== role) {
        throw new ApiError(
            "FORBIDDEN",
            "You do not have access to this area.",
            403,
        );
    }
    return store.session;
}

function invitationStatus(token: string): InvitationStatus {
    if (token === "expired") return "EXPIRED";
    if (token === "revoked") return "REVOKED";
    if (token === "used" || store.quoteSubmitted) return "USED";
    if (token === "invalid") return "INVALID";
    if (token === "SB-INV-7XK9M2") return "VALID";
    return "INVALID";
}

const defaultDocument = (): DocumentReview => ({
    id: "doc-1",
    state: "READY",
    confidence: "REVIEW_NEEDED",
    lines: [
        {
            id: "L1",
            label: "Sunflower oil 5L",
            extracted: "50",
            current: "50",
        },
        {
            id: "L2",
            label: "Maize meal 10kg",
            extracted: "25",
            current: "25",
        },
    ],
});

export const mocks = {
    reset() {
        store.session = null;
        store.quoteSubmitted = false;
        store.orderKeys = new Map();
        store.capacityEmpty = false;
        store.shipmentTicks = 0;
        store.handoverError = null;
        store.challengeUsed = false;
        store.document = null;
    },
    setCapacityEmpty(value: boolean) {
        store.capacityEmpty = value;
    },
    setHandoverError(code: string | null) {
        store.handoverError = code;
    },
    login(email: string, password: string): Session {
        const record = users[email.trim().toLowerCase()];
        if (!record || record.password !== password) {
            throw new ApiError(
                "UNAUTHORIZED",
                "Email or password is wrong.",
                401,
            );
        }
        store.session = { ...record.session };
        return store.session;
    },
    logout() {
        requireSession();
        store.session = null;
    },
    getSession(): Session {
        return requireSession();
    },
    refreshSession() {
        requireSession();
    },
    lookupBusiness(registrationNumber: string): BusinessLookup {
        requireSession("BUSINESS");
        if (!registrationNumber.trim()) {
            throw new ApiError(
                "VALIDATION",
                "Enter a registration number.",
                400,
            );
        }
        if (registrationNumber === "fail") {
            throw new ApiError(
                "PROVIDER_FAILURE",
                "The company registry could not be reached. Try again.",
                502,
            );
        }
        if (registrationNumber === "missing") {
            throw new ApiError(
                "NOT_FOUND",
                "No business matched that registration number.",
                404,
            );
        }
        if (registrationNumber === "duplicate") {
            throw new ApiError(
                "CONFLICT",
                "This registration is already on the platform.",
                409,
            );
        }
        return {
            registrationNumber,
            legalName: "Mama Nkosi Spaza Supply",
            confirmed: false,
        };
    },
    confirmBusinessProfile(
        registrationNumber: string,
        legalName: string,
    ): BusinessLookup {
        const session = requireSession("BUSINESS");
        if (!legalName.trim()) {
            throw new ApiError("VALIDATION", "Confirm the legal name.", 400);
        }
        store.session = {
            ...session,
            onboardingComplete: true,
            displayName: legalName,
        };
        return {
            registrationNumber,
            legalName,
            confirmed: true,
        };
    },
    getInvitation(token: string): Invitation {
        return {
            status: invitationStatus(token),
            requestSummary: "Sunflower oil, maize meal, and milk for Soweto.",
        };
    },
    submitGuestQuote(token: string): GuestQuoteReceipt {
        const status = invitationStatus(token);
        if (status !== "VALID") {
            throw new ApiError(
                "GONE",
                "This invitation can no longer be used.",
                410,
            );
        }
        if (store.quoteSubmitted) {
            throw new ApiError(
                "CONFLICT",
                "A quote was already sent for this invite.",
                409,
            );
        }
        store.quoteSubmitted = true;
        return { quoteId: "QUO-1001", conversionOffered: true };
    },
    uploadDocument(fileName: string): { id: string; state: string } {
        if (fileName.endsWith(".exe") || fileName === "unsupported") {
            throw new ApiError(
                "VALIDATION",
                "This file type is not supported. Use PDF, JPG, or PNG.",
                400,
            );
        }
        if (fileName === "corrupt") {
            store.document = {
                id: "doc-fail",
                state: "FAILED",
                confidence: "REVIEW_NEEDED",
                lines: [],
            };
            return { id: "doc-fail", state: "FAILED" };
        }
        store.document = defaultDocument();
        return { id: "doc-1", state: "PROCESSING" };
    },
    getDocument(id: string): DocumentReview {
        if (id === "doc-fail" || store.document?.id === "doc-fail") {
            throw new ApiError(
                "SERVER_ERROR",
                "The document could not be parsed. Try another file.",
                500,
            );
        }
        return store.document ?? defaultDocument();
    },
    correctExtraction(lineId: string, value: string): DocumentReview {
        const document = store.document ?? defaultDocument();
        store.document = {
            ...document,
            lines: document.lines.map((line) =>
                line.id === lineId ? { ...line, current: value } : line,
            ),
        };
        return store.document;
    },
    getMismatch(id: string): MismatchEvidence {
        requireSession();
        return {
            id,
            left: {
                source: "Purchase order ORD-2026-9012",
                value: "30 × 10kg",
            },
            right: { source: "Supplier invoice INV-441", value: "25 × 10kg" },
            language: "MISMATCH",
        };
    },
    createStockRequest(request: StockRequest): StockRequest {
        requireSession("BUSINESS");
        if (request.items.length === 0) {
            throw new ApiError("VALIDATION", "Add at least one item.", 400);
        }
        return request;
    },
    getQuote(id: string): Quote {
        requireSession("BUSINESS");
        if (id === "expired") {
            throw new ApiError("GONE", "This quote is no longer valid.", 410);
        }
        return {
            id,
            supplier: "Thabo Distributors",
            validUntil: "2026-09-04T12:00:00Z",
            total: { currency: "ZAR", minor: 1448000 },
            lines: [
                { sku: "OIL-SFW-5L", requested: "50", quoted: "50" },
                { sku: "GRN-MZM-10", requested: "30", quoted: "25" },
            ],
        };
    },
    confirmOrder(quoteId: string, idempotencyKey: string): Order {
        requireSession("BUSINESS");
        const existing = store.orderKeys.get(idempotencyKey);
        if (existing) {
            return existing;
        }
        if (quoteId === "conflict") {
            throw new ApiError(
                "CONFLICT",
                "This quote was already confirmed.",
                409,
            );
        }
        const order: Order = {
            id: "ORD-2026-9012",
            quoteId,
            state: "CONFIRMED",
        };
        store.orderKeys.set(idempotencyKey, order);
        return order;
    },
    getConsolidation(): Consolidation {
        requireSession();
        return {
            id: "con-soweto",
            included: [
                { label: "Mama Nkosi Spaza", weightKg: 710, volumeM3: 1.2 },
                { label: "Phindile's Spaza", weightKg: 240, volumeM3: 0.4 },
            ],
            exclusions: [{ reason: "Delivery window does not overlap." }],
        };
    },
    getCapacityMatches(): { matches: CapacityMatch[] } {
        requireSession();
        if (store.capacityEmpty) {
            return { matches: [] };
        }
        return {
            matches: [
                {
                    id: "T-JHB-0047",
                    spareKg: 590,
                    addedKm: 18,
                    cost: { currency: "ZAR", minor: 42000 },
                    score: 87,
                    hardness: "TRADE_OFF",
                    reason: "Spare mass and timing line up.",
                },
                {
                    id: "T-BLOCK",
                    spareKg: 40,
                    addedKm: 96,
                    cost: { currency: "ZAR", minor: 180000 },
                    score: 12,
                    hardness: "HARD_FAIL",
                    reason: "Cargo class is incompatible with this vehicle.",
                },
            ],
        };
    },
    getRouteOptions(): { options: RouteOption[] } {
        requireSession();
        return {
            options: [
                {
                    id: "A",
                    name: "N1 + R21 Bypass",
                    kind: "RECOMMENDED",
                    time: "2h 14m",
                    distanceKm: 64,
                    missingData: false,
                    reason: "Avoids a known high-risk stretch.",
                    geometry: "n1-r21",
                },
                {
                    id: "B",
                    name: "N14 Toll Route",
                    kind: "SAFEST",
                    time: "2h 41m",
                    distanceKm: 71,
                    missingData: true,
                    reason: "Coverage samples are incomplete — not marked safe.",
                    geometry: "n14",
                },
                {
                    id: "C",
                    name: "N3 Direct",
                    kind: "FASTEST",
                    time: "1h 58m",
                    distanceKm: 58,
                    missingData: false,
                    reason: "Shortest time, heavier midday traffic.",
                    geometry: "n3",
                },
            ],
        };
    },
    selectRoute(routeId: string, cargoProfile: string): RouteOption {
        requireSession();
        const match = this.getRouteOptions().options.find(
            (option) => option.id === routeId,
        );
        if (!match) {
            throw new ApiError(
                "NOT_FOUND",
                "That route is not available.",
                404,
            );
        }
        return {
            ...match,
            reason: `${match.reason} Cargo: ${cargoProfile}.`,
        };
    },
    getShipment(): Shipment {
        requireSession();
        store.shipmentTicks += 1;
        const events: Shipment["events"] = [
            {
                at: "2026-09-02T07:30:00Z",
                kind: "HANDOVER",
                summary: "Collection confirmed",
            },
            {
                at: "2026-09-02T08:02:00Z",
                kind: "DEVIATION",
                summary: "Possible route deviation — requires review",
            },
        ];
        if (store.shipmentTicks > 1) {
            events.push({
                at: "2026-09-02T08:08:00Z",
                kind: "POSITION",
                summary: "Latest authorized position: East Rand corridor",
            });
        }
        return {
            id: "SB-2026-9901",
            state: "IN_TRANSIT",
            approvedPath: "Germiston → Soweto CBD via N1/R21",
            actualPath: "Same corridor, fuel stop on N12",
            approximateArea: "East Rand to Soweto",
            events,
        };
    },
    createHandoverChallenge(
        shipmentId: string,
        kind: "COLLECTION" | "DELIVERY",
    ): HandoverChallenge {
        requireSession();
        if (shipmentId === "wrong") {
            throw new ApiError(
                "NOT_FOUND",
                "That shipment was not found.",
                404,
            );
        }
        store.challengeUsed = false;
        return {
            id: `ch-${kind.toLowerCase()}`,
            expiresAt: new Date(Date.now() + 15 * 60 * 1000).toISOString(),
            displayCode: "SB-2026-9901",
        };
    },
    confirmHandover(input: {
        challengeId: string;
        quantity: string;
        fallback: boolean;
        disputeNote?: string;
    }): HandoverReceipt {
        requireSession();
        const forced = store.handoverError;
        if (forced === "GONE") {
            throw new ApiError("GONE", "This challenge has expired.", 410);
        }
        if (forced === "CONFLICT" || store.challengeUsed) {
            throw new ApiError(
                "CONFLICT",
                "This challenge was already used.",
                409,
            );
        }
        if (forced === "FORBIDDEN") {
            throw new ApiError(
                "FORBIDDEN",
                "You are not the assigned party for this handover.",
                403,
            );
        }
        if (forced === "VALIDATION") {
            throw new ApiError(
                "VALIDATION",
                "The scan location does not match the expected site.",
                400,
            );
        }
        if (forced === "SERVER_ERROR") {
            throw new ApiError(
                "SERVER_ERROR",
                "The device is offline. Use the fallback code when you reconnect.",
                503,
            );
        }
        if (!input.quantity.trim()) {
            throw new ApiError("VALIDATION", "Confirm the quantity.", 400);
        }
        store.challengeUsed = true;
        return {
            id: "REF-F9K2",
            confirmedAt: new Date().toISOString(),
        };
    },
    getRiskCase(): RiskCase {
        requireSession("INTERNAL_RISK");
        return {
            id: "risk-1",
            notes: "Investigate device change against handover timestamps.",
            indicators: [
                {
                    label: "Driver phone changed 3× in 30 days",
                    state: "OPEN",
                    source: "device-registry",
                    at: "2026-09-01T10:00:00Z",
                },
            ],
        };
    },
    getInsuranceCase(): InsuranceCase {
        requireSession("INSURER");
        return {
            id: "ins-1",
            evidence: [
                { label: "Collection QR", source: "handover-receipt" },
                { label: "Approved route", source: "routing-service" },
            ],
        };
    },
};
