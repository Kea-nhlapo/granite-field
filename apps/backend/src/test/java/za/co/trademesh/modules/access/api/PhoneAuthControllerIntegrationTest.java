package za.co.trademesh.modules.access.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class PhoneAuthControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanAccessTables() {
        jdbcTemplate.update("DELETE FROM access_momo_profile");
        jdbcTemplate.update("DELETE FROM access_momo_sign_in");
        jdbcTemplate.update("DELETE FROM access_otp_send_limit");
        jdbcTemplate.update("DELETE FROM access_phone_identity");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void turnstileRunsBeforeOtpAndPhoneCooldownPreventsRepeatedSends() throws Exception {
        mockMvc.perform(post("/api/auth/otp/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"phoneNumber":"+27821234567","turnstileToken":"forged"}
                    """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("BOT_CHALLENGE_FAILED"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM access_otp_send_limit", Integer.class))
                .isZero();

        sendOtp("+27821234567", challenge("otp-send"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("accepted"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM access_otp_send_limit", Integer.class))
                .isOne();

        sendOtp("+27821234567", challenge("otp-send"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("OTP_SEND_RATE_LIMITED"));
    }

    @Test
    void approvedOtpCreatesAReusablePhoneIdentityAndIssuesTheNormalSessionPair() throws Exception {
        sendOtp("+27821234568", challenge("otp-send")).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"phoneNumber":"+27821234568","code":"000000"}
                    """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.roles[0]").value("BUSINESS_OWNER"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM access_phone_identity", Integer.class))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT email IS NULL AND password_hash IS NULL FROM access_user_account", Boolean.class))
                .isTrue();
    }

    @Test
    void invalidOtpDoesNotCreateAnAccount() throws Exception {
        sendOtp("+27821234569", challenge("otp-send")).andExpect(status().isAccepted());

        mockMvc.perform(post("/api/auth/otp/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"phoneNumber":"+27821234569","code":"123456"}
                    """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OTP_INVALID"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM access_user_account", Integer.class))
                .isZero();
    }

    @Test
    void momoConsentUsesAnOpaqueSingleUsePollTokenAndPersistsVerifiedProfileData() throws Exception {
        String body = mockMvc.perform(post("/api/auth/momo/initiate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "phoneNumber":"+27821234570",
                      "turnstileToken":"%s"
                    }
                    """.formatted(challenge("momo-sign-in"))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.pollToken").isNotEmpty())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode initiated = objectMapper.readTree(body);
        String pollToken = initiated.path("pollToken").asText();

        mockMvc.perform(get("/api/auth/momo/status/{pollToken}", pollToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        mockMvc.perform(post("/api/auth/momo/userinfo/{pollToken}", pollToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.givenName").value("Demo"))
                .andExpect(jsonPath("$.tokens.accessToken").isNotEmpty());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM access_momo_profile", Integer.class))
                .isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT poll_token_hash <> ? FROM access_momo_sign_in", Boolean.class, pollToken))
                .isTrue();

        mockMvc.perform(post("/api/auth/momo/userinfo/{pollToken}", pollToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOMO_SIGN_IN_UNAVAILABLE"));
    }

    @Test
    void accountHolderFallbackNeverCreatesASessionOrAccount() throws Exception {
        mockMvc.perform(post("/api/auth/momo/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "phoneNumber":"+27821230000",
                      "turnstileToken":"%s"
                    }
                    """.formatted(challenge("momo-sign-in"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM access_user_account", Integer.class))
                .isZero();
    }

    private org.springframework.test.web.servlet.ResultActions sendOtp(String phoneNumber, String token)
            throws Exception {
        return mockMvc.perform(post("/api/auth/otp/send")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
            {"phoneNumber":"%s","turnstileToken":"%s"}
            """.formatted(phoneNumber, token)));
    }

    private static String challenge(String action) {
        return "local-pass:" + action + ":" + UUID.randomUUID();
    }
}
