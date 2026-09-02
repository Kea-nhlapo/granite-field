import {
    authRegister,
    supplierConvert,
    supplierSubmitResponse,
    supplierViewGuest,
} from "../../shared/api/app-api";
import type { SubmitResponseRequest } from "../../shared/api/generated";

export function viewGuestInvitation(token: string) {
    return supplierViewGuest({
        path: { token },
    });
}

export function submitGuestResponse(
    token: string,
    body: SubmitResponseRequest,
) {
    return supplierSubmitResponse({
        body,
        path: { token },
    });
}

export function registerSupplierAccount(email: string, password: string) {
    return authRegister({
        body: {
            accountType: "SUPPLIER",
            email,
            password,
        },
    });
}

export function convertGuestSupplier(
    supplierProfileId: string,
    invitationToken: string,
) {
    return supplierConvert({
        body: { invitationToken },
        path: { supplierProfileId },
    });
}
