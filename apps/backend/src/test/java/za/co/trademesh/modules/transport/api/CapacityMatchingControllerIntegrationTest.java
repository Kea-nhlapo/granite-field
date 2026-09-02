package za.co.trademesh.modules.transport.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.aggregation.application.ConsolidatedDemandCatalog;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.transport.application.CapacityMatchingException;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityMatchSearch;
import za.co.trademesh.modules.transport.domain.CapacityMatchStatus;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CapacityReservation;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(CapacityMatchingControllerIntegrationTest.DemandTestConfiguration.class)
class CapacityMatchingControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private TransportService transportService;

    @Autowired
    private CapacityMatchingService matchingService;

    @Autowired
    private TestDemandCatalog demandCatalog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanState() {
        demandCatalog.clear();
        jdbcTemplate.update("DELETE FROM transport_capacity_reservation");
        jdbcTemplate.update("DELETE FROM transport_capacity_match_score_component");
        jdbcTemplate.update("DELETE FROM transport_capacity_match_constraint_result");
        jdbcTemplate.update("DELETE FROM transport_capacity_match_candidate");
        jdbcTemplate.update("DELETE FROM transport_capacity_match_cargo_trait");
        jdbcTemplate.update("DELETE FROM transport_capacity_match_search");
        jdbcTemplate.update("DELETE FROM transport_capacity_offer_restriction");
        jdbcTemplate.update("DELETE FROM transport_capacity_offer_route_point");
        jdbcTemplate.update("DELETE FROM transport_capacity_offer");
        jdbcTemplate.update("DELETE FROM transport_driver_vehicle_assignment");
        jdbcTemplate.update("DELETE FROM transport_driver");
        jdbcTemplate.update("DELETE FROM transport_vehicle");
        jdbcTemplate.update("DELETE FROM transport_transporter");
        jdbcTemplate.update("DELETE FROM access_refresh_session");
        jdbcTemplate.update("DELETE FROM access_business_membership");
        jdbcTemplate.update("DELETE FROM business_registered_onboarding");
        jdbcTemplate.update("DELETE FROM business_profile");
        jdbcTemplate.update("DELETE FROM access_user_role");
        jdbcTemplate.update("DELETE FROM access_user_account");
    }

    @Test
    void explainsHardChecksRanksCompatibleOffersAndScopesResultsToTheBuyer() throws Exception {
        Account buyer = register("buyer-matching@example.com", RegistrationType.BUSINESS_OWNER);
        Account outsider = register("matching-outsider@example.com", RegistrationType.BUSINESS_OWNER);
        Account fleet = register("matching-fleet@example.com", RegistrationType.TRANSPORTER);
        UUID buyerBusiness = createBusiness(buyer, "2026/810001/07");
        UUID outsiderBusiness = createBusiness(outsider, "2026/810002/07");
        UUID fleetBusiness = createBusiness(fleet, "2026/810003/07");
        transportService.registerTransporter(fleetBusiness, "Matching Fleet", fleet.userId());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID suggestionId = demandCatalog.add(buyerBusiness, start, start.plus(2, ChronoUnit.HOURS));
        CapacityOffer good = createOffer(
                fleet,
                fleetBusiness,
                start,
                new Capacity(amount("100.000"), amount("10.000")),
                List.of(CargoRestriction.NO_HAZARDOUS_GOODS),
                20_000,
                -26.2041,
                28.0473,
                -25.7479,
                28.2293);
        createOffer(
                fleet,
                fleetBusiness,
                start,
                new Capacity(amount("20.000"), amount("2.000")),
                List.of(CargoRestriction.DRY_GOODS_ONLY),
                2_000,
                -29.8587,
                31.0218,
                -29.6000,
                30.3794);

        UUID requestId = UUID.randomUUID();
        String requestBody = searchBody(requestId, suggestionId, "80.000", "8.000");
        String response = search(buyer, buyerBusiness, requestBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.orderCount").value(2))
                .andExpect(jsonPath("$.candidates.length()").value(2))
                .andExpect(jsonPath("$.candidates[0].offerId").value(good.id().toString()))
                .andExpect(jsonPath("$.candidates[0].compatible").value(true))
                .andExpect(jsonPath("$.candidates[0].rank").value(1))
                .andExpect(jsonPath("$.candidates[0].checks.length()").value(5))
                .andExpect(jsonPath("$.candidates[0].scoreComponents.length()").value(4))
                .andExpect(jsonPath("$.candidates[1].compatible").value(false))
                .andExpect(jsonPath("$.candidates[1].rank").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID searchId = UUID.fromString(JsonPath.read(response, "$.searchId"));

        search(buyer, buyerBusiness, requestBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.searchId").value(searchId.toString()));
        search(buyer, buyerBusiness, searchBody(requestId, suggestionId, "70.000", "7.000"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_MATCH_REQUEST_CONFLICT"));
        getSearch(outsider, buyerBusiness, searchId).andExpect(status().isForbidden());
        getSearch(outsider, outsiderBusiness, searchId).andExpect(status().isNotFound());

        UUID reservationRequest = UUID.randomUUID();
        reserve(buyer, buyerBusiness, searchId, reservationRequest, good.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.reservedCapacity.weightKg").value(80.0));
        reserve(buyer, buyerBusiness, searchId, reservationRequest, good.id())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        assertThat(remainingWeight(good.id())).isEqualByComparingTo("20.000");

        release(buyer, buyerBusiness, searchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        release(buyer, buyerBusiness, searchId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RELEASED"));
        assertThat(remainingWeight(good.id())).isEqualByComparingTo("100.000");
    }

    @Test
    void allowsOnlyOneConcurrentReservationAndReturnsExpiredCapacityExactlyOnce() throws Exception {
        Account buyer = register("concurrent-buyer@example.com", RegistrationType.BUSINESS_OWNER);
        Account fleet = register("concurrent-fleet@example.com", RegistrationType.TRANSPORTER);
        UUID buyerBusiness = createBusiness(buyer, "2026/820001/07");
        UUID fleetBusiness = createBusiness(fleet, "2026/820002/07");
        transportService.registerTransporter(fleetBusiness, "Concurrent Fleet", fleet.userId());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        CapacityOffer offer = createOffer(
                fleet,
                fleetBusiness,
                start,
                new Capacity(amount("100.000"), amount("10.000")),
                List.of(CargoRestriction.NO_HAZARDOUS_GOODS),
                20_000,
                -26.2041,
                28.0473,
                -25.7479,
                28.2293);
        UUID firstSuggestion = demandCatalog.add(buyerBusiness, start, start.plus(2, ChronoUnit.HOURS));
        UUID secondSuggestion = demandCatalog.add(buyerBusiness, start, start.plus(2, ChronoUnit.HOURS));
        CapacityMatchSearch first =
                matchingService.search(buyerBusiness, command(UUID.randomUUID(), firstSuggestion), buyer.userId());
        CapacityMatchSearch second =
                matchingService.search(buyerBusiness, command(UUID.randomUUID(), secondSuggestion), buyer.userId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Object> firstResult = executor.submit(
                    () -> reserveAfterLatch(buyerBusiness, first.id(), offer.id(), buyer.userId(), ready, go));
            Future<Object> secondResult = executor.submit(
                    () -> reserveAfterLatch(buyerBusiness, second.id(), offer.id(), buyer.userId(), ready, go));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            List<Object> results =
                    List.of(firstResult.get(10, TimeUnit.SECONDS), secondResult.get(10, TimeUnit.SECONDS));
            assertThat(results.stream()
                            .filter(CapacityReservation.class::isInstance)
                            .count())
                    .isOne();
            assertThat(results.stream()
                            .filter(CapacityMatchingException.class::isInstance)
                            .count())
                    .isOne();
        }
        assertThat(remainingWeight(offer.id())).isEqualByComparingTo("20.000");

        UUID winningSearch = jdbcTemplate.queryForObject(
                "SELECT match_search_id FROM transport_capacity_reservation WHERE status = 'ACTIVE'", UUID.class);
        jdbcTemplate.update(
                "UPDATE transport_capacity_reservation"
                        + " SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',"
                        + " expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'"
                        + " WHERE match_search_id = ?",
                winningSearch);
        assertThat(matchingService.expireDueReservations()).isOne();
        assertThat(matchingService.expireDueReservations()).isZero();
        assertThat(remainingWeight(offer.id())).isEqualByComparingTo("100.000");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transport_capacity_match_search WHERE id = ?", String.class, winningSearch))
                .isEqualTo(CapacityMatchStatus.EXPIRED.name());
    }

    private Object reserveAfterLatch(
            UUID businessId, UUID searchId, UUID offerId, UUID actorId, CountDownLatch ready, CountDownLatch go)
            throws InterruptedException {
        ready.countDown();
        if (!go.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent reservation test did not start");
        }
        try {
            return matchingService.reserve(
                    businessId,
                    searchId,
                    new CapacityMatchingService.ReserveCapacity(UUID.randomUUID(), offerId),
                    actorId);
        } catch (CapacityMatchingException failure) {
            return failure;
        }
    }

    private CapacityMatchingService.SearchCapacity command(UUID requestId, UUID suggestionId) {
        return new CapacityMatchingService.SearchCapacity(
                requestId,
                suggestionId,
                new Capacity(amount("80.000"), amount("8.000")),
                List.of(CargoTrait.DRY_GOODS));
    }

    private CapacityOffer createOffer(
            Account fleet,
            UUID businessId,
            Instant start,
            Capacity capacity,
            List<CargoRestriction> restrictions,
            int corridorRadius,
            double originLatitude,
            double originLongitude,
            double destinationLatitude,
            double destinationLongitude) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var vehicle = transportService.createVehicle(
                businessId,
                new TransportService.CreateVehicle(
                        UUID.randomUUID(),
                        "GP " + suffix,
                        "Shared capacity truck",
                        amount("500.000"),
                        amount("50.000")),
                fleet.userId());
        var driver = transportService.createDriver(
                businessId,
                new TransportService.CreateDriver(UUID.randomUUID(), "Driver " + suffix, "DRV-" + suffix),
                fleet.userId());
        var assignment = transportService.assignDriver(
                businessId,
                new TransportService.AssignDriver(UUID.randomUUID(), vehicle.id(), driver.id()),
                fleet.userId());
        return transportService.publishOffer(
                businessId,
                new TransportService.PublishCapacityOffer(
                        UUID.randomUUID(),
                        vehicle.id(),
                        assignment.id(),
                        List.of(
                                new RoutePoint(0, "Origin", originLatitude, originLongitude),
                                new RoutePoint(1, "Destination", destinationLatitude, destinationLongitude)),
                        corridorRadius,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        start.plus(1, ChronoUnit.HOURS),
                        restrictions,
                        capacity),
                fleet.userId());
    }

    private Account register(String email, RegistrationType type) {
        var tokens = authService.register(email, "correct-horse-battery", type);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private ResultActions search(Account account, UUID businessId, String body) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/logistics/capacity-matches", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getSearch(Account account, UUID businessId, UUID searchId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/logistics/capacity-matches/{searchId}", businessId, searchId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions reserve(Account account, UUID businessId, UUID searchId, UUID requestId, UUID offerId)
            throws Exception {
        return mockMvc.perform(post(
                        "/api/businesses/{businessId}/logistics/capacity-matches/{searchId}/reservations",
                        businessId,
                        searchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"requestId":"%s","offerId":"%s"}
                    """.formatted(requestId, offerId)));
    }

    private ResultActions release(Account account, UUID businessId, UUID searchId) throws Exception {
        return mockMvc.perform(post(
                        "/api/businesses/{businessId}/logistics/capacity-matches/{searchId}/reservation-release",
                        businessId,
                        searchId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private BigDecimal remainingWeight(UUID offerId) {
        return jdbcTemplate.queryForObject(
                "SELECT remaining_weight_kg FROM transport_capacity_offer WHERE id = ?", BigDecimal.class, offerId);
    }

    private static String searchBody(UUID requestId, UUID suggestionId, String weight, String volume) {
        return """
            {
              "requestId":"%s",
              "demandGroupSuggestionId":"%s",
              "requiredCapacity":{"weightKg":%s,"volumeCubicMetres":%s},
              "cargoTraits":["DRY_GOODS"]
            }
            """.formatted(requestId, suggestionId, weight, volume);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class DemandTestConfiguration {
        @Bean
        @Primary
        TestDemandCatalog testDemandCatalog() {
            return new TestDemandCatalog();
        }
    }

    static final class TestDemandCatalog implements ConsolidatedDemandCatalog {
        private final Map<UUID, ConsolidatedDemand> demands = new ConcurrentHashMap<>();

        UUID add(UUID businessId, Instant windowStart, Instant windowEnd) {
            UUID suggestionId = UUID.randomUUID();
            demands.put(
                    suggestionId,
                    new ConsolidatedDemand(
                            suggestionId,
                            businessId,
                            UUID.randomUUID(),
                            List.of(
                                    stop(businessId, "Kempton Park", -26.1000, 28.2333, windowStart, windowEnd),
                                    stop(businessId, "Midrand", -25.9992, 28.1263, windowStart, windowEnd))));
            return suggestionId;
        }

        @Override
        public Optional<ConsolidatedDemand> findActive(UUID requestedByBusinessId, UUID suggestionId) {
            return Optional.ofNullable(demands.get(suggestionId))
                    .filter(demand -> demand.requestedByBusinessId().equals(requestedByBusinessId));
        }

        void clear() {
            demands.clear();
        }

        private static DeliveryStop stop(
                UUID businessId,
                String label,
                double latitude,
                double longitude,
                Instant windowStart,
                Instant windowEnd) {
            return new DeliveryStop(
                    UUID.randomUUID(),
                    businessId,
                    label,
                    latitude,
                    longitude,
                    windowStart,
                    windowEnd,
                    List.of(new CargoItem("SKU-DRY-01", "CASE")));
        }
    }
}
