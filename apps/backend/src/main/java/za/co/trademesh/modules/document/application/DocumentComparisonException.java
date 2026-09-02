package za.co.trademesh.modules.document.application;

import org.springframework.http.HttpStatus;

public class DocumentComparisonException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DocumentComparisonException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static DocumentComparisonException notFound() {
        return new DocumentComparisonException(
                HttpStatus.NOT_FOUND, "DOCUMENT_COMPARISON_NOT_FOUND", "The document comparison was not found");
    }

    public static DocumentComparisonException sourceUnavailable() {
        return new DocumentComparisonException(
                HttpStatus.CONFLICT,
                "DOCUMENT_COMPARISON_SOURCE_UNAVAILABLE",
                "Both source documents must exist and have confirmed fields");
    }

    public static DocumentComparisonException unsupportedSource() {
        return new DocumentComparisonException(
                HttpStatus.BAD_REQUEST,
                "DOCUMENT_COMPARISON_SOURCE_UNSUPPORTED",
                "Only purchase orders, quotes, invoices, and delivery notes can be compared");
    }

    public static DocumentComparisonException invalidRequest() {
        return new DocumentComparisonException(
                HttpStatus.BAD_REQUEST,
                "INVALID_DOCUMENT_COMPARISON",
                "Two different source documents and a request identifier are required");
    }

    public static DocumentComparisonException idempotencyConflict() {
        return new DocumentComparisonException(
                HttpStatus.CONFLICT,
                "DOCUMENT_COMPARISON_REQUEST_CONFLICT",
                "The request identifier has already been used for different source revisions");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
