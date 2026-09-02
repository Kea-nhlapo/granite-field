import type {
    CapacityResponse,
    SearchResponse,
} from "../../shared/api/generated";

export type CapacityOfferCandidate = {
    offerId?: string;
    transporterId?: string;
    compatible?: boolean;
    rank?: number;
    availableCapacity?: CapacityResponse;
    addedDistanceMetres?: number;
    timingOverlapSeconds?: number;
    estimatedCostZar?: number;
    score?: number;
    checks?: Array<{
        constraint?: string;
        outcome?: "PASS" | "FAIL";
        explanation?: string;
    }>;
    scoreComponents?: Array<{
        code?: string;
        explanation?: string;
        contribution?: number;
    }>;
};

export function capacityCandidates(search: SearchResponse | undefined) {
    return (
        (search?.candidates ?? []) as unknown as CapacityOfferCandidate[]
    ).filter((candidate) => Boolean(candidate.offerId));
}

export function isHardFailure(candidate: CapacityOfferCandidate) {
    return (
        candidate.compatible === false ||
        (candidate.checks ?? []).some((check) => check.outcome === "FAIL")
    );
}
