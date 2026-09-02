package za.co.trademesh.modules.notification.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.notification.application.InfobipNotificationProperties;
import za.co.trademesh.modules.notification.application.MobileDeliveryProvider;
import za.co.trademesh.modules.notification.application.MobileProviderException;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

class InfobipMobileDeliveryProviderTest {

    @Test
    void createsTheBoundedProviderClientWithoutExternalBuilderConfiguration() {
        RestClient client = new InfobipRestClientConfiguration().infobipRestClient(properties());

        assertThat(client).isNotNull();
    }

    @Test
    void sendsSmsThroughTheMessagesApiWithoutLeakingConfigurationIntoThePayload() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder);
        UUID notificationId = UUID.randomUUID();
        server.expect(once(), requestTo("https://api.example.test/messages-api/1/messages"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "App test-api-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                    {
                      "messages": [{
                        "channel": "SMS",
                        "sender": "TradeMesh",
                        "destinations": [{"to": "27821234567"}],
                        "content": {"body": {"type": "TEXT", "text": "Safe text"}},
                        "messageId": "%s",
                        "callbackData": "%s",
                        "options": {"adaptationMode": false}
                      }]
                    }
                    """.formatted(notificationId, notificationId), true))
                .andRespond(withSuccess("""
                    {"messages":[{"messageId":"provider-message-1","status":{"groupName":"PENDING"}}]}
                    """, MediaType.APPLICATION_JSON));

        var result = provider.deliver(message(notificationId, MobileChannel.SMS, List.of()));

        assertThat(result.providerMessageId()).isEqualTo("provider-message-1");
        assertThat(result.status()).isEqualTo(MobileNotificationStatus.QUEUED);
        server.verify();
    }

    @Test
    void sendsWhatsAppUsingTheEnvironmentMappedApprovedTemplate() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder);
        UUID notificationId = UUID.randomUUID();
        server.expect(requestTo("https://api.example.test/messages-api/1/messages"))
                .andExpect(content().json("""
                    {
                      "messages": [{
                        "channel": "WHATSAPP",
                        "sender": "27870000000",
                        "destinations": [{"to": "27821234567"}],
                        "content": {"body": {"type": "TEXT", "1": "https://app.example.test/confirm"}},
                        "template": {"templateName": "delivery_confirmation_prod", "language": "en"}
                      }]
                    }
                    """, false))
                .andRespond(withSuccess("""
                    {"messages":[{"messageId":"provider-message-2","status":{"groupName":"ACCEPTED"}}]}
                    """, MediaType.APPLICATION_JSON));

        var source = message(notificationId, MobileChannel.WHATSAPP, List.of("https://app.example.test/confirm"));
        var result = provider.deliver(new MobileDeliveryProvider.MobileMessage(
                source.notificationId(),
                source.idempotencyKey(),
                source.recipientPhone(),
                source.channel(),
                "delivery-confirmation",
                1,
                source.text(),
                source.whatsappParameters(),
                source.whatsappLanguage()));

        assertThat(result.providerMessageId()).isEqualTo("provider-message-2");
        assertThat(result.status()).isEqualTo(MobileNotificationStatus.ACCEPTED);
        server.verify();
    }

    @Test
    void classifiesHttpFailuresWithoutReturningProviderBodies() {
        RestClient.Builder retryBuilder = RestClient.builder();
        MockRestServiceServer retryServer =
                MockRestServiceServer.bindTo(retryBuilder).build();
        var retryProvider = provider(retryBuilder);
        retryServer
                .expect(requestTo("https://api.example.test/messages-api/1/messages"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"requestError\":\"contains-sensitive-provider-data\"}"));
        assertThatThrownBy(() -> retryProvider.deliver(message(UUID.randomUUID(), MobileChannel.SMS, List.of())))
                .isInstanceOfSatisfying(MobileProviderException.class, failure -> {
                    assertThat(failure.kind()).isEqualTo(MobileProviderException.FailureKind.RETRYABLE);
                    assertThat(failure.getMessage()).doesNotContain("sensitive-provider-data");
                });

        RestClient.Builder permanentBuilder = RestClient.builder();
        MockRestServiceServer permanentServer =
                MockRestServiceServer.bindTo(permanentBuilder).build();
        var permanentProvider = provider(permanentBuilder);
        permanentServer
                .expect(requestTo("https://api.example.test/messages-api/1/messages"))
                .andRespond(withResourceNotFound());
        assertThatThrownBy(() -> permanentProvider.deliver(message(UUID.randomUUID(), MobileChannel.SMS, List.of())))
                .isInstanceOfSatisfying(MobileProviderException.class, failure -> assertThat(failure.kind())
                        .isEqualTo(MobileProviderException.FailureKind.PERMANENT));
    }

    @Test
    void reconcilesAnAmbiguousSubmissionByTheCustomMessageId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = provider(builder);
        UUID notificationId = UUID.randomUUID();
        server.expect(requestTo("https://api.example.test/messages-api/1/reports?messageID=" + notificationId))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "App test-api-key"))
                .andRespond(withSuccess("""
                    {
                      "results": [{
                        "messageId": "%s",
                        "status": {"groupName": "DELIVERED"},
                        "doneAt": "2026-09-02T17:30:00.000+0200"
                      }]
                    }
                    """.formatted(notificationId), MediaType.APPLICATION_JSON));

        var result = provider.reconcile(notificationId);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().providerMessageId()).isEqualTo(notificationId.toString());
        assertThat(result.orElseThrow().status()).isEqualTo(MobileNotificationStatus.DELIVERED);
        assertThat(result.orElseThrow().observedAt()).isEqualTo(Instant.parse("2026-09-02T15:30:00Z"));
        server.verify();
    }

    @Test
    void failsClosedForIncompleteOrNonHttpsLiveConfiguration() {
        var missing = new InfobipNotificationProperties("", "", "", "", "", Map.of(), null, null);
        assertThatThrownBy(() -> new InfobipMobileDeliveryProvider(RestClient.create(), missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Infobip");
        var insecure = new InfobipNotificationProperties(
                "http://api.example.test", "key", "sms", "+27870000000", "secret", templateMappings(), null, null);
        assertThatThrownBy(() -> new InfobipMobileDeliveryProvider(RestClient.create(), insecure))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
    }

    @Test
    void redactsProviderSecretsAndMessageContentFromRecordRepresentations() {
        var properties = properties();
        var message = message(UUID.randomUUID(), MobileChannel.SMS, List.of());

        assertThat(properties.toString())
                .doesNotContain(
                        properties.apiKey(),
                        properties.smsSender(),
                        properties.whatsappSender(),
                        properties.webhookHmacSecret());
        assertThat(message.toString()).doesNotContain(message.recipientPhone(), message.text());
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(15));
    }

    private static InfobipMobileDeliveryProvider provider(RestClient.Builder builder) {
        var properties = properties();
        return new InfobipMobileDeliveryProvider(
                builder.baseUrl(properties.baseUrl())
                        .defaultHeader("Authorization", "App " + properties.apiKey())
                        .build(),
                properties);
    }

    private static InfobipNotificationProperties properties() {
        return new InfobipNotificationProperties(
                "https://api.example.test",
                "test-api-key",
                "TradeMesh",
                "+27870000000",
                "test-webhook-secret",
                templateMappings(),
                null,
                null);
    }

    private static Map<String, String> templateMappings() {
        return Map.of(
                "capacity-match-found.v1", "capacity_match_prod",
                "handover-confirmation-accepted.v1", "handover_accepted_prod",
                "handover-finalized-clean.v1", "handover_clean_prod",
                "handover-finalized-disputed.v1", "handover_disputed_prod",
                "escrow-released.v1", "escrow_released_prod",
                "delivery-confirmation.v1", "delivery_confirmation_prod");
    }

    private static MobileDeliveryProvider.MobileMessage message(
            UUID notificationId, MobileChannel channel, List<String> parameters) {
        return new MobileDeliveryProvider.MobileMessage(
                notificationId,
                "event:" + notificationId,
                "+27821234567",
                channel,
                "capacity-match-found",
                1,
                "Safe text",
                parameters,
                "en");
    }
}
