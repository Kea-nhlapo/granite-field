package za.co.trademesh.modules.business.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
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
class BusinessControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void exposesTheAuthenticatedRegisteredBusinessJourney() throws Exception {
        String accessToken = register("owner@example.com", RegistrationType.BUSINESS_OWNER);

        String draftJson = mockMvc.perform(post("/api/businesses/onboarding/registered")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"registrationNumber":" 2024-123456-07 "}
                    """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationNumber").value("2024/123456/07"))
                .andExpect(jsonPath("$.legalName").value("Mahlako General Trading (Pty) Ltd"))
                .andExpect(jsonPath("$.state").value("PENDING_CONFIRMATION"))
                .andExpect(jsonPath("$.trusted").value(false))
                .andExpect(jsonPath("$.businessId").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String onboardingId = JsonPath.read(draftJson, "$.onboardingId");

        mockMvc.perform(get("/api/businesses/onboarding/registered/{onboardingId}", onboardingId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trusted").value(false));

        String businessJson = mockMvc.perform(
                        post("/api/businesses/onboarding/registered/{onboardingId}/confirmation", onboardingId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationStatus").value("REGISTRY_VERIFIED"))
                .andExpect(jsonPath("$.lifecycleStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.trusted").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String businessId = JsonPath.read(businessJson, "$.businessId");

        mockMvc.perform(get("/api/businesses/{businessId}", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("2024/123456/07"));
    }

    @Test
    void rejectsInvalidNumbersAndNonOwnerAccountTypes() throws Exception {
        String ownerToken = register("owner@example.com", RegistrationType.BUSINESS_OWNER);
        String supplierToken = register("supplier@example.com", RegistrationType.SUPPLIER);

        mockMvc.perform(post("/api/businesses/onboarding/registered")
                        .header(HttpHeaders.AUTHORIZATION, bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"registrationNumber":"CIPC-2024/123456/07"}
                    """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REGISTRATION_NUMBER"));

        mockMvc.perform(post("/api/businesses/onboarding/registered")
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplierToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"registrationNumber":"2024/123456/07"}
                    """))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/businesses/onboarding/registered")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"registrationNumber":"2024/123456/07"}
                    """))
                .andExpect(status().isUnauthorized());
    }

    private String register(String email, RegistrationType registrationType) {
        return authService
                .register(email, "correct-horse-battery", registrationType)
                .accessToken();
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
