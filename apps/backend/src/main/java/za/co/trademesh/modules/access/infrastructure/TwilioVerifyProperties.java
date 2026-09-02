package za.co.trademesh.modules.access.infrastructure;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.access.otp.providers.twilio")
public record TwilioVerifyProperties(URI baseUrl, String accountSid, String authToken, String verifyServiceSid) {

    public TwilioVerifyProperties {
        baseUrl = baseUrl == null ? URI.create("https://verify.twilio.com/v2") : baseUrl;
        if (!baseUrl.isAbsolute()) {
            throw new IllegalArgumentException("Twilio Verify base URL must be absolute");
        }
        accountSid = value(accountSid);
        authToken = value(authToken);
        verifyServiceSid = value(verifyServiceSid);
    }

    private static String value(String value) {
        return value == null ? "" : value.strip();
    }
}
