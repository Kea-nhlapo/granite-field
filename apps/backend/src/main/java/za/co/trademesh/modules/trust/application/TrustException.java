package za.co.trademesh.modules.trust.application;

import org.springframework.http.HttpStatus;

public final class TrustException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    private TrustException(String code, HttpStatus status, String message) {
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

    static TrustException businessNotFound() {
        return new TrustException("TRUST_BUSINESS_NOT_FOUND", HttpStatus.NOT_FOUND, "The business was not found");
    }

    static TrustException scoreNotFound() {
        return new TrustException("TRUST_SCORE_NOT_FOUND", HttpStatus.NOT_FOUND, "A trust score is not available");
    }

    static TrustException premiumUnavailable() {
        return new TrustException(
                "PREMIUM_ESTIMATE_UNAVAILABLE",
                HttpStatus.NOT_FOUND,
                "A premium estimate is not available for this delivery");
    }
}
