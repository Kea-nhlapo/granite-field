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
}
