package za.co.trademesh.modules.notification.application;

public interface MobileNotificationRequests {

    void requestMobile(MobileRequest request);

    record MobileRequest(String idempotencyKey, String recipientPhone, MobileChannel channel, String message) {}

    enum MobileChannel {
        SMS,
        WHATSAPP
    }
}
