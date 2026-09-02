package za.co.trademesh.modules.handover.application;

import org.springframework.http.HttpStatus;

public class HandoverException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private HandoverException(HttpStatus status, String code, String message) {
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

    static HandoverException invalidRequest() {
        return new HandoverException(
                HttpStatus.BAD_REQUEST, "INVALID_HANDOVER_REQUEST", "The handover request is invalid.");
    }

    static HandoverException notFound() {
        return new HandoverException(HttpStatus.NOT_FOUND, "HANDOVER_NOT_FOUND", "The handover was not found.");
    }

    static HandoverException invalidToken() {
        return new HandoverException(
                HttpStatus.NOT_FOUND, "HANDOVER_TOKEN_INVALID", "The handover challenge is unavailable.");
    }

    static HandoverException activeChallengeExists() {
        return new HandoverException(
                HttpStatus.CONFLICT,
                "HANDOVER_CHALLENGE_ACTIVE",
                "An active challenge already exists for this handover.");
    }

    static HandoverException stateConflict() {
        return new HandoverException(
                HttpStatus.CONFLICT,
                "HANDOVER_SHIPMENT_STATE_CONFLICT",
                "The shipment is not in the required state for this handover.");
    }

    static HandoverException expired() {
        return new HandoverException(
                HttpStatus.GONE, "HANDOVER_CHALLENGE_EXPIRED", "The handover challenge has expired.");
    }

    static HandoverException replayed() {
        return new HandoverException(
                HttpStatus.CONFLICT,
                "HANDOVER_CHALLENGE_REPLAYED",
                "The completed handover challenge cannot be reused.");
    }

    static HandoverException participantMismatch() {
        return new HandoverException(
                HttpStatus.FORBIDDEN,
                "HANDOVER_PARTICIPANT_MISMATCH",
                "This account is not an expected participant in the handover.");
    }

    static HandoverException offlineNotAllowed() {
        return new HandoverException(
                HttpStatus.CONFLICT,
                "HANDOVER_OFFLINE_NOT_ALLOWED",
                "Offline handover confirmation is not accepted; reconnect and submit again.");
    }

    static HandoverException clockSkew() {
        return new HandoverException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "HANDOVER_CLOCK_SKEW_EXCEEDED",
                "The device time is outside the allowed handover window.");
    }

    static HandoverException outsideLocation() {
        return new HandoverException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "HANDOVER_OUTSIDE_LOCATION_TOLERANCE",
                "The confirmation location is outside the allowed handover area.");
    }

    static HandoverException partyAlreadyConfirmed() {
        return new HandoverException(
                HttpStatus.CONFLICT,
                "HANDOVER_PARTY_ALREADY_CONFIRMED",
                "This party has already confirmed the handover.");
    }

    static HandoverException commandConflict() {
        return new HandoverException(
                HttpStatus.CONFLICT,
                "HANDOVER_COMMAND_CONFLICT",
                "The command ID has already been used with different confirmation data.");
    }
}
