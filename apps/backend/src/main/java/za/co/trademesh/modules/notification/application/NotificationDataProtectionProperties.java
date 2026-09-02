package za.co.trademesh.modules.notification.application;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.notifications.security")
public record NotificationDataProtectionProperties(String dataEncryptionKey) {

    public NotificationDataProtectionProperties {
        if (dataEncryptionKey == null || dataEncryptionKey.isBlank()) {
            throw new IllegalStateException("Notification data encryption key is required");
        }
        dataEncryptionKey = dataEncryptionKey.strip();
    }
}
