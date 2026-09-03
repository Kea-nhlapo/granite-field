package za.co.trademesh.modules.notification.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class NotificationContactIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM mobile_status_observation");
        jdbcTemplate.update("DELETE FROM mobile_delivery_attempt");
        jdbcTemplate.update("DELETE FROM mobile_notification_template_data");
        jdbcTemplate.update("DELETE FROM mobile_notification");
        jdbcTemplate.update("DELETE FROM notification_contact_point");
        jdbcTemplate.update("DELETE FROM notification_preference");
        String testUsers = "SELECT id FROM access_user_account WHERE email LIKE 'contact-%@example.test'";
        jdbcTemplate.update("DELETE FROM access_refresh_session WHERE user_id IN (" + testUsers + ")");
        jdbcTemplate.update("DELETE FROM access_business_membership WHERE user_id IN (" + testUsers + ")");
        jdbcTemplate.update("DELETE FROM access_user_role WHERE user_id IN (" + testUsers + ")");
        jdbcTemplate.update("DELETE FROM access_phone_identity WHERE user_id IN (" + testUsers + ")");
        jdbcTemplate.update("DELETE FROM access_momo_profile WHERE user_id IN (" + testUsers + ")");
        jdbcTemplate.update("DELETE FROM access_user_account WHERE id IN (" + testUsers + ")");
    }

    @Test
    void storesOnlyAnEncryptedPhoneAndRequiresIndependentConsentBeforeEnablingChannels() throws Exception {
        String token = register("contact-owner@example.test");

        mockMvc.perform(get("/api/notification-contacts/phone").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.maskedPhone").doesNotExist());

        mockMvc.perform(put("/api/notification-contacts/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {
                              "phoneNumber": "+27 82-123-4567",
                              "smsConsent": true,
                              "whatsappConsent": false
                            }
                            """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.maskedPhone").value("********4567"))
                .andExpect(jsonPath("$.smsConsentedAt").isNotEmpty())
                .andExpect(jsonPath("$.whatsappConsentedAt").doesNotExist());

        String protectedPhone =
                jdbcTemplate.queryForObject("SELECT protected_phone FROM notification_contact_point", String.class);
        assertThat(protectedPhone).startsWith("v1:").doesNotContain("27821234567", "4567");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT phone_fingerprint FROM notification_contact_point", String.class))
                .matches("[0-9a-f]{64}")
                .isNotEqualTo("+27821234567");

        mockMvc.perform(put("/api/notification-preferences/SHIPMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsEnabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailEnabled").value(true))
                .andExpect(jsonPath("$.smsEnabled").value(true))
                .andExpect(jsonPath("$.whatsappEnabled").value(false));

        mockMvc.perform(put("/api/notification-preferences/SHIPMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"whatsappEnabled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_CONSENT_REQUIRED"));

        mockMvc.perform(put("/api/notification-preferences/SHIPMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_PREFERENCE_EMPTY"));
    }

    @Test
    void deletingTheContactRevokesConsentAndDisablesMobilePreferences() throws Exception {
        String token = register("contact-delete@example.test");
        mockMvc.perform(put("/api/notification-contacts/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"phoneNumber":"+27829876543","smsConsent":true,"whatsappConsent":true}
                            """))
                .andExpect(status().isOk());
        mockMvc.perform(put("/api/notification-preferences/SHIPMENT_UPDATE")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"smsEnabled\":true,\"whatsappEnabled\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/notification-contacts/phone").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/notification-contacts/phone").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT sms_enabled OR whatsapp_enabled FROM notification_preference", Boolean.class))
                .isFalse();
    }

    @Test
    void rejectsInvalidNumbersAndUnauthenticatedContactAccess() throws Exception {
        String token = register("contact-invalid@example.test");

        mockMvc.perform(put("/api/notification-contacts/phone")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"phoneNumber":"082 123 4567","smsConsent":true,"whatsappConsent":true}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_PHONE_INVALID"));

        mockMvc.perform(get("/api/notification-contacts/phone")).andExpect(status().isUnauthorized());
    }

    private String register(String email) {
        return authService
                .register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER)
                .accessToken();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
