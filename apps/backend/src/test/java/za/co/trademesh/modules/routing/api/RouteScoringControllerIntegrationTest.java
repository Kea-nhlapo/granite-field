package za.co.trademesh.modules.routing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.web.servlet.ResultActions;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class RouteScoringControllerIntegrationTest extends PostgresIntegrationTest {

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
    void cleanState() {
        jdbcTemplate.update("DELETE FROM routing_candidate_reason");
        jdbcTemplate.update("DELETE FROM routing_candidate_option");
        jdbcTemplate.update("DELETE FROM routing_factor_score");
        jdbcTemplate.update("DELETE FROM routing_candidate_score");
        jdbcTemplate.update("DELETE FROM routing_assessment_weight");
        jdbcTemplate.update("DELETE FROM routing_assessment");
        jdbcTemplate.update("DELETE FROM routing_segment");
        jdbcTemplate.update("DELETE FROM routing_candidate");
        jdbcTemplate.update("DELETE FROM routing_avoidance");
        jdbcTemplate.update("DELETE FROM routing_waypoint");
        jdbcTemplate.update("DELETE FROM routing_calculation");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void scoresEveryFactorExplainsUncertaintyAndChangesRecommendationWithWeights() throws Exception {
        Account owner = register("score-owner@example.com");
        UUID businessId = createBusiness(owner, "2026/840001/07");
        UUID calculationId = createCalculation(owner, businessId);

        String highValue = score(
                        owner, businessId, calculationId, scoreBody(UUID.randomUUID(), "HIGH_VALUE_ELECTRONICS", ""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cargoProfile").value("HIGH_VALUE_ELECTRONICS"))
                .andExpect(jsonPath("$.algorithmVersion").value("route-score/v1"))
                .andExpect(jsonPath("$.scoreScale").value("0 is best; 1 is worst"))
                .andExpect(jsonPath("$.weights.SAFETY_EXPOSURE").value(0.35))
                .andExpect(jsonPath("$.options.FASTEST").exists())
                .andExpect(jsonPath("$.options.LOWEST_COST").exists())
                .andExpect(jsonPath("$.options.SAFEST").exists())
                .andExpect(jsonPath("$.options.BEST_CONNECTIVITY").exists())
                .andExpect(jsonPath("$.options.RECOMMENDED").exists())
                .andExpect(jsonPath("$.candidates.length()").value(3))
                .andExpect(jsonPath("$.candidates[0].factors.length()").value(7))
                .andExpect(jsonPath("$.candidates[1].factors[5].factor").value("ROAD_QUALITY"))
                .andExpect(jsonPath("$.candidates[1].factors[5].dataAvailable").value(false))
                .andExpect(jsonPath("$.candidates[1].factors[5].rawValue").doesNotExist())
                .andExpect(
                        jsonPath("$.candidates[1].factors[5].normalizedValue").value(0.85))
                .andExpect(jsonPath("$.candidates[1].confidence").value(0.85))
                .andExpect(jsonPath("$.candidates[1].reasons[0]").isNotEmpty())
                .andExpect(jsonPath("$.candidates[2].options", hasItem("RECOMMENDED")))
                .andExpect(jsonPath("$.candidates[2].reasons[0]")
                        .value("Best weighted fit for the high value electronics profile."))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID highValueAssessmentId = UUID.fromString(JsonPath.read(highValue, "$.assessmentId"));
        String highValueRecommended = JsonPath.read(highValue, "$.recommendedCandidateId");

        String timeOnlyOverrides = """
            ,"weightOverrides":{
              "TIME":1,"DISTANCE":0,"FUEL":0,"TOLLS":0,
              "SAFETY_EXPOSURE":0,"ROAD_QUALITY":0,"CONNECTIVITY":0
            }
            """;
        String timeOnly = score(
                        owner,
                        businessId,
                        calculationId,
                        scoreBody(UUID.randomUUID(), "HIGH_VALUE_ELECTRONICS", timeOnlyOverrides))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.weights.TIME").value(1.0))
                .andExpect(jsonPath("$.options.FASTEST")
                        .value(org.hamcrest.Matchers.equalTo(JsonPath.read(highValue, "$.options.FASTEST"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(timeOnly, "$.recommendedCandidateId"))
                .isEqualTo(JsonPath.read(timeOnly, "$.options.FASTEST"))
                .isNotEqualTo(highValueRecommended);

        getAssessment(owner, businessId, highValueAssessmentId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendedCandidateId").value(highValueRecommended));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM routing_factor_score", Integer.class))
                .isEqualTo(42);
    }

    @Test
    void keepsScoreRequestsIdempotentTenantScopedAndRejectsUnknownProfiles() throws Exception {
        Account owner = register("score-idempotent@example.com");
        Account outsider = register("score-outsider@example.com");
        UUID businessId = createBusiness(owner, "2026/840002/07");
        UUID outsiderBusinessId = createBusiness(outsider, "2026/840003/07");
        UUID calculationId = createCalculation(owner, businessId);
        UUID requestId = UUID.randomUUID();
        String body = scoreBody(requestId, "BALANCED", "");

        String first = score(owner, businessId, calculationId, body)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID assessmentId = UUID.fromString(JsonPath.read(first, "$.assessmentId"));
        score(owner, businessId, calculationId, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assessmentId").value(assessmentId.toString()));
        score(owner, businessId, calculationId, scoreBody(requestId, "LOW_VALUE_DRY_GOODS", ""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_SCORE_REQUEST_CONFLICT"));
        score(owner, businessId, calculationId, scoreBody(UUID.randomUUID(), "NOT_CONFIGURED", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_CARGO_PROFILE"));

        getAssessment(outsider, businessId, assessmentId).andExpect(status().isForbidden());
        getAssessment(outsider, outsiderBusinessId, assessmentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_ASSESSMENT_NOT_FOUND"));
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private UUID createCalculation(Account account, UUID businessId) throws Exception {
        String response = mockMvc.perform(post("/api/businesses/{businessId}/routing/calculations", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(routeBody(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.calculationId"));
    }

    private ResultActions score(Account account, UUID businessId, UUID calculationId, String body) throws Exception {
        return mockMvc.perform(post(
                        "/api/businesses/{businessId}/routing/calculations/{calculationId}/assessments",
                        businessId,
                        calculationId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getAssessment(Account account, UUID businessId, UUID assessmentId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/routing/assessments/{assessmentId}", businessId, assessmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private static String scoreBody(UUID requestId, String profile, String extraFields) {
        return """
            {"requestId":"%s","cargoProfile":"%s"%s}
            """.formatted(requestId, profile, extraFields);
    }

    private static String routeBody(UUID requestId) {
        return """
            {
              "requestId":"%s",
              "origin":{"label":"Johannesburg","latitude":-26.2041,"longitude":28.0473},
              "destination":{"label":"Pretoria","latitude":-25.7479,"longitude":28.2293},
              "waypoints":[{"label":"Midrand","latitude":-25.9992,"longitude":28.1263}],
              "vehicleLimits":{
                "maximumWeightKg":5000.000,
                "maximumHeightMetres":4.200,
                "maximumWidthMetres":2.500,
                "maximumLengthMetres":12.000
              },
              "avoidances":[]
            }
            """.formatted(requestId);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
