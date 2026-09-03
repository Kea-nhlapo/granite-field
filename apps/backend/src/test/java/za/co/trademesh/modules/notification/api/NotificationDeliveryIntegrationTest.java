package za.co.trademesh.modules.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.notification.application.LocalEmailCapture;
import za.co.trademesh.modules.notification.application.LocalMobileCapture;
import za.co.trademesh.modules.notification.application.NotificationRequests;
import za.co.trademesh.modules.notification.application.NotificationTemplates;
import za.co.trademesh.modules.notification.domain.MobileChannel;
import za.co.trademesh.modules.payment.events.PaymentEvent;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.DomainEvents;
import za.co.trademesh.shared.events.outbox.OutboxWorker;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@TestPropertySource(properties = "trademesh.outbox.enabled=false")
class NotificationDeliveryIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private NotificationRequests notifications;

    @Autowired
    private LocalEmailCapture emailCapture;

    @Autowired
    private LocalMobileCapture mobileCapture;

    @Autowired
    private DomainEvents domainEvents;

    @Autowired
    private OutboxWorker outboxWorker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    @AfterEach
    void cleanState() {
        emailCapture.clear();
        mobileCapture.clear();
        jdbcTemplate.update("DELETE FROM mobile_status_observation");
        jdbcTemplate.update("DELETE FROM mobile_delivery_attempt");
        jdbcTemplate.update("DELETE FROM mobile_notification_template_data");
        jdbcTemplate.update("DELETE FROM mobile_notification");
        jdbcTemplate.update("DELETE FROM email_delivery_attempt");
        jdbcTemplate.update("DELETE FROM email_notification_template_data");
        jdbcTemplate.update("DELETE FROM email_notification");
        jdbcTemplate.update("DELETE FROM notification_contact_point");
        jdbcTemplate.update("DELETE FROM notification_preference");
        jdbcTemplate.update("DELETE FROM outbox_message");
        jdbcTemplate.update("DELETE FROM supplier_invitation");
        jdbcTemplate.update("DELETE FROM supplier_profile");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_phone_identity");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void sendsSupplierInvitationsThroughVersionedIdempotentEmailDelivery() throws Exception {
        Account buyer = register("notification-buyer@example.com");
        UUID businessId = createBusiness(buyer, "2026/810001/07");
        UUID requestId = UUID.randomUUID();

        String invitation = mockMvc.perform(post("/api/businesses/{businessId}/supplier-invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"requestId":"%s","supplierEmail":"supplier@example.com"}
                    """.formatted(requestId)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String rawToken = JsonPath.read(invitation, "$.invitationToken");

        UUID notificationId = jdbcTemplate.queryForObject(
                "SELECT id FROM email_notification WHERE idempotency_key LIKE 'supplier-invitation:%'", UUID.class);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM email_notification WHERE id = ?", String.class, notificationId))
                .isEqualTo("PENDING");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT template_key FROM email_notification WHERE id = ?", String.class, notificationId))
                .isEqualTo("supplier-invitation");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT template_version FROM email_notification WHERE id = ?", Integer.class, notificationId))
                .isOne();
        String outboxPayload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_message WHERE type = 'notification.email-delivery-requested'",
                String.class);
        assertThat(outboxPayload).contains(notificationId.toString()).doesNotContain(rawToken, "supplier@example.com");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT data_value FROM email_notification_template_data WHERE notification_id = ?",
                        String.class,
                        notificationId))
                .startsWith("v1:")
                .doesNotContain(rawToken);

        assertThat(outboxWorker.pollOnce()).isOne();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM email_notification WHERE id = ?", String.class, notificationId))
                .isEqualTo("SENT");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT provider_message_id FROM email_delivery_attempt WHERE notification_id = ?",
                        String.class,
                        notificationId))
                .startsWith("local-");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM email_delivery_attempt WHERE notification_id = ?",
                        String.class,
                        notificationId))
                .isEqualTo("SENT");

        assertThat(emailCapture.capturedEmails()).hasSize(1);
        var captured = emailCapture.capturedEmails().getFirst();
        assertThat(captured.recipientEmail()).isEqualTo("supplier@example.com");
        assertThat(captured.textBody()).contains(rawToken);
        assertThat(captured.subject())
                .isEqualTo("You have a new supplier request")
                .doesNotContain(rawToken, requestId.toString());
    }

    @Test
    void storesUserPreferencesAndSuppressesOptionalEmailBeforeItReachesTheOutbox() throws Exception {
        Account user = register("preferences@example.com");

        mockMvc.perform(get("/api/notification-preferences").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences.length()").value(4))
                .andExpect(jsonPath("$.preferences[?(@.category == 'PROCUREMENT_UPDATE')].emailEnabled")
                        .value(true));
        mockMvc.perform(put("/api/notification-preferences/PROCUREMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailEnabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(false));

        var request = new NotificationRequests.EmailRequest(
                "order-confirmed:" + UUID.randomUUID(),
                user.email(),
                user.userId(),
                "PROCUREMENT_UPDATE",
                NotificationTemplates.PROCUREMENT_ORDER_CONFIRMED,
                NotificationTemplates.PROCUREMENT_ORDER_CONFIRMED_VERSION,
                Map.of(),
                false);
        UUID first = notifications.requestEmail(request);
        UUID retry = notifications.requestEmail(request);

        assertThat(retry).isEqualTo(first);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM email_notification WHERE id = ?", String.class, first))
                .isEqualTo("SUPPRESSED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_message", Integer.class))
                .isZero();
        assertThat(emailCapture.capturedEmails()).isEmpty();

        var conflicting = new NotificationRequests.EmailRequest(
                request.idempotencyKey(),
                "different@example.com",
                user.userId(),
                request.category(),
                request.templateKey(),
                request.templateVersion(),
                request.templateData(),
                request.requiredDelivery());
        assertThatThrownBy(() -> notifications.requestEmail(conflicting))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("idempotency key");
    }

    @Test
    void queuesEncryptedConsentedMobileUpdatesForOperationalEvents() throws Exception {
        Account owner = register("mobile-events@example.com");
        Account supplier = register("mobile-supplier@example.com", RegistrationType.SUPPLIER);
        UUID businessId = createBusiness(owner, "2026/810002/07");
        UUID supplierProfileId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO supplier_profile (
                id, normalized_email, profile_status, claimed_user_id, business_id, created_at, converted_at
            ) VALUES (?, ?, 'REGISTERED', ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, supplierProfileId, supplier.email(), supplier.userId());
        String ownerPhone = "+27821234567";
        String supplierPhone = "+27821234568";
        jdbcTemplate.update(
                "INSERT INTO access_phone_identity (phone_number, user_id, verification_method, verified_at) VALUES (?, ?, 'OTP', CURRENT_TIMESTAMP)",
                ownerPhone,
                owner.userId());
        jdbcTemplate.update(
                "INSERT INTO access_phone_identity (phone_number, user_id, verification_method, verified_at) VALUES (?, ?, 'OTP', CURRENT_TIMESTAMP)",
                supplierPhone,
                supplier.userId());
        mockMvc.perform(put("/api/notification-contacts/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"phoneNumber":"+27821234567","smsConsent":true,"whatsappConsent":true}
                            """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/notification-contacts/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"phoneNumber":"+27821234568","smsConsent":true,"whatsappConsent":true}
                            """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/notification-preferences/SHIPMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsEnabled\":true,\"whatsappEnabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/notification-preferences/SHIPMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsEnabled\":true,\"whatsappEnabled\":true}"))
                .andExpect(status().isOk());
        UUID shipmentId = UUID.randomUUID();
        UUID candidateShipmentId = UUID.randomUUID();
        UUID escrowId = UUID.randomUUID();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            domainEvents.publish(new TelemetryEvent.BackhaulMatchesFound(
                    shipmentId, businessId, candidateShipmentId, 2, 3_400, new BigDecimal("0.87")));
            domainEvents.publish(new PaymentEvent.Released(
                    escrowId, shipmentId, businessId, supplierProfileId, new BigDecimal("8500.00"), "ZAR"));
        });

        var payloads = jdbcTemplate.queryForList(
                "SELECT payload::text FROM outbox_message WHERE type = 'MOBILE_NOTIFICATION_DELIVERY_REQUESTED' ORDER BY created_at",
                String.class);
        assertThat(payloads).hasSize(4).allSatisfy(payload -> assertThat(payload)
                .contains("notificationId")
                .doesNotContain(ownerPhone, supplierPhone, "Transport matches are ready", "8500.00"));

        assertThat(outboxWorker.pollOnce()).isEqualTo(4);
        assertThat(mobileCapture.capturedMessages())
                .hasSize(4)
                .extracting(LocalMobileCapture.CapturedMessage::recipientPhone)
                .containsExactlyInAnyOrder(ownerPhone, ownerPhone, supplierPhone, supplierPhone);
        assertThat(mobileCapture.capturedMessages())
                .extracting(LocalMobileCapture.CapturedMessage::channel)
                .containsExactlyInAnyOrder(
                        MobileChannel.SMS, MobileChannel.WHATSAPP, MobileChannel.SMS, MobileChannel.WHATSAPP);
        assertThat(mobileCapture.capturedMessages())
                .extracting(LocalMobileCapture.CapturedMessage::body)
                .containsOnly(
                        "Transport matches are ready. Sign in to TradeMesh to review them.",
                        "An escrow payment was released. Sign in to TradeMesh to review the transaction.");
    }

    private Account register(String email) {
        return register(email, RegistrationType.BUSINESS_OWNER);
    }

    private Account register(String email, RegistrationType registrationType) {
        var tokens = authService.register(email, "correct-horse-battery", registrationType);
        return new Account(tokens.userId(), email, tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String email, String accessToken) {}
}
