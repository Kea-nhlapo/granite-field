package za.co.trademesh.modules.shipment.application;

import org.springframework.http.HttpStatus;

public class ShipmentException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private ShipmentException(HttpStatus status, String code, String message) {
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

    static ShipmentException invalidRequest() {
        return new ShipmentException(
                HttpStatus.BAD_REQUEST, "INVALID_SHIPMENT_REQUEST", "The shipment request is invalid.");
    }

    static ShipmentException prerequisitesNotReady() {
        return new ShipmentException(
                HttpStatus.CONFLICT,
                "SHIPMENT_PREREQUISITES_NOT_READY",
                "The demand, capacity reservation, or scored route is not ready for shipment.");
    }

    static ShipmentException notFound() {
        return new ShipmentException(HttpStatus.NOT_FOUND, "SHIPMENT_NOT_FOUND", "The shipment was not found.");
    }

    static ShipmentException requestConflict() {
        return new ShipmentException(
                HttpStatus.CONFLICT,
                "SHIPMENT_REQUEST_CONFLICT",
                "The request ID has already been used with different shipment input.");
    }

    static ShipmentException invalidTransition() {
        return new ShipmentException(
                HttpStatus.CONFLICT,
                "INVALID_SHIPMENT_TRANSITION",
                "The shipment cannot move to the requested status from its current status.");
    }

    static ShipmentException transitionConflict() {
        return new ShipmentException(
                HttpStatus.CONFLICT,
                "SHIPMENT_TRANSITION_CONFLICT",
                "The transition command ID has already been used with different input.");
    }

    static ShipmentException assignmentConflict() {
        return new ShipmentException(
                HttpStatus.CONFLICT,
                "SHIPMENT_ASSIGNMENT_CONFLICT",
                "The assignment command conflicts with shipment history or active logistics data.");
    }
}
