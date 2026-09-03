package za.co.trademesh.modules.notification.application;

import java.util.UUID;

public record MobileReconciliationRequested(UUID notificationId) {

    public static final String TYPE = "MOBILE_NOTIFICATION_RECONCILIATION_REQUESTED";
    public static final int SCHEMA_VERSION = 1;
}
