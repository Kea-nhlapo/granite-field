package za.co.trademesh.modules.notification.application;

import java.util.UUID;

public record MobileDeliveryRequested(UUID notificationId) {

    public static final String TYPE = "MOBILE_NOTIFICATION_DELIVERY_REQUESTED";
    public static final int SCHEMA_VERSION = 2;
}
