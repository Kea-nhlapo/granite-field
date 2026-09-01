package za.co.trademesh.modules.access.application;

import org.springframework.http.HttpStatus;

public class AccessException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private AccessException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static AccessException emailAlreadyRegistered() {
        return new AccessException(
            HttpStatus.CONFLICT,
            "EMAIL_ALREADY_REGISTERED",
            "An account already exists for this email address");
    }

    public static AccessException invalidCredentials() {
        return new AccessException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_CREDENTIALS",
            "Email or password is incorrect");
    }

    public static AccessException invalidRefreshToken() {
        return new AccessException(
            HttpStatus.UNAUTHORIZED,
            "INVALID_REFRESH_TOKEN",
            "Refresh token is invalid, expired, or already used");
    }

    public static AccessException invalidPassword() {
        return new AccessException(
            HttpStatus.BAD_REQUEST,
            "INVALID_PASSWORD",
            "Password must contain 12 to 72 UTF-8 bytes");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
