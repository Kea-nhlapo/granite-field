package za.co.trademesh.modules.supplier.application;

import org.springframework.http.HttpStatus;

public class SupplierException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private SupplierException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static SupplierException invalidEmail() {
        return new SupplierException(HttpStatus.BAD_REQUEST, "INVALID_SUPPLIER_EMAIL", "Enter a valid supplier email");
    }

    public static SupplierException invitationAlreadyActive() {
        return new SupplierException(
                HttpStatus.CONFLICT,
                "SUPPLIER_INVITATION_ALREADY_ACTIVE",
                "An active invitation already exists for this supplier and request");
    }

    public static SupplierException invitationUnavailable() {
        return new SupplierException(
                HttpStatus.NOT_FOUND, "SUPPLIER_INVITATION_UNAVAILABLE", "This supplier invitation is unavailable");
    }

    public static SupplierException invitationRateLimited() {
        return new SupplierException(
                HttpStatus.TOO_MANY_REQUESTS,
                "SUPPLIER_INVITATION_RATE_LIMITED",
                "Too many invitation attempts; try again later");
    }

    public static SupplierException invitationStateConflict() {
        return new SupplierException(
                HttpStatus.CONFLICT,
                "SUPPLIER_INVITATION_STATE_CHANGED",
                "The supplier invitation changed while the request was being processed");
    }

    public static SupplierException profileNotFound() {
        return new SupplierException(HttpStatus.NOT_FOUND, "SUPPLIER_PROFILE_NOT_FOUND", "The supplier was not found");
    }

    public static SupplierException controlNotProven() {
        return new SupplierException(
                HttpStatus.FORBIDDEN,
                "SUPPLIER_CONTROL_NOT_PROVEN",
                "The account does not control this temporary supplier profile");
    }

    public static SupplierException profileAlreadyClaimed() {
        return new SupplierException(
                HttpStatus.CONFLICT,
                "SUPPLIER_PROFILE_ALREADY_CLAIMED",
                "This temporary supplier has already been converted by another account");
    }

    public static SupplierException businessAlreadyClaimed() {
        return new SupplierException(
                HttpStatus.CONFLICT,
                "SUPPLIER_BUSINESS_ALREADY_CLAIMED",
                "This business is already linked to another supplier profile");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
