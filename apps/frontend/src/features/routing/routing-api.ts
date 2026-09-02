import {
    routeScoringGet,
    routeScoringScore,
    routingCalculate,
    routingGet,
} from "../../shared/api/app-api";
import type {
    CalculateRoutesRequest,
    ScoreRoutesRequest,
} from "../../shared/api/generated";

export function calculateRoutes(
    businessId: string,
    body: CalculateRoutesRequest,
) {
    return routingCalculate({
        body,
        path: { businessId },
    });
}

export function loadCalculation(businessId: string, calculationId: string) {
    return routingGet({
        path: { businessId, calculationId },
    });
}

export function scoreRoutes(
    businessId: string,
    calculationId: string,
    body: ScoreRoutesRequest,
) {
    return routeScoringScore({
        body,
        path: { businessId, calculationId },
    });
}

export function loadAssessment(businessId: string, assessmentId: string) {
    return routeScoringGet({
        path: { businessId, assessmentId },
    });
}
