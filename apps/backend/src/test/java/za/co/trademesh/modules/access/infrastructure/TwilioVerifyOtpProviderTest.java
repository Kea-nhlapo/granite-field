package za.co.trademesh.modules.access.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.access.application.OtpProperties;

class TwilioVerifyOtpProviderTest {

    @Test
    void sendsAndChecksCodesThroughTheConfiguredVerifyService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TwilioVerifyOtpProvider provider = new TwilioVerifyOtpProvider(builder, properties());

        server.expect(requestTo("https://verify.test/v2/Services/VA123/Verifications"))
                .andRespond(withSuccess("""
                    {"status":"pending"}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://verify.test/v2/Services/VA123/VerificationCheck"))
                .andRespond(withSuccess("""
                    {"status":"approved"}
                    """, MediaType.APPLICATION_JSON));

        provider.send("+27821234567");
        assertThat(provider.verify("+27821234567", "123456")).isTrue();
        server.verify();
    }

    private static OtpProperties properties() {
        return new OtpProperties(
                "twilio", URI.create("https://verify.test/v2"), "AC123", "secret", "VA123", "", Duration.ofMinutes(1));
    }
}
