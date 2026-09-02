package za.co.trademesh.modules.procurement.application;

import org.springframework.http.HttpStatus;

public class ProcurementException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ProcurementException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static ProcurementException requestNotFound() {
        return new ProcurementException(
                HttpStatus.NOT_FOUND, "PRODUCT_REQUEST_NOT_FOUND", "The product request was not found");
    }

    public static ProcurementException quoteNotFound() {
        return new ProcurementException(HttpStatus.NOT_FOUND, "QUOTE_NOT_FOUND", "The quote was not found");
    }

    public static ProcurementException orderNotFound() {
        return new ProcurementException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "The order was not found");
    }

    public static ProcurementException supplierNotFound() {
        return new ProcurementException(HttpStatus.NOT_FOUND, "SUPPLIER_NOT_FOUND", "The supplier was not found");
    }

    public static ProcurementException documentNotConfirmed() {
        return new ProcurementException(
                HttpStatus.CONFLICT,
                "QUOTE_DOCUMENT_NOT_CONFIRMED",
                "The quote source document must be confirmed first");
    }

    public static ProcurementException invalidRequest() {
        return new ProcurementException(
                HttpStatus.BAD_REQUEST,
                "INVALID_PRODUCT_REQUEST",
                "The product request contains invalid items, destination, or delivery dates");
    }

    public static ProcurementException invalidQuote() {
        return new ProcurementException(
                HttpStatus.BAD_REQUEST,
                "INVALID_QUOTE",
                "The quote contains invalid prices, currency, validity, or line items");
    }

    public static ProcurementException stateConflict() {
        return new ProcurementException(
                HttpStatus.CONFLICT,
                "PROCUREMENT_STATE_CONFLICT",
                "This procurement action is not allowed in the current state");
    }

    public static ProcurementException idempotencyConflict() {
        return new ProcurementException(
                HttpStatus.CONFLICT,
                "PROCUREMENT_REQUEST_CONFLICT",
                "The request identifier has already been used for different data");
    }

    public static ProcurementException sourceDocumentUsed() {
        return new ProcurementException(
                HttpStatus.CONFLICT,
                "QUOTE_DOCUMENT_ALREADY_USED",
                "The source document is already attached to another quote");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
