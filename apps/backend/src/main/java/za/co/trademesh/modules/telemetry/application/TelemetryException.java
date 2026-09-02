package za.co.trademesh.modules.telemetry.application;

import org.springframework.http.HttpStatus;

public class TelemetryException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TelemetryException(HttpStatus status, String code, String message) {
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

    static TelemetryException invalidRequest() {
        return new TelemetryException(
                HttpStatus.BAD_REQUEST,
                "INVALID_TELEMETRY_READING",
                "The telemetry batch contains an invalid reading or unsupported value.");
    }

    static TelemetryException deviceAuthenticationFailed() {
        return new TelemetryException(
                HttpStatus.UNAUTHORIZED,
                "TELEMETRY_DEVICE_AUTHENTICATION_FAILED",
                "The telemetry device credential is invalid or inactive.");
    }

    static TelemetryException shipmentNotFound() {
        return new TelemetryException(
                HttpStatus.NOT_FOUND, "TELEMETRY_SHIPMENT_NOT_FOUND", "The shipment was not found.");
    }

    static TelemetryException deviceNotFound() {
        return new TelemetryException(
                HttpStatus.NOT_FOUND, "TELEMETRY_DEVICE_NOT_FOUND", "The telemetry device was not found.");
    }

    static TelemetryException livePositionNotFound() {
        return new TelemetryException(
                HttpStatus.NOT_FOUND,
                "TELEMETRY_LIVE_POSITION_NOT_FOUND",
                "The shipment does not have a live position yet.");
    }

    static TelemetryException shipmentNotAcceptingReadings() {
        return new TelemetryException(
                HttpStatus.CONFLICT,
                "SHIPMENT_NOT_ACCEPTING_TELEMETRY",
                "The shipment is complete, disputed, or cancelled and no longer accepts telemetry.");
    }

    static TelemetryException clientEventConflict() {
        return new TelemetryException(
                HttpStatus.CONFLICT,
                "TELEMETRY_CLIENT_EVENT_CONFLICT",
                "A client event ID was reused with different telemetry data.");
    }

    static TelemetryException rateLimited() {
        return new TelemetryException(
                HttpStatus.TOO_MANY_REQUESTS,
                "TELEMETRY_RATE_LIMITED",
                "The telemetry device sent too many readings in the current window.");
    }
}
