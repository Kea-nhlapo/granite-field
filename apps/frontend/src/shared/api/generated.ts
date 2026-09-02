/**
 * Generated client surface for packages/api-contracts/openapi/openapi.yaml.
 * `npm run contracts:check` fails when OPERATION_IDS drift from the spec.
 */
export const OPERATION_IDS = [
    "login",
    "logout",
    "getSession",
    "refreshSession",
    "lookupBusiness",
    "confirmBusinessProfile",
    "getInvitation",
    "submitGuestQuote",
    "uploadDocument",
    "getDocument",
    "correctExtraction",
    "getMismatch",
    "createStockRequest",
    "getQuote",
    "confirmOrder",
    "getConsolidation",
    "getCapacityMatches",
    "getRouteOptions",
    "selectRoute",
    "getShipment",
    "createHandoverChallenge",
    "confirmHandover",
    "getRiskCase",
    "getInsuranceCase",
] as const;

export type OperationId = (typeof OPERATION_IDS)[number];

export type PublicRole = "BUSINESS" | "INTERNAL_RISK" | "INSURER";

export type ApiErrorCode =
    | "UNAUTHORIZED"
    | "FORBIDDEN"
    | "VALIDATION"
    | "NOT_FOUND"
    | "CONFLICT"
    | "GONE"
    | "PROVIDER_FAILURE"
    | "SERVER_ERROR";

export type Session = {
    id: string;
    email: string;
    role: PublicRole;
    displayName: string;
    onboardingComplete: boolean;
};

export type Money = { currency: string; minor: number };

export type InvitationStatus =
    "VALID" | "EXPIRED" | "REVOKED" | "USED" | "INVALID";

export type BusinessLookup = {
    registrationNumber: string;
    legalName: string;
    confirmed: boolean;
};

export type Invitation = {
    status: InvitationStatus;
    requestSummary: string;
};

export type GuestQuoteReceipt = {
    quoteId: string;
    conversionOffered: boolean;
};

export type DocumentJob = {
    id: string;
    state: "QUEUED" | "PROCESSING" | "READY" | "FAILED";
};

export type ExtractedLine = {
    id: string;
    label: string;
    extracted: string;
    current: string;
};

export type DocumentReview = {
    id: string;
    state: string;
    confidence: "HIGH" | "REVIEW_NEEDED";
    lines: ExtractedLine[];
};

export type EvidenceSide = { source: string; value: string };

export type MismatchEvidence = {
    id: string;
    left: EvidenceSide;
    right: EvidenceSide;
    language: "MISMATCH" | "RISK_INDICATOR";
};

export type StockRequestItem = {
    sku: string;
    quantity: string;
    unit: string;
};

export type StockRequest = {
    items: StockRequestItem[];
    destination: string;
    window: string;
};

export type QuoteLine = { sku: string; requested: string; quoted: string };

export type Quote = {
    id: string;
    supplier: string;
    validUntil: string;
    total: Money;
    lines: QuoteLine[];
};

export type Order = { id: string; quoteId: string; state: string };

export type Consolidation = {
    id: string;
    included: { label: string; weightKg: number; volumeM3: number }[];
    exclusions: { reason: string }[];
};

export type CapacityMatch = {
    id: string;
    spareKg: number;
    addedKm: number;
    cost: Money;
    score: number;
    hardness: "HARD_FAIL" | "TRADE_OFF";
    reason: string;
};

export type RouteKind =
    "FASTEST" | "LOWEST_COST" | "SAFEST" | "BEST_CONNECTIVITY" | "RECOMMENDED";

export type RouteOption = {
    id: string;
    name: string;
    kind: RouteKind;
    time: string;
    distanceKm: number;
    missingData: boolean;
    reason: string;
    geometry: string;
};

export type ShipmentEventKind =
    | "POSITION"
    | "OFFLINE"
    | "DELAYED"
    | "DEVIATION"
    | "FUEL_LOSS"
    | "SEAL"
    | "DEVICE_CHANGE"
    | "HANDOVER";

export type ShipmentEvent = {
    at: string;
    kind: ShipmentEventKind;
    summary: string;
};

export type Shipment = {
    id: string;
    state: string;
    approvedPath: string;
    actualPath: string;
    approximateArea?: string;
    events: ShipmentEvent[];
};

export type HandoverChallenge = {
    id: string;
    expiresAt: string;
    displayCode: string;
};

export type HandoverReceipt = { id: string; confirmedAt: string };

export type RiskIndicator = {
    label: string;
    state: string;
    source: string;
    at: string;
};

export type RiskCase = {
    id: string;
    indicators: RiskIndicator[];
    notes: string;
};

export type InsuranceCase = {
    id: string;
    evidence: { label: string; source: string }[];
};

export function formatMoney(money: Money): string {
    const negative = money.minor < 0;
    const absolute = Math.abs(money.minor);
    const whole = Math.trunc(absolute / 100);
    const fraction = String(absolute % 100).padStart(2, "0");
    return `${negative ? "-" : ""}${money.currency} ${whole}.${fraction}`;
}
