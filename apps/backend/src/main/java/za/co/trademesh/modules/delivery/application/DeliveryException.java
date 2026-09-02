package za.co.trademesh.modules.delivery.application;

import org.springframework.http.HttpStatus;

public class DeliveryException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private DeliveryException(HttpStatus status, String code, String message) {
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

    public static DeliveryException invalidVoiceRequest() {
        return new DeliveryException(
                HttpStatus.BAD_REQUEST, "INVALID_VOICE_SEARCH_REQUEST", "The voice search request is invalid.");
    }

    public static DeliveryException voiceProviderUnavailable() {
        return new DeliveryException(
                HttpStatus.SERVICE_UNAVAILABLE, "VOICE_SEARCH_UNAVAILABLE", "Voice search is temporarily unavailable.");
    }

    public static DeliveryException invalidNearbySearch() {
        return new DeliveryException(
                HttpStatus.BAD_REQUEST, "INVALID_NEARBY_SUPPLIER_SEARCH", "The nearby supplier search is invalid.");
    }

    public static DeliveryException distanceProviderUnavailable() {
        return new DeliveryException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "SUPPLIER_DISTANCE_UNAVAILABLE",
                "Supplier travel distances are temporarily unavailable.");
    }

    public static DeliveryException invalidProposal() {
        return new DeliveryException(
                HttpStatus.BAD_REQUEST, "INVALID_DELIVERY_PROPOSAL", "The delivery proposal is invalid.");
    }

    public static DeliveryException shipmentNotFound() {
        return new DeliveryException(
                HttpStatus.NOT_FOUND, "DELIVERY_SHIPMENT_NOT_FOUND", "The shipment was not found.");
    }

    public static DeliveryException proposalConflict() {
        return new DeliveryException(
                HttpStatus.CONFLICT,
                "DELIVERY_PROPOSAL_CONFLICT",
                "A different delivery proposal already exists for this shipment or request.");
    }

    public static DeliveryException confirmationUnavailable() {
        return new DeliveryException(
                HttpStatus.GONE,
                "DELIVERY_CONFIRMATION_UNAVAILABLE",
                "The delivery confirmation link is invalid or has expired.");
    }
}
