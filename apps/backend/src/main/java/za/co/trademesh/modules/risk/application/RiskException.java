package za.co.trademesh.modules.risk.application;

import org.springframework.http.HttpStatus;

public class RiskException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private RiskException(HttpStatus status, String code, String message) {
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

    static RiskException indicatorNotFound() {
        return new RiskException(HttpStatus.NOT_FOUND, "RISK_INDICATOR_NOT_FOUND", "The risk indicator was not found.");
    }

    static RiskException invalidTransition() {
        return new RiskException(
                HttpStatus.CONFLICT,
                "RISK_INDICATOR_TRANSITION_NOT_ALLOWED",
                "The risk indicator cannot move to the requested state.");
    }

    static RiskException commandConflict() {
        return new RiskException(
                HttpStatus.CONFLICT,
                "RISK_COMMAND_CONFLICT",
                "The command ID was already used for different risk review data.");
    }

    static RiskException invalidRequest() {
        return new RiskException(HttpStatus.BAD_REQUEST, "INVALID_RISK_REQUEST", "The risk request is invalid.");
    }
}
