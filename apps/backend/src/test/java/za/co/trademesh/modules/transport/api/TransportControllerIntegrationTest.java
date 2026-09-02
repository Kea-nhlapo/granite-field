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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import za.co.trademesh.modules.transport.application.CapacityOfferInventory;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class TransportControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private TransportService transportService;

    @Autowired
    private CapacityOfferInventory capacityInventory;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanState() {
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
    void publishesAnOwnerScopedOfferWithExplicitCapacityAndPostgisCorridor() throws Exception {
        Account owner = register("fleet-owner@example.com");
        Account outsider = register("other-fleet@example.com");
        UUID businessId = createBusiness(owner, "2026/710001/07");
        UUID outsiderBusinessId = createBusiness(outsider, "2026/710002/07");
        registerTransporter(owner, businessId, "East Rand Shared Transport").andExpect(status().isCreated());
        registerTransporter(outsider, outsiderBusinessId, "Pretoria Transport").andExpect(status().isCreated());

        UUID vehicleId = createdId(createVehicle(owner, businessId, UUID.randomUUID(), "CA 123-456"));
        UUID driverId = createdId(createDriver(owner, businessId, UUID.randomUUID(), "Thabo Driver", "DRV-100"));
        UUID assignmentId =
                createdId(assign(owner, businessId, UUID.randomUUID(), vehicleId, driverId, status().isCreated()));

        UUID requestId = UUID.randomUUID();
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(3, ChronoUnit.HOURS);
        Instant expiry = start.plus(1, ChronoUnit.HOURS);
        String body = offerBody(requestId, vehicleId, assignmentId, start, end, expiry, "1300.000", "18.500");

        String created = publishOffer(owner, businessId, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.totalCapacity.weightKg").value(1300.0))
                .andExpect(jsonPath("$.totalCapacity.volumeCubicMetres").value(18.5))
                .andExpect(jsonPath("$.remainingCapacity.weightKg").value(1300.0))
                .andExpect(jsonPath("$.routePoints.length()").value(3))
                .andExpect(jsonPath("$.routePoints[0].label").value("Johannesburg"))
                .andExpect(jsonPath("$.routePoints[2].label").value("Pretoria"))
                .andExpect(jsonPath("$.corridorRadiusMetres").value(15000))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID offerId = UUID.fromString(JsonPath.read(created, "$.id"));

        publishOffer(owner, businessId, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(offerId.toString()));

        getOffer(outsider, businessId, offerId).andExpect(status().isForbidden());
        getOffer(outsider, outsiderBusinessId, offerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAPACITY_OFFER_NOT_FOUND"));
        cancelOffer(outsider, businessId, offerId).andExpect(status().isForbidden());

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT ST_GeometryType(route_corridor::geometry) FROM transport_capacity_offer WHERE id = ?",
                        String.class,
                        offerId))
                .isEqualTo("ST_LineString");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT ST_SRID(route_corridor::geometry) FROM transport_capacity_offer WHERE id = ?",
                        Integer.class,
                        offerId))
                .isEqualTo(4326);

        cancelOffer(owner, businessId, offerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());
        cancelOffer(owner, businessId, offerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void preservesAssignmentHistoryAndPreventsTwoActiveAssignments() throws Exception {
        Account owner = register("assignments@example.com");
        UUID businessId = createBusiness(owner, "2026/720001/07");
        registerTransporter(owner, businessId, "Assignment Fleet").andExpect(status().isCreated());
        UUID vehicleId = createdId(createVehicle(owner, businessId, UUID.randomUUID(), "GP 777-777"));
        UUID firstDriver = createdId(createDriver(owner, businessId, UUID.randomUUID(), "First Driver", "DRV-201"));
        UUID secondDriver = createdId(createDriver(owner, businessId, UUID.randomUUID(), "Second Driver", "DRV-202"));

        UUID firstAssignment =
                createdId(assign(owner, businessId, UUID.randomUUID(), vehicleId, firstDriver, status().isCreated()));
        assign(owner, businessId, UUID.randomUUID(), vehicleId, secondDriver, status().isConflict())
                .andExpect(jsonPath("$.code").value("DRIVER_ASSIGNMENT_CONFLICT"));

        endAssignment(owner, businessId, firstAssignment)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        UUID secondRequestId = UUID.randomUUID();
        String second = assign(owner, businessId, secondRequestId, vehicleId, secondDriver, status().isCreated())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID secondAssignment = UUID.fromString(JsonPath.read(second, "$.id"));

        assign(owner, businessId, secondRequestId, vehicleId, secondDriver, status().isCreated())
                .andExpect(jsonPath("$.id").value(secondAssignment.toString()));
        assignmentHistory(owner, businessId, vehicleId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[0].endedAt").isNotEmpty())
                .andExpect(jsonPath("$[1].active").value(true));
    }

    @Test
    void reservesCapacityAtomicallyWithoutGoingNegative() throws Exception {
        Account owner = register("capacity@example.com");
        UUID businessId = createBusiness(owner, "2026/730001/07");
        transportService.registerTransporter(businessId, "Concurrent Fleet", owner.userId());
        var vehicle = transportService.createVehicle(
                businessId,
                new TransportService.CreateVehicle(
                        UUID.randomUUID(), "GP 100-100", "Small truck", amount("100.000"), amount("10.000")),
                owner.userId());
        var driver = transportService.createDriver(
                businessId,
                new TransportService.CreateDriver(UUID.randomUUID(), "Capacity Driver", "DRV-301"),
                owner.userId());
        var assignment = transportService.assignDriver(
                businessId,
                new TransportService.AssignDriver(UUID.randomUUID(), vehicle.id(), driver.id()),
                owner.userId());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        CapacityOffer offer = transportService.publishOffer(
                businessId,
                new TransportService.PublishCapacityOffer(
                        UUID.randomUUID(),
                        vehicle.id(),
                        assignment.id(),
                        List.of(
                                new RoutePoint(0, "Johannesburg", -26.2041, 28.0473),
                                new RoutePoint(1, "Pretoria", -25.7479, 28.2293)),
                        10_000,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        start.plus(1, ChronoUnit.HOURS),
                        List.of(CargoRestriction.NO_HAZARDOUS_GOODS),
                        new Capacity(amount("100.000"), amount("10.000"))),
                owner.userId());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(
                    () -> reserveAfterLatch(offer.id(), ready, startTogether, amount("80.000"), amount("8.000")));
            Future<Boolean> second = executor.submit(
                    () -> reserveAfterLatch(offer.id(), ready, startTogether, amount("80.000"), amount("8.000")));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }

        CapacityOffer afterReservation = transportService.getOffer(businessId, offer.id());
        assertThat(afterReservation.remainingCapacity().weightKg()).isEqualByComparingTo("20.000");
        assertThat(afterReservation.remainingCapacity().volumeCubicMetres()).isEqualByComparingTo("2.000");
        assertThat(capacityInventory.tryReserve(offer.id(), amount("20.001"), amount("1.000")))
                .isFalse();
        assertThat(capacityInventory.release(offer.id(), amount("80.000"), amount("8.000")))
                .isTrue();
        assertThat(capacityInventory.release(offer.id(), amount("80.000"), amount("8.000")))
                .isFalse();

        CapacityOffer restored = transportService.getOffer(businessId, offer.id());
        assertThat(restored.remainingCapacity()).isEqualTo(restored.totalCapacity());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT remaining_weight_kg >= 0 AND remaining_volume_cubic_metres >= 0"
                                + " FROM transport_capacity_offer WHERE id = ?",
                        Boolean.class,
                        offer.id()))
                .isTrue();
    }

    private boolean reserveAfterLatch(
            UUID offerId, CountDownLatch ready, CountDownLatch start, BigDecimal weight, BigDecimal volume)
            throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent capacity test did not start");
        }
        return capacityInventory.tryReserve(offerId, weight, volume);
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.TRANSPORTER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private ResultActions registerTransporter(Account account, UUID businessId, String displayName) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/transport/profile", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"displayName":"%s"}
                    """.formatted(displayName)));
    }

    private ResultActions createVehicle(Account account, UUID businessId, UUID requestId, String registrationNumber)
            throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/transport/vehicles", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "requestId":"%s",
                      "registrationNumber":"%s",
                      "description":"Five-ton curtain-side truck",
                      "maximumWeightKg":5000,
                      "maximumVolumeCubicMetres":40
                    }
                    """.formatted(requestId, registrationNumber)))
                .andExpect(status().isCreated());
    }

    private ResultActions createDriver(
            Account account, UUID businessId, UUID requestId, String displayName, String driverReference)
            throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/transport/drivers", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {
                      "requestId":"%s",
                      "displayName":"%s",
                      "driverReference":"%s"
                    }
                    """.formatted(requestId, displayName, driverReference)))
                .andExpect(status().isCreated());
    }

    private ResultActions assign(
            Account account,
            UUID businessId,
            UUID requestId,
            UUID vehicleId,
            UUID driverId,
            org.springframework.test.web.servlet.ResultMatcher expectedStatus)
            throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/transport/assignments", businessId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                    {"requestId":"%s","vehicleId":"%s","driverId":"%s"}
                    """.formatted(requestId, vehicleId, driverId)))
                .andExpect(expectedStatus);
    }

    private ResultActions endAssignment(Account account, UUID businessId, UUID assignmentId) throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/transport/assignments/{assignmentId}/end", businessId, assignmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions assignmentHistory(Account account, UUID businessId, UUID vehicleId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/transport/vehicles/{vehicleId}/assignments", businessId, vehicleId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions publishOffer(Account account, UUID businessId, String body) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/transport/capacity-offers", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getOffer(Account account, UUID businessId, UUID offerId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/transport/capacity-offers/{offerId}", businessId, offerId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions cancelOffer(Account account, UUID businessId, UUID offerId) throws Exception {
        return mockMvc.perform(post(
                        "/api/businesses/{businessId}/transport/capacity-offers/{offerId}/cancellation",
                        businessId,
                        offerId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private static UUID createdId(ResultActions result) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(body, "$.id"));
    }

    private static String offerBody(
            UUID requestId,
            UUID vehicleId,
            UUID assignmentId,
            Instant start,
            Instant end,
            Instant expiry,
            String weight,
            String volume) {
        return """
            {
              "requestId":"%s",
              "vehicleId":"%s",
              "driverAssignmentId":"%s",
              "routePoints":[
                {"label":"Johannesburg","latitude":-26.2041,"longitude":28.0473},
                {"label":"Kempton Park","latitude":-26.1000,"longitude":28.2333},
                {"label":"Pretoria","latitude":-25.7479,"longitude":28.2293}
              ],
              "corridorRadiusMetres":15000,
              "departureWindowStart":"%s",
              "departureWindowEnd":"%s",
              "expiresAt":"%s",
              "restrictions":["NO_HAZARDOUS_GOODS","NO_TEMPERATURE_CONTROLLED_CARGO"],
              "capacity":{"weightKg":%s,"volumeCubicMetres":%s}
            }
            """.formatted(requestId, vehicleId, assignmentId, start, end, expiry, weight, volume);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
