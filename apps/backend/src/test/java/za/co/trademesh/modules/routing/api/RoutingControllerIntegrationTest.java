package za.co.trademesh.modules.routing.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThan;
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
class RoutingControllerIntegrationTest extends PostgresIntegrationTest {

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
    void calculatesSeveralProviderNeutralRoutesAndKeepsRecalculationsImmutable() throws Exception {
        Account owner = register("route-owner@example.com");
        Account outsider = register("route-outsider@example.com");
        UUID businessId = createBusiness(owner, "2026/830001/07");
        UUID outsiderBusiness = createBusiness(outsider, "2026/830002/07");
        UUID firstRequestId = UUID.randomUUID();
        String firstRequest = requestBody(firstRequestId, null, "[]");

        String created = calculate(owner, businessId, firstRequest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.providerName").value("deterministic-mock"))
                .andExpect(jsonPath("$.providerVersion").value("mock-route/v1"))
                .andExpect(jsonPath("$.fallbackUsed").value(false))
                .andExpect(jsonPath("$.fallbackReason").doesNotExist())
                .andExpect(jsonPath("$.candidates.length()").value(3))
                .andExpect(jsonPath("$.candidates[0].label").value("FASTEST"))
                .andExpect(jsonPath("$.candidates[0].geometry.length()").value(5))
                .andExpect(jsonPath("$.candidates[0].segments.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].distanceMetres").value(greaterThan(0)))
                .andExpect(jsonPath("$.candidates[0].durationSeconds").value(greaterThan(0)))
                .andExpect(jsonPath("$.candidates[0].tollEstimateZar").value(greaterThan(0.0)))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID firstCalculationId = UUID.fromString(JsonPath.read(created, "$.calculationId"));

        calculate(owner, businessId, firstRequest)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.calculationId").value(firstCalculationId.toString()));
        calculate(owner, businessId, firstRequest.replace("-25.7479", "-25.7000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ROUTE_REQUEST_CONFLICT"));

        getCalculation(outsider, businessId, firstCalculationId).andExpect(status().isForbidden());
        getCalculation(outsider, outsiderBusiness, firstCalculationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROUTE_CALCULATION_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT ST_GeometryType(geometry) FROM routing_candidate WHERE calculation_id = ? LIMIT 1",
                        String.class,
                        firstCalculationId))
                .isEqualTo("ST_LineString");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT ST_SRID(geometry) FROM routing_candidate WHERE calculation_id = ? LIMIT 1",
                        Integer.class,
                        firstCalculationId))
                .isEqualTo(4326);

        UUID secondRequestId = UUID.randomUUID();
        String recalculated = calculate(
                        owner, businessId, requestBody(secondRequestId, firstCalculationId, "[\"TOLLS\"]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recalculationOfId").value(firstCalculationId.toString()))
                .andExpect(jsonPath("$.candidates.length()").value(3))
                .andExpect(jsonPath("$.candidates[0].tollEstimateZar").value(0.0))
                .andExpect(jsonPath("$.candidates[1].tollEstimateZar").value(0.0))
                .andExpect(jsonPath("$.candidates[2].tollEstimateZar").value(0.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID secondCalculationId = UUID.fromString(JsonPath.read(recalculated, "$.calculationId"));

        assertThat(secondCalculationId).isNotEqualTo(firstCalculationId);
        getCalculation(owner, businessId, firstCalculationId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recalculationOfId").doesNotExist())
                .andExpect(jsonPath("$.candidates[0].tollEstimateZar").value(greaterThan(0.0)));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM routing_calculation", Integer.class))
                .isEqualTo(2);
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private ResultActions calculate(Account account, UUID businessId, String body) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/routing/calculations", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getCalculation(Account account, UUID businessId, UUID calculationId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/routing/calculations/{calculationId}", businessId, calculationId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private static String requestBody(UUID requestId, UUID recalculationOfId, String avoidances) {
        String recalculation = recalculationOfId == null ? "" : "\"recalculationOfId\":\"" + recalculationOfId + "\",";
        return """
            {
              "requestId":"%s",
              %s
              "origin":{"label":"Johannesburg","latitude":-26.2041,"longitude":28.0473},
              "destination":{"label":"Pretoria","latitude":-25.7479,"longitude":28.2293},
              "waypoints":[{"label":"Midrand","latitude":-25.9992,"longitude":28.1263}],
              "vehicleLimits":{
                "maximumWeightKg":5000.000,
                "maximumHeightMetres":4.200,
                "maximumWidthMetres":2.500,
                "maximumLengthMetres":12.000
              },
              "avoidances":%s
            }
            """.formatted(requestId, recalculation, avoidances);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
