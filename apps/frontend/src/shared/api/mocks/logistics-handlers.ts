import { http, HttpResponse } from "msw";

import type {
    SearchRequest,
    SearchResponse,
    SuggestionResponse,
    SuggestDemandGroupRequest,
} from "../generated";
import { runtimeConfig } from "../../lib/runtime-config";
import { problem, scenarioOf, standardError } from "./mock-http";
import { mockBusinessId } from "./onboarding-handlers";
import { mockOrderId } from "./procurement-handlers";

export const mockSuggestionId = "00000000-0000-4000-8000-000000000061";
export const mockCompatibleOrderId = "00000000-0000-4000-8000-000000000062";
export const mockExcludedOrderId = "00000000-0000-4000-8000-000000000063";
export const mockCapacitySearchId = "00000000-0000-4000-8000-000000000064";
export const mockCompatibleOfferId = "00000000-0000-4000-8000-000000000065";
export const mockFailedOfferId = "00000000-0000-4000-8000-000000000066";
export const mockReservationId = "00000000-0000-4000-8000-000000000067";

const suggestions = new Map<string, SuggestionResponse>();
const searches = new Map<string, SearchResponse>();

export function resetLogisticsMocks() {
    suggestions.clear();
    searches.clear();
}

function successSuggestion(): SuggestionResponse {
    return {
        suggestionId: mockSuggestionId,
        requestedByBusinessId: mockBusinessId,
        anchorOrderId: mockOrderId,
        status: "ACTIVE",
        algorithmVersion: "demand-aggregation/v1",
        thresholds: {
            searchRadiusMeters: 25000,
            maximumDistanceMeters: 15000,
            minimumWindowOverlap: "PT30M",
            minimumCargoOverlapRatio: 0.5,
            candidateLimit: 20,
        },
        score: 0.86,
        includedOrderCount: 2,
        createdAt: "2026-09-02T12:00:00Z",
        orders: [
            {
                orderId: mockOrderId,
                role: "ANCHOR",
                included: true,
                distanceMeters: 0,
                windowOverlapSeconds: 14400,
                cargoOverlapRatio: 1,
                score: 1,
                passedChecks: [
                    "ANCHOR_ORDER",
                    "SUPPLIER_OR_PICKUP_COMPATIBLE",
                    "WITHIN_DISTANCE",
                    "DELIVERY_WINDOW_OVERLAP",
                    "CARGO_COMPATIBLE",
                ],
                exclusionReasons: [],
            },
            {
                orderId: mockCompatibleOrderId,
                role: "CANDIDATE",
                included: true,
                distanceMeters: 2400,
                windowOverlapSeconds: 12600,
                cargoOverlapRatio: 0.8,
                score: 0.74,
                passedChecks: [
                    "SUPPLIER_OR_PICKUP_COMPATIBLE",
                    "WITHIN_DISTANCE",
                    "DELIVERY_WINDOW_OVERLAP",
                    "CARGO_COMPATIBLE",
                ],
                exclusionReasons: [],
            },
            {
                orderId: mockExcludedOrderId,
                role: "CANDIDATE",
                included: false,
                distanceMeters: 3100,
                windowOverlapSeconds: 0,
                cargoOverlapRatio: 0.2,
                score: 0,
                passedChecks: [],
                exclusionReasons: ["DELIVERY_WINDOWS_DO_NOT_OVERLAP"],
            },
        ],
    };
}

function emptySuggestion(anchorOrderId: string): SuggestionResponse {
    return {
        ...successSuggestion(),
        status: "NO_MATCH",
        includedOrderCount: 1,
        score: 0,
        orders: [
            {
                orderId: anchorOrderId,
                role: "ANCHOR",
                included: true,
                distanceMeters: 0,
                windowOverlapSeconds: 14400,
                cargoOverlapRatio: 1,
                score: 1,
                passedChecks: ["ANCHOR_ORDER"],
                exclusionReasons: [],
            },
        ],
    };
}

function successSearch(suggestionId: string): SearchResponse {
    return {
        searchId: mockCapacitySearchId,
        requestedByBusinessId: mockBusinessId,
        demandGroupSuggestionId: suggestionId,
        algorithmVersion: "capacity-matching/v1",
        requiredCapacity: { weightKg: 80, volumeCubicMetres: 6 },
        cargoTraits: ["DRY_GOODS", "FOOD_GRADE"],
        deliveryWindowStart: "2026-10-01T06:00:00Z",
        deliveryWindowEnd: "2026-10-01T10:00:00Z",
        orderCount: 2,
        status: "MATCHED",
        createdAt: "2026-09-02T12:05:00Z",
        candidates: [
            {
                offerId: mockCompatibleOfferId,
                transporterId: "00000000-0000-4000-8000-000000000068",
                compatible: true,
                rank: 1,
                availableCapacity: { weightKg: 100, volumeCubicMetres: 10 },
                addedDistanceMetres: 1800,
                timingOverlapSeconds: 7200,
                estimatedCostZar: 1450,
                score: 0.81,
                checks: [
                    {
                        constraint: "WEIGHT_CAPACITY",
                        outcome: "PASS",
                        explanation: "Spare weight remains after this group.",
                    },
                    {
                        constraint: "DELIVERY_WINDOW",
                        outcome: "PASS",
                        explanation: "The offer window covers the group.",
                    },
                ],
                scoreComponents: [
                    {
                        code: "ADDED_DISTANCE",
                        explanation: "A longer corridor adds travel time.",
                        contribution: -0.12,
                    },
                ],
            },
            {
                offerId: mockFailedOfferId,
                transporterId: "00000000-0000-4000-8000-000000000069",
                compatible: false,
                rank: undefined,
                availableCapacity: { weightKg: 20, volumeCubicMetres: 2 },
                addedDistanceMetres: 900,
                timingOverlapSeconds: 0,
                estimatedCostZar: 900,
                score: 0,
                checks: [
                    {
                        constraint: "WEIGHT_CAPACITY",
                        outcome: "FAIL",
                        explanation:
                            "The offer does not have enough spare weight.",
                    },
                ],
                scoreComponents: [],
            },
        ] as unknown as SearchResponse["candidates"],
    };
}

