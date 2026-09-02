package za.co.trademesh.modules.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.notification.application.MobileDeliveryProvider;
import za.co.trademesh.modules.notification.application.MobileNotificationRequests;

class TwilioMobileDeliveryProviderTest {

    @Test
    void sendsOrdinarySmsThroughTwilioMessaging() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new TwilioMobileDeliveryProvider(builder, properties());
        server.expect(requestTo("https://twilio.test/2010-04-01/Accounts/AC123/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic QUMxMjM6c2VjcmV0"))
                .andExpect(content().string(containsString("To=%2B27821234567")))
                .andExpect(content().string(containsString("From=%2B27110000000")))
                .andExpect(content().string(containsString("Body=Shipment+started")))
                .andRespond(withSuccess("{\"sid\":\"SM-sms\"}", MediaType.APPLICATION_JSON));

        String messageId = provider.deliver(new MobileDeliveryProvider.MobileMessage(
                "sms:1", "+27821234567", MobileNotificationRequests.MobileChannel.SMS, "Shipment started"));

        assertThat(messageId).isEqualTo("SM-sms");
        server.verify();
    }

    @Test
    void addsTwilioWhatsAppAddressesForSandboxMessages() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new TwilioMobileDeliveryProvider(builder, properties());
        server.expect(requestTo("https://twilio.test/2010-04-01/Accounts/AC123/Messages.json"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("To=whatsapp%3A%2B27821234567")))
                .andExpect(content().string(containsString("From=whatsapp%3A%2B14155238886")))
                .andRespond(withSuccess("{\"sid\":\"SM-whatsapp\"}", MediaType.APPLICATION_JSON));

        String messageId = provider.deliver(new MobileDeliveryProvider.MobileMessage(
                "whatsapp:1", "+27821234567", MobileNotificationRequests.MobileChannel.WHATSAPP, "Delivery verified"));

        assertThat(messageId).isEqualTo("SM-whatsapp");
        server.verify();
    }

    private static TwilioMessagingProperties properties() {
        return new TwilioMessagingProperties(
                java.net.URI.create("https://twilio.test"), "AC123", "secret", "+27110000000", "+14155238886");
    }
}
