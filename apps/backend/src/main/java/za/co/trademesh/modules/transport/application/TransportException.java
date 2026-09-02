package za.co.trademesh.modules.transport.application;

import org.springframework.http.HttpStatus;

public class TransportException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private TransportException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static TransportException transporterNotFound() {
        return new TransportException(
                HttpStatus.NOT_FOUND, "TRANSPORTER_NOT_FOUND", "The transporter profile was not found.");
    }

    static TransportException vehicleNotFound() {
        return new TransportException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "The vehicle was not found.");
    }

    static TransportException driverNotFound() {
        return new TransportException(HttpStatus.NOT_FOUND, "DRIVER_NOT_FOUND", "The driver was not found.");
    }

    static TransportException assignmentNotFound() {
        return new TransportException(
                HttpStatus.NOT_FOUND, "DRIVER_ASSIGNMENT_NOT_FOUND", "The driver assignment was not found.");
    }

    static TransportException offerNotFound() {
        return new TransportException(
                HttpStatus.NOT_FOUND, "CAPACITY_OFFER_NOT_FOUND", "The capacity offer was not found.");
    }

    static TransportException invalidAsset() {
        return new TransportException(
                HttpStatus.BAD_REQUEST,
                "INVALID_TRANSPORT_ASSET",
                "The transporter, vehicle, or driver details are invalid.");
    }

    static TransportException invalidOffer() {
        return new TransportException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CAPACITY_OFFER",
                "The route, capacity, departure window, or expiry is invalid.");
    }

    static TransportException requestConflict() {
        return new TransportException(
                HttpStatus.CONFLICT,
                "TRANSPORT_REQUEST_CONFLICT",
                "The request ID or unique asset reference has already been used.");
    }

    static TransportException assignmentConflict() {
        return new TransportException(
                HttpStatus.CONFLICT,
                "DRIVER_ASSIGNMENT_CONFLICT",
                "The driver or vehicle already has an active assignment.");
    }

    static TransportException offerStateConflict() {
        return new TransportException(
                HttpStatus.CONFLICT, "CAPACITY_OFFER_STATE_CONFLICT", "The capacity offer can no longer be changed.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
