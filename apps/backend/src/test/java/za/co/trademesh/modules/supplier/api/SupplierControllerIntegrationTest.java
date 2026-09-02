package za.co.trademesh.modules.supplier.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
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
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class SupplierControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanTables() {
        jdbcTemplate.update("DELETE FROM supplier_invitation");
        jdbcTemplate.update("DELETE FROM supplier_profile");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void supportsTheScopedGuestResponseAndInPlaceAccountConversionJourney() throws Exception {
        Account buyer = register("buyer@example.com", RegistrationType.BUSINESS_OWNER);
        UUID buyerBusinessId = createBusiness(buyer, "2024/111111/07");
        UUID requestId = UUID.randomUUID();

        String created = invite(buyer, buyerBusinessId, requestId, "Supplier@Example.com");
        String invitationId = JsonPath.read(created, "$.invitationId");
        String supplierProfileId = JsonPath.read(created, "$.supplierProfileId");
        String rawToken = JsonPath.read(created, "$.invitationToken");
        assertThat(rawToken).hasSize(43);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT token_hash FROM supplier_invitation WHERE id = ?", String.class, UUID.fromString(invitationId));
        assertThat(storedHash).hasSize(64).isNotEqualTo(rawToken);

        mockMvc.perform(get("/api/supplier-invitations/guest/{token}", rawToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(jsonPath("$.invitationId").value(invitationId))
                .andExpect(jsonPath("$.supplierProfileId").value(supplierProfileId))
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.purpose").value("QUOTE_RESPONSE"))
                .andExpect(jsonPath("$.supplierEmail").doesNotExist())
                .andExpect(jsonPath("$.invitationToken").doesNotExist());

        UUID responseReference = UUID.randomUUID();
        mockMvc.perform(post("/api/supplier-invitations/guest/{token}/responses", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseBody(UUID.randomUUID(), responseReference)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_INVITATION_UNAVAILABLE"));

        mockMvc.perform(post("/api/supplier-invitations/guest/{token}/responses", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseBody(requestId, responseReference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESPONDED"))
                .andExpect(jsonPath("$.responseReference").value(responseReference.toString()));

        // A browser retry with the same response is safe, while a second response is rejected.
        mockMvc.perform(post("/api/supplier-invitations/guest/{token}/responses", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseBody(requestId, responseReference)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.responseReference").value(responseReference.toString()));
        mockMvc.perform(post("/api/supplier-invitations/guest/{token}/responses", rawToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(responseBody(requestId, UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_INVITATION_UNAVAILABLE"));

        Account supplier = register("supplier@example.com", RegistrationType.SUPPLIER);
        String conversionBody = """
            {"invitationToken":"%s"}
            """.formatted(rawToken);
        mockMvc.perform(post("/api/supplier-profiles/{supplierProfileId}/conversion", supplierProfileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierProfileId").value(supplierProfileId))
                .andExpect(jsonPath("$.supplierEmail").value("supplier@example.com"))
                .andExpect(jsonPath("$.status").value("REGISTERED"))
                .andExpect(jsonPath("$.claimedUserId").value(supplier.userId().toString()));

        mockMvc.perform(post("/api/supplier-profiles/{supplierProfileId}/conversion", supplierProfileId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(supplier.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(conversionBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierProfileId").value(supplierProfileId));

        Integer profileCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM supplier_profile WHERE id = ?",
                Integer.class,
                UUID.fromString(supplierProfileId));
        assertThat(profileCount).isOne();
    }

    @Test
    void enforcesBuyerOwnershipRevocationAndGenericGuestFailures() throws Exception {
        Account buyer = register("buyer@example.com", RegistrationType.BUSINESS_OWNER);
        Account otherBuyer = register("other@example.com", RegistrationType.BUSINESS_OWNER);
        UUID buyerBusinessId = createBusiness(buyer, "2024/222222/07");
        UUID otherBusinessId = createBusiness(otherBuyer, "2024/333333/07");
        UUID requestId = UUID.randomUUID();
        String created = invite(buyer, buyerBusinessId, requestId, "supplier@example.com");
        String invitationId = JsonPath.read(created, "$.invitationId");
        String rawToken = JsonPath.read(created, "$.invitationToken");

        mockMvc.perform(post(
                                "/api/businesses/{businessId}/supplier-invitations/{invitationId}/revocation",
                                otherBusinessId,
                                invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherBuyer.accessToken())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_INVITATION_UNAVAILABLE"));

        mockMvc.perform(post(
                                "/api/businesses/{businessId}/supplier-invitations/{invitationId}/revocation",
                                buyerBusinessId,
                                invitationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.accessToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mockMvc.perform(get("/api/supplier-invitations/guest/{token}", rawToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_INVITATION_UNAVAILABLE"));
        mockMvc.perform(get("/api/supplier-invitations/guest/{token}", "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_INVITATION_UNAVAILABLE"));
    }

    @Test
    void preventsTwoTemporaryProfilesFromClaimingTheSameBusiness() throws Exception {
        Account buyer = register("buyer@example.com", RegistrationType.BUSINESS_OWNER);
        UUID businessId = createBusiness(buyer, "2024/444444/07");
        Account supplierOne = register("one@supplier.example", RegistrationType.SUPPLIER);
        Account supplierTwo = register("two@supplier.example", RegistrationType.SUPPLIER);
        grantBusinessMembership(businessId, supplierOne.userId());
        grantBusinessMembership(businessId, supplierTwo.userId());

        String first = invite(buyer, businessId, UUID.randomUUID(), "one@supplier.example");
        String second = invite(buyer, businessId, UUID.randomUUID(), "two@supplier.example");

        convertWithBusiness(first, supplierOne, businessId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.businessId").value(businessId.toString()));
        convertWithBusiness(second, supplierTwo, businessId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUPPLIER_BUSINESS_ALREADY_CLAIMED"));
    }

    @Test
    void marksExpiredTokensUnavailableWithoutRevealingTheirPreviousValidity() throws Exception {
        Account buyer = register("buyer@example.com", RegistrationType.BUSINESS_OWNER);
        UUID businessId = createBusiness(buyer, "2024/555555/07");
        String created = invite(buyer, businessId, UUID.randomUUID(), "supplier@example.com");
        UUID invitationId = UUID.fromString(JsonPath.read(created, "$.invitationId"));
        String rawToken = JsonPath.read(created, "$.invitationToken");

        jdbcTemplate.update("""
            UPDATE supplier_invitation
            SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',
                expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'
            WHERE id = ?
            """, invitationId);

        mockMvc.perform(get("/api/supplier-invitations/guest/{token}", rawToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_INVITATION_UNAVAILABLE"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM supplier_invitation WHERE id = ?", String.class, invitationId))
                .isEqualTo("EXPIRED");
    }

    private String invite(Account buyer, UUID businessId, UUID requestId, String supplierEmail) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/supplier-invitations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(buyer.accessToken()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"requestId":"%s","supplierEmail":"%s"}
                    """.formatted(requestId, supplierEmail)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requestId").value(requestId.toString()))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions convertWithBusiness(
            String createdInvitation, Account supplier, UUID businessId) throws Exception {
        String profileId = JsonPath.read(createdInvitation, "$.supplierProfileId");
        String token = JsonPath.read(createdInvitation, "$.invitationToken");
        return mockMvc.perform(post("/api/supplier-profiles/{profileId}/conversion", profileId)
                .header(HttpHeaders.AUTHORIZATION, bearer(supplier.accessToken()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"invitationToken":"%s","businessId":"%s"}
                    """.formatted(token, businessId)));
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private Account register(String email, RegistrationType registrationType) {
        AuthService.AuthTokens tokens = authService.register(email, "correct-horse-battery", registrationType);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private void grantBusinessMembership(UUID businessId, UUID userId) {
        jdbcTemplate.update("""
            INSERT INTO access_business_membership (
                business_id, user_id, membership_role, created_at, membership_status
            ) VALUES (?, ?, 'BUSINESS_MEMBER', CURRENT_TIMESTAMP, 'ACTIVE')
            """, businessId, userId);
    }

    private static String responseBody(UUID requestId, UUID responseReference) {
        return """
            {"requestId":"%s","responseReference":"%s"}
            """.formatted(requestId, responseReference);
    }

    private static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }

    private record Account(UUID userId, String accessToken) {}
}
