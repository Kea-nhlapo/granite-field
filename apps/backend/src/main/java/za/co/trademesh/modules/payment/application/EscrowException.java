package za.co.trademesh.modules.payment.application;

import org.springframework.http.HttpStatus;

public class EscrowException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private EscrowException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    static EscrowException contextUnavailable() {
        return new EscrowException(
                "ESCROW_CONTEXT_UNAVAILABLE",
                "The confirmed delivery does not have a complete payment context",
                HttpStatus.CONFLICT);
    }

    static EscrowException verifiedPayerRequired() {
        return new EscrowException(
                "ESCROW_VERIFIED_PAYER_REQUIRED",
                "The paying business needs a verified phone number",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    public static EscrowException notFound() {
        return new EscrowException("ESCROW_NOT_FOUND", "Escrow was not found", HttpStatus.NOT_FOUND);
    }

    static EscrowException invalidCommand() {
        return new EscrowException("ESCROW_COMMAND_INVALID", "Escrow command is invalid", HttpStatus.BAD_REQUEST);
    }

    static EscrowException commandConflict() {
        return new EscrowException(
                "ESCROW_COMMAND_CONFLICT",
                "The request ID was already used with different escrow details",
                HttpStatus.CONFLICT);
    }

    static EscrowException invalidState() {
        return new EscrowException(
                "ESCROW_STATE_CONFLICT", "Escrow cannot perform that action in its current state", HttpStatus.CONFLICT);
    }

    static EscrowException releaseBlocked() {
        return new EscrowException(
                "ESCROW_RELEASE_BLOCKED",
                "Escrow release requires a clean delivery handover or a recorded dispute resolution",
                HttpStatus.CONFLICT);
    }
}
