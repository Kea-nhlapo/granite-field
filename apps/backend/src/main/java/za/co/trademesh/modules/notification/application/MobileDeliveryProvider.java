package za.co.trademesh.modules.notification.application;

public interface MobileDeliveryProvider {

    String providerKey();

    String deliver(MobileMessage message);

    record MobileMessage(
            String idempotencyKey,
            String recipientPhone,
            MobileNotificationRequests.MobileChannel channel,
            String body) {}
}
