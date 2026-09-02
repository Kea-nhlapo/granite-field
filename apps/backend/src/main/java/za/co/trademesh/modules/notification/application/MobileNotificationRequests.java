package za.co.trademesh.modules.notification.application;

public interface MobileNotificationRequests {

    void requestMobile(MobileRequest request);

    default void sendSms(String idempotencyKey, String phoneNumber, String message) {
        requestMobile(new MobileRequest(idempotencyKey, phoneNumber, MobileChannel.SMS, message));
    }

    default void sendWhatsApp(String idempotencyKey, String phoneNumber, String message) {
        requestMobile(new MobileRequest(idempotencyKey, phoneNumber, MobileChannel.WHATSAPP, message));
    }

    record MobileRequest(String idempotencyKey, String recipientPhone, MobileChannel channel, String message) {}

    enum MobileChannel {
        SMS,
        WHATSAPP
    }
}
