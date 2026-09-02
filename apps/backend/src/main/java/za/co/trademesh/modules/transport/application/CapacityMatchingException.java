package za.co.trademesh.modules.transport.application;

import org.springframework.http.HttpStatus;

public class CapacityMatchingException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private CapacityMatchingException(HttpStatus status, String code, String message) {
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

    static CapacityMatchingException invalidRequest() {
        return new CapacityMatchingException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CAPACITY_MATCH_REQUEST",
                "The demand group, cargo details, or required capacity is invalid.");
    }

    static CapacityMatchingException demandNotFound() {
        return new CapacityMatchingException(
                HttpStatus.NOT_FOUND,
                "CONSOLIDATED_DEMAND_NOT_FOUND",
                "The active consolidated demand group was not found.");
    }

    static CapacityMatchingException demandWindowConflict() {
        return new CapacityMatchingException(
                HttpStatus.CONFLICT,
                "DEMAND_DELIVERY_WINDOWS_DO_NOT_OVERLAP",
                "The orders no longer share a usable delivery window.");
    }

    static CapacityMatchingException searchNotFound() {
        return new CapacityMatchingException(
                HttpStatus.NOT_FOUND, "CAPACITY_MATCH_NOT_FOUND", "The capacity match search was not found.");
    }

    static CapacityMatchingException requestConflict() {
        return new CapacityMatchingException(
                HttpStatus.CONFLICT,
                "CAPACITY_MATCH_REQUEST_CONFLICT",
                "The request ID has already been used with different matching input.");
    }

    static CapacityMatchingException candidateNotReservable() {
        return new CapacityMatchingException(
                HttpStatus.CONFLICT,
                "CAPACITY_CANDIDATE_NOT_RESERVABLE",
                "The selected offer did not pass every hard check or is no longer available.");
    }

    static CapacityMatchingException reservationConflict() {
        return new CapacityMatchingException(
                HttpStatus.CONFLICT,
                "CAPACITY_RESERVATION_CONFLICT",
                "The match already has a different reservation or can no longer be reserved.");
    }

    static CapacityMatchingException releaseConflict() {
        return new CapacityMatchingException(
                HttpStatus.CONFLICT,
                "CAPACITY_RELEASE_CONFLICT",
                "The reserved capacity could not be returned safely.");
    }
}
