package za.co.trademesh.modules.payment.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.payment.application.MomoClient;
import za.co.trademesh.modules.payment.application.MomoProperties;

class HttpMomoClientTest {

    @Test
    void cachesProductTokenUntilItsRefreshWindow() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        MomoProperties properties = properties();
        HttpMomoClient client = new HttpMomoClient(builder, properties, Clock.systemUTC());

        server.expect(ExpectedCount.once(), requestTo("https://momo.test/collection/token/"))
                .andExpect(header("Ocp-Apim-Subscription-Key", "collections-subscription"))
                .andRespond(withSuccess("""
                    {"access_token":"token-value","expires_in":3600}
                    """, MediaType.APPLICATION_JSON));

        assertThat(client.getToken(MomoClient.Product.COLLECTIONS).value()).isEqualTo("token-value");
        assertThat(client.getToken(MomoClient.Product.COLLECTIONS).value()).isEqualTo("token-value");
        server.verify();
    }

    private static MomoProperties properties() {
        return new MomoProperties(
                "http",
                URI.create("https://momo.test"),
                "sandbox",
                null,
                "ZAR",
                Duration.ofSeconds(30),
                new MomoProperties.ProductCredentials(
                        "collections-subscription", "collections-user", "collections-key"),
                new MomoProperties.ProductCredentials(
                        "disbursements-subscription", "disbursements-user", "disbursements-key"));
    }
}
