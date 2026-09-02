package za.co.trademesh.modules.notification.application;

public interface EmailDeliveryProvider {

    String providerKey();

    DeliveryResult deliver(EmailMessage message) throws EmailProviderException;

    record EmailMessage(
            String idempotencyKey, String fromAddress, String recipientEmail, String subject, String textBody) {}

    record DeliveryResult(String providerMessageId) {}
}
