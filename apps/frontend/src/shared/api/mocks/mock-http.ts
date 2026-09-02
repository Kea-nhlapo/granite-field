import { HttpResponse } from "msw";

import type { ApiProblem } from "../generated";

export const mockScenarioHeader = "X-Mock-Scenario";

export function scenarioOf(request: Request) {
    return request.headers.get(mockScenarioHeader) ?? "success";
}

export function standardError(scenario: string) {
    if (scenario === "validation") {
        return problem(400, "Request validation failed", "INVALID_REQUEST");
    }
    if (scenario === "forbidden") {
        return problem(403, "Access denied", "ACCESS_DENIED");
    }
    if (scenario === "server-error") {
        return problem(500, "Request could not be completed", "INTERNAL_ERROR");
    }
    return undefined;
}

export function problem(status: number, title: string, code: string) {
    const response: ApiProblem = {
        code,
        detail: `${title}.`,
        instance: "/api",
        requestId: "00000000-0000-4000-8000-000000000099",
        status,
        title,
        type: "about:blank",
    };
    return HttpResponse.json(response, { status });
}
