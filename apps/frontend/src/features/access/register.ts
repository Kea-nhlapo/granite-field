import { authRegister } from "../../shared/api/app-api";
import type { ApiProblem, RegisterRequest } from "../../shared/api/generated";

import { applyTokenResponse, type Session } from "./session";

export async function registerWithPassword(
    email: string,
    password: string,
    accountType: RegisterRequest["accountType"],
): Promise<{ session: Session | null; error?: ApiProblem }> {
    const result = await authRegister({
        body: { accountType, email, password },
    });

    if (result.error) {
        return { error: result.error as ApiProblem, session: null };
    }

    if (!result.data) {
        return {
            error: {
                code: "INVALID_REQUEST",
                detail: "The account could not be created.",
                instance: "/api/auth/register",
                requestId: "",
                status: 400,
                title: "The account could not be created",
                type: "about:blank",
            },
            session: null,
        };
    }

    return { session: applyTokenResponse(result.data) };
}