export const logisticsHandlers = [
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/aggregation/suggestions`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "stale") {
                return problem(
                    409,
                    "The request ID has already been used for another aggregation input",
                    "AGGREGATION_REQUEST_CONFLICT",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The aggregation suggestion was not found",
                    "AGGREGATION_SUGGESTION_NOT_FOUND",
                );
            }
            const body = (await request.json()) as SuggestDemandGroupRequest;
            if (!body.requestId || !body.anchorOrderId) {
                return problem(
                    400,
                    "A request ID and anchor order are required",
                    "INVALID_AGGREGATION_REQUEST",
                );
            }
            const created =
                scenario === "empty"
                    ? emptySuggestion(body.anchorOrderId)
                    : successSuggestion();
            suggestions.set(created.suggestionId ?? mockSuggestionId, created);
            return HttpResponse.json(created);
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/aggregation/suggestions/:suggestionId`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            const found = suggestions.get(String(params.suggestionId));
            if (!found) {
                return problem(
                    404,
                    "The aggregation suggestion was not found",
                    "AGGREGATION_SUGGESTION_NOT_FOUND",
                );
            }
            return HttpResponse.json(found);
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/logistics/capacity-matches/:searchId/reservations`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            const search = searches.get(String(params.searchId));
            if (!search) {
                return problem(
                    404,
                    "The capacity match search was not found",
                    "CAPACITY_MATCH_NOT_FOUND",
                );
            }
            const body = (await request.json()) as {
                requestId?: string;
                offerId?: string;
            };
            if (!body.requestId || !body.offerId) {
                return problem(
                    400,
                    "The demand group, cargo details, or required capacity is invalid",
                    "INVALID_CAPACITY_MATCH_REQUEST",
                );
            }
            if (body.offerId === mockFailedOfferId) {
                return problem(
                    409,
                    "The selected offer did not pass every hard check or is no longer available",
                    "CAPACITY_CANDIDATE_NOT_RESERVABLE",
                );
            }
            search.status = "RESERVED";
            searches.set(String(params.searchId), search);
            return HttpResponse.json(
                {
                    reservationId: mockReservationId,
                    matchSearchId: params.searchId,
                    requestId: body.requestId,
                    offerId: body.offerId,
                    reservedCapacity: search.requiredCapacity,
                    status: "ACTIVE",
                    expiresAt: "2026-09-02T13:05:00Z",
                    createdAt: "2026-09-02T12:10:00Z",
                },
                { status: 201 },
            );
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/logistics/capacity-matches/:searchId/reservation-release`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            const search = searches.get(String(params.searchId));
            if (!search) {
                return problem(
                    404,
                    "The capacity match search was not found",
                    "CAPACITY_MATCH_NOT_FOUND",
                );
            }
            search.status = "RELEASED";
            searches.set(String(params.searchId), search);
            return HttpResponse.json({
                reservationId: mockReservationId,
                matchSearchId: params.searchId,
                status: "RELEASED",
                releasedAt: "2026-09-02T12:20:00Z",
            });
        },
    ),
    http.post(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/logistics/capacity-matches`,
        async ({ params, request }) => {
            const scenario = scenarioOf(request);
            if (scenario === "stale") {
                return problem(
                    409,
                    "The orders no longer share a usable delivery window",
                    "DEMAND_DELIVERY_WINDOWS_DO_NOT_OVERLAP",
                );
            }
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            if (String(params.businessId) !== mockBusinessId) {
                return problem(
                    404,
                    "The active consolidated demand group was not found",
                    "CONSOLIDATED_DEMAND_NOT_FOUND",
                );
            }
            const body = (await request.json()) as SearchRequest;
            if (
                !body.requestId ||
                !body.demandGroupSuggestionId ||
                !body.requiredCapacity ||
                !body.cargoTraits?.length
            ) {
                return problem(
                    400,
                    "The demand group, cargo details, or required capacity is invalid",
                    "INVALID_CAPACITY_MATCH_REQUEST",
                );
            }
            const created =
                scenario === "no-match"
                    ? {
                          ...successSearch(body.demandGroupSuggestionId),
                          status: "NO_MATCH" as const,
                          candidates: [],
                      }
                    : successSearch(body.demandGroupSuggestionId);
            created.requiredCapacity = body.requiredCapacity;
            created.cargoTraits = body.cargoTraits;
            searches.set(created.searchId ?? mockCapacitySearchId, created);
            return HttpResponse.json(created, { status: 201 });
        },
    ),
    http.get(
        `${runtimeConfig.apiBaseUrl}/api/businesses/:businessId/logistics/capacity-matches/:searchId`,
        ({ params, request }) => {
            const scenario = scenarioOf(request);
            const error = standardError(scenario);
            if (error) {
                return error;
            }
            const found = searches.get(String(params.searchId));
            if (!found) {
                return problem(
                    404,
                    "The capacity match search was not found",
                    "CAPACITY_MATCH_NOT_FOUND",
                );
            }
            return HttpResponse.json(found);
        },
    ),
];
