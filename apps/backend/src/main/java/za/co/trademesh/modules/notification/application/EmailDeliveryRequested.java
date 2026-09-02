package za.co.trademesh.modules.notification.application;

import java.util.UUID;

public record EmailDeliveryRequested(UUID notificationId) {

    public static final String TYPE = "notification.email-delivery-requested";
    public static final int SCHEMA_VERSION = 1;
}
