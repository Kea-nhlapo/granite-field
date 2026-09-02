package za.co.trademesh.modules.notification.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import za.co.trademesh.modules.notification.application.EmailDeliveryProvider;
import za.co.trademesh.modules.notification.application.EmailProviderException;
import za.co.trademesh.modules.notification.application.NotificationEmailProperties;

@Component
@ConditionalOnProperty(prefix = "trademesh.notifications.email", name = "provider", havingValue = "http")
class HttpEmailDeliveryProvider implements EmailDeliveryProvider {

    private final RestClient client;
    private final String apiKey;

    HttpEmailDeliveryProvider(RestClient.Builder builder, NotificationEmailProperties properties) {
        if (properties.endpoint().isBlank() || properties.apiKey().isBlank()) {
            throw new IllegalStateException("HTTP email provider endpoint and API key are required");
        }
        this.client = builder.baseUrl(properties.endpoint()).build();
        this.apiKey = properties.apiKey();
    }

    @Override
    public String providerKey() {
        return "http-email";
    }

    @Override
    public DeliveryResult deliver(EmailMessage message) throws EmailProviderException {
        try {
            ProviderResponse response = client.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("Idempotency-Key", message.idempotencyKey())
                    .body(new ProviderRequest(
                            message.fromAddress(), message.recipientEmail(), message.subject(), message.textBody()))
                    .retrieve()
                    .body(ProviderResponse.class);
            if (response == null || response.id() == null || response.id().isBlank()) {
                throw new EmailProviderException(
                        "PROVIDER_RESPONSE_INVALID", "The email provider returned no message ID.", true);
            }
            return new DeliveryResult(response.id().strip());
        } catch (RestClientResponseException responseFailure) {
            int status = responseFailure.getStatusCode().value();
            boolean retryable = status == 429 || status >= 500;
            throw new EmailProviderException(
                    "PROVIDER_HTTP_" + status, "The email provider returned HTTP " + status + ".", retryable);
        } catch (ResourceAccessException connectionFailure) {
            throw new EmailProviderException("PROVIDER_UNAVAILABLE", "The email provider could not be reached.", true);
        }
    }

    private record ProviderRequest(String from, String to, String subject, String text) {}

    private record ProviderResponse(String id) {}
}
