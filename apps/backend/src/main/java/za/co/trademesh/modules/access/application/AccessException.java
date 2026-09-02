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
                HttpStatus.CONFLICT, "EMAIL_ALREADY_REGISTERED", "An account already exists for this email address");
    }

    public static AccessException invalidCredentials() {
        return new AccessException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect");
    }

    public static AccessException invalidRefreshToken() {
        return new AccessException(
                HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh token is invalid, expired, or already used");
    }

    public static AccessException invalidPassword() {
        return new AccessException(
                HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "Password must contain 12 to 72 UTF-8 bytes");
    }

    public static AccessException invalidExternalIdentity() {
        return new AccessException(
                HttpStatus.UNAUTHORIZED,
                "EXTERNAL_IDENTITY_INVALID",
                "The external identity could not be authenticated");
    }

    public static AccessException botChallengeFailed() {
        return new AccessException(
                HttpStatus.FORBIDDEN,
                "BOT_CHALLENGE_FAILED",
                "Complete a fresh bot-detection challenge before trying again");
    }

    public static AccessException invalidPhoneNumber() {
        return new AccessException(
                HttpStatus.BAD_REQUEST, "PHONE_NUMBER_INVALID", "Phone number must use E.164 format");
    }

    public static AccessException otpRateLimited() {
        return new AccessException(
                HttpStatus.TOO_MANY_REQUESTS,
                "OTP_SEND_RATE_LIMITED",
                "Wait before requesting another verification code");
    }

    public static AccessException otpInvalid() {
        return new AccessException(HttpStatus.UNAUTHORIZED, "OTP_INVALID", "Verification code is invalid or expired");
    }

    public static AccessException momoSignInUnavailable() {
        return new AccessException(
                HttpStatus.NOT_FOUND,
                "MOMO_SIGN_IN_UNAVAILABLE",
                "The sign-in request is invalid, expired, or already used");
    }

    public static AccessException momoConsentPending() {
        return new AccessException(
                HttpStatus.CONFLICT, "MOMO_CONSENT_PENDING", "Mobile Money consent has not been approved");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
