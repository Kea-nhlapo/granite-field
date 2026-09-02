package za.co.trademesh.modules.insurance.application;

import org.springframework.http.HttpStatus;

public final class InsuranceException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private InsuranceException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    static InsuranceException invalidRequest() {
        return new InsuranceException("INVALID_INSURANCE_REQUEST", HttpStatus.BAD_REQUEST, "The request is invalid");
    }

    static InsuranceException caseNotFound() {
        return new InsuranceException("INSURANCE_CASE_NOT_FOUND", HttpStatus.NOT_FOUND, "The case was not found");
    }

    static InsuranceException shipmentNotFound() {
        return new InsuranceException("SHIPMENT_NOT_FOUND", HttpStatus.NOT_FOUND, "The shipment was not found");
    }

    static InsuranceException insurerNotEligible() {
        return new InsuranceException(
                "INSURER_NOT_ELIGIBLE", HttpStatus.UNPROCESSABLE_CONTENT, "The assigned insurer account is not active");
    }

    static InsuranceException evidenceAccessDenied() {
        return new InsuranceException(
                "INSURANCE_EVIDENCE_ACCESS_DENIED",
                HttpStatus.FORBIDDEN,
                "This account is not assigned to review the case");
    }

    static InsuranceException requestConflict() {
        return new InsuranceException(
                "INSURANCE_REQUEST_CONFLICT", HttpStatus.CONFLICT, "The request ID was already used differently");
    }

    static InsuranceException decisionConflict() {
        return new InsuranceException(
                "INSURANCE_DECISION_CONFLICT", HttpStatus.CONFLICT, "The decision command conflicts with prior work");
    }
}
