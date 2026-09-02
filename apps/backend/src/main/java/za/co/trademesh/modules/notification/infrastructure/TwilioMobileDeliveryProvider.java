package za.co.trademesh.modules.notification.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.notification.application.MobileDeliveryProvider;
import za.co.trademesh.modules.notification.application.MobileNotificationProperties;
import za.co.trademesh.modules.notification.application.MobileNotificationRequests;

@Component
@ConditionalOnProperty(prefix = "trademesh.notifications.mobile", name = "provider", havingValue = "twilio")
class TwilioMobileDeliveryProvider implements MobileDeliveryProvider {

    private final RestClient client;
    private final MobileNotificationProperties properties;
    private final String authorization;

    TwilioMobileDeliveryProvider(RestClient.Builder builder, MobileNotificationProperties properties) {
        if (properties.accountSid().isBlank()
                || properties.authToken().isBlank()
                || (properties.smsFrom().isBlank() && properties.whatsAppFrom().isBlank())) {
            throw new IllegalStateException("Twilio messaging credentials and at least one sender are required");
        }
        this.client = builder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
        this.authorization = "Basic "
                + Base64.getEncoder()
                        .encodeToString((properties.accountSid() + ":" + properties.authToken())
                                .getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String providerKey() {
        return "twilio";
    }

    @Override
    public String deliver(MobileMessage message) {
        boolean whatsApp = message.channel() == MobileNotificationRequests.MobileChannel.WHATSAPP;
        String sender = whatsApp ? properties.whatsAppFrom() : properties.smsFrom();
        if (sender.isBlank()) {
            throw new IllegalStateException("The selected Twilio channel has no configured sender");
        }
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", channelAddress(message.recipientPhone(), whatsApp));
        form.add("From", channelAddress(sender, whatsApp));
        form.add("Body", message.body());
        ProviderResponse response = client.post()
                .uri("/2010-04-01/Accounts/{accountSid}/Messages.json", properties.accountSid())
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(ProviderResponse.class);
        if (response == null || response.sid() == null || response.sid().isBlank()) {
            throw new IllegalStateException("Twilio returned no message ID");
        }
        return response.sid();
    }

    private static String channelAddress(String phone, boolean whatsApp) {
        return whatsApp && !phone.startsWith("whatsapp:") ? "whatsapp:" + phone : phone;
    }

    private record ProviderResponse(String sid) {}
}
