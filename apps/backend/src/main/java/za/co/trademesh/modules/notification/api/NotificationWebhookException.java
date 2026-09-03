package za.co.trademesh.modules.notification.api;

import org.springframework.http.HttpStatus;

final class NotificationWebhookException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    private NotificationWebhookException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    static NotificationWebhookException unauthorized() {
        return new NotificationWebhookException(
                HttpStatus.UNAUTHORIZED, "INFOBIP_SIGNATURE_INVALID", "The provider signature is invalid.");
    }

    static NotificationWebhookException invalid() {
        return new NotificationWebhookException(
                HttpStatus.BAD_REQUEST, "INFOBIP_CALLBACK_INVALID", "The provider callback is invalid.");
    }

    static NotificationWebhookException tooLarge() {
        return new NotificationWebhookException(
                HttpStatus.PAYLOAD_TOO_LARGE,
                "INFOBIP_CALLBACK_TOO_LARGE",
                "The provider callback exceeds the maximum size.");
    }

    HttpStatus status() {
        return status;
    }

    String code() {
        return code;
    }
}
