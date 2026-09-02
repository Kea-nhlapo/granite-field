package za.co.trademesh.modules.access.infrastructure;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import za.co.trademesh.modules.access.application.BotChallengeVerifier;
import za.co.trademesh.modules.access.application.TurnstileProperties;

@Component
@ConditionalOnProperty(prefix = "trademesh.access.turnstile", name = "provider", havingValue = "cloudflare")
class CloudflareTurnstileVerifier implements BotChallengeVerifier {

    private final RestClient client;
    private final TurnstileProperties properties;

    CloudflareTurnstileVerifier(RestClient.Builder builder, TurnstileProperties properties) {
        if (properties.secretKey() == null || properties.secretKey().isBlank()) {
            throw new IllegalStateException("Cloudflare Turnstile secret key is required");
        }
        this.client = builder.baseUrl(properties.endpoint().toString()).build();
        this.properties = properties;
    }

    @Override
    public VerificationResult verify(String token, String remoteIp, String expectedAction) {
        if (token == null || token.isBlank() || token.length() > 2048) {
            return new VerificationResult(false, List.of("invalid-input-response"));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.secretKey());
        form.add("response", token);
        form.add("idempotency_key", UUID.randomUUID().toString());
        if (remoteIp != null && !remoteIp.isBlank()) {
            form.add("remoteip", remoteIp);
        }

        try {
            SiteVerifyResponse response = client.post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(SiteVerifyResponse.class);
            if (response == null || !response.success()) {
                return new VerificationResult(
                        false, response == null ? List.of("internal-error") : response.errorCodes());
            }
            if (present(properties.expectedHostname())
                    && !properties.expectedHostname().equalsIgnoreCase(response.hostname())) {
                return new VerificationResult(false, List.of("hostname-mismatch"));
            }
            if (present(response.action()) && !expectedAction.equals(response.action())) {
                return new VerificationResult(false, List.of("action-mismatch"));
            }
            return new VerificationResult(true, List.of());
        } catch (RestClientResponseException | ResourceAccessException failure) {
            return new VerificationResult(false, List.of("internal-error"));
        }
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private record SiteVerifyResponse(
            boolean success,
            String hostname,
            String action,
            @JsonProperty("error-codes") List<String> errorCodes) {}
}
