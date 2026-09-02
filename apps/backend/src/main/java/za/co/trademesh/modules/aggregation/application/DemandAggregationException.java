package za.co.trademesh.modules.aggregation.application;

import org.springframework.http.HttpStatus;

public class DemandAggregationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DemandAggregationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    static DemandAggregationException invalidRequest() {
        return new DemandAggregationException(
                HttpStatus.BAD_REQUEST, "INVALID_AGGREGATION_REQUEST", "A request ID and anchor order are required.");
    }

    static DemandAggregationException orderNotFound() {
        return new DemandAggregationException(
                HttpStatus.NOT_FOUND, "CONFIRMED_ORDER_NOT_FOUND", "The confirmed anchor order was not found.");
    }

    static DemandAggregationException suggestionNotFound() {
        return new DemandAggregationException(
                HttpStatus.NOT_FOUND, "AGGREGATION_SUGGESTION_NOT_FOUND", "The aggregation suggestion was not found.");
    }

    static DemandAggregationException idempotencyConflict() {
        return new DemandAggregationException(
                HttpStatus.CONFLICT,
                "AGGREGATION_REQUEST_CONFLICT",
                "The request ID has already been used for another aggregation input.");
    }
}
