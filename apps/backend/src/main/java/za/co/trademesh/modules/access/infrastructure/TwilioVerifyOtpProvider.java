package za.co.trademesh.modules.access.infrastructure;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import za.co.trademesh.modules.access.application.OtpProperties;
import za.co.trademesh.modules.access.application.OtpProvider;
import za.co.trademesh.modules.access.application.OtpProviderException;

@Component
@ConditionalOnProperty(prefix = "trademesh.access.otp", name = "provider", havingValue = "twilio")
class TwilioVerifyOtpProvider implements OtpProvider {

    private final RestClient client;
    private final OtpProperties properties;
    private final String authorization;

    TwilioVerifyOtpProvider(RestClient.Builder builder, OtpProperties properties) {
        requireLiveConfiguration(properties);
        this.client = builder.baseUrl(properties.baseUrl().toString()).build();
        this.properties = properties;
        this.authorization = basic(properties.accountSid(), properties.authToken());
    }

    @Override
    public void send(String phoneNumber) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneNumber);
        form.add("Channel", "sms");
        VerifyResponse response = post("/Services/{service}/Verifications", form);
        if (response == null || !"pending".equalsIgnoreCase(response.status())) {
            throw new OtpProviderException("OTP_SEND_REJECTED", "Twilio did not accept the verification", true);
        }
    }

    @Override
    public boolean verify(String phoneNumber, String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("To", phoneNumber);
        form.add("Code", code);
        try {
            VerifyResponse response = post("/Services/{service}/VerificationCheck", form);
            return response != null && "approved".equalsIgnoreCase(response.status());
        } catch (OtpProviderException failure) {
            if ("OTP_PROVIDER_HTTP_404".equals(failure.code())) {
                return false;
            }
            throw failure;
        }
    }

    private VerifyResponse post(String path, MultiValueMap<String, String> form) {
        try {
            return client.post()
                    .uri(path, properties.verifyServiceSid())
                    .header(HttpHeaders.AUTHORIZATION, authorization)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(VerifyResponse.class);
        } catch (RestClientResponseException failure) {
            int status = failure.getStatusCode().value();
            throw new OtpProviderException(
                    "OTP_PROVIDER_HTTP_" + status,
                    "Twilio Verify returned HTTP " + status,
                    status == 429 || status >= 500);
        } catch (ResourceAccessException failure) {
            throw new OtpProviderException("OTP_PROVIDER_UNAVAILABLE", "Twilio Verify could not be reached", true);
        }
    }

    private static String basic(String username, String password) {
        String value = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void requireLiveConfiguration(OtpProperties properties) {
        if (blank(properties.accountSid()) || blank(properties.authToken()) || blank(properties.verifyServiceSid())) {
            throw new IllegalStateException("Twilio account, token, and Verify service SID are required");
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record VerifyResponse(String status) {}
}
