package za.co.trademesh.modules.notification.application;

import org.springframework.http.HttpStatus;

public final class NotificationException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private NotificationException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static NotificationException invalidPhone() {
        return new NotificationException(
                HttpStatus.BAD_REQUEST,
                "NOTIFICATION_PHONE_INVALID",
                "The phone number must use international E.164 format.");
    }

    public static NotificationException emptyPreference() {
        return new NotificationException(
                HttpStatus.BAD_REQUEST,
                "NOTIFICATION_PREFERENCE_EMPTY",
                "At least one notification preference must be supplied.");
    }

    public static NotificationException consentRequired() {
        return new NotificationException(
                HttpStatus.BAD_REQUEST,
                "NOTIFICATION_CONSENT_REQUIRED",
                "A consented phone contact is required before enabling this channel.");
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
