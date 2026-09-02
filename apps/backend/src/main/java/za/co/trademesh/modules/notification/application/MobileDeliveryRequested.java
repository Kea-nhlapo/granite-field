package za.co.trademesh.modules.notification.application;

public record MobileDeliveryRequested(
        String idempotencyKey,
        String protectedRecipient,
        String protectedMessage,
        MobileNotificationRequests.MobileChannel channel) {

    public static final String TYPE = "MOBILE_NOTIFICATION_DELIVERY_REQUESTED";
    public static final int SCHEMA_VERSION = 1;
}
