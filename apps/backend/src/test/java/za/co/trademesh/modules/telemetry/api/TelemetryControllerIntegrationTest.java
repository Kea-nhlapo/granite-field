package za.co.trademesh.modules.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.aggregation.application.ConsolidatedDemandCatalog;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.routing.application.RouteScoringService;
import za.co.trademesh.modules.routing.application.RoutingService;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.VehicleLimits;
import za.co.trademesh.modules.shipment.application.ShipmentService;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.telemetry.application.TelemetryService;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.shared.events.PublishedEvent;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(TelemetryControllerIntegrationTest.DemandTestConfiguration.class)
@TestPropertySource(
        properties = {"trademesh.telemetry.maximum-readings-per-window=4", "trademesh.telemetry.maximum-batch-size=4"})
class TelemetryControllerIntegrationTest extends PostgresIntegrationTest {

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
    private RoutingService routingService;

    @Autowired
    private RouteScoringService scoringService;

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private TelemetryService telemetryService;

    @Autowired
    private TestDemandCatalog demandCatalog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TelemetryEventRecorder eventRecorder;

    @BeforeEach
    @AfterEach
    void cleanState() {
        demandCatalog.clear();
        eventRecorder.clear();
        jdbcTemplate.update("DELETE FROM telemetry_live_position");
        jdbcTemplate.update("DELETE FROM telemetry_reading");
        jdbcTemplate.update("DELETE FROM telemetry_device");
        jdbcTemplate.update("DELETE FROM shipment_transition");
        jdbcTemplate.update("DELETE FROM shipment_assignment");
        jdbcTemplate.update("DELETE FROM shipment_load_cargo_item");
        jdbcTemplate.update("DELETE FROM shipment_load_order");
        jdbcTemplate.update("DELETE FROM shipment_record");
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
        jdbcTemplate.update("DELETE FROM outbox_message");
    }

    @Test
    void authenticatesDevicesIngestsBatchesAndMaintainsAnOwnerScopedLiveProjection() throws Exception {
        ShipmentSetup setup = createShipment();
        provisionWithoutHumanToken(setup.businessId(), setup.shipmentId()).andExpect(status().isUnauthorized());

        String provisioned = provision(setup.buyer(), setup.businessId(), setup.shipmentId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.credential").isString())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID deviceId = UUID.fromString(JsonPath.read(provisioned, "$.deviceId"));
        String credential = JsonPath.read(provisioned, "$.credential");
        assertThat(credential).startsWith(deviceId + ".");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT credential_hash FROM telemetry_device WHERE id = ?", String.class, deviceId))
                .doesNotContain(credential.substring(credential.indexOf('.') + 1));

        Instant newer = Instant.now().minusSeconds(1).truncatedTo(ChronoUnit.MILLIS);
        Instant older = newer.minusSeconds(30);
        UUID newerEvent = UUID.randomUUID();
        UUID olderEvent = UUID.randomUUID();
        String firstBatch = batch(
                fullReading(newerEvent, newer, -26.1000, 28.2333, "65.5", "280.0"),
                fullReading(olderEvent, older, -26.2000, 28.1000, "40.0", "282.0"));
        ingest(credential, firstBatch)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedCount").value(2))
                .andExpect(jsonPath("$.duplicateCount").value(0));

        live(setup.buyer(), setup.businessId(), setup.shipmentId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value(deviceId.toString()))
                .andExpect(jsonPath("$.latitude").value(-26.1))
                .andExpect(jsonPath("$.longitude").value(28.2333))
                .andExpect(jsonPath("$.units.speed").value("kilometres per hour"));
        history(setup.buyer(), setup.businessId(), setup.shipmentId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readings.length()").value(2))
                .andExpect(jsonPath("$.readings[0].clientEventId").value(newerEvent.toString()))
                .andExpect(jsonPath("$.readings[0].fuelLitres").value(280.0))
                .andExpect(jsonPath("$.units.temperature").value("degrees Celsius"));
        history(setup.outsider(), setup.businessId(), setup.shipmentId()).andExpect(status().isForbidden());

        ingest(credential, firstBatch)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedCount").value(0))
                .andExpect(jsonPath("$.duplicateCount").value(2));
        assertThat(readingCount(deviceId)).isEqualTo(2);
        ingest(credential, batch(fullReading(newerEvent, newer, -26.1000, 28.2333, "65.5", "279.0")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TELEMETRY_CLIENT_EVENT_CONFLICT"));
        ingest("not-a-device-credential", firstBatch)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TELEMETRY_DEVICE_AUTHENTICATION_FAILED"));
        ingest(credential, batch(speedReading(UUID.randomUUID(), Instant.now().minus(8, ChronoUnit.DAYS), "20")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TELEMETRY_READING"));

        UUID thirdEvent = UUID.randomUUID();
        UUID fourthEvent = UUID.randomUUID();
        ingest(
                        credential,
                        batch(
                                speedReading(thirdEvent, Instant.now().minusSeconds(2), "30"),
                                speedReading(fourthEvent, Instant.now().minusSeconds(1), "31")))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.acceptedCount").value(2));
        ingest(credential, batch(speedReading(UUID.randomUUID(), Instant.now(), "32")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TELEMETRY_RATE_LIMITED"));
        assertThat(eventRecorder.readings()).hasSize(4);

        Instant sampledAt = Instant.now().minus(8, ChronoUnit.DAYS).truncatedTo(ChronoUnit.MINUTES);
        jdbcTemplate.update(
                "UPDATE telemetry_reading SET recorded_at = ? WHERE client_event_id IN (?, ?, ?)",
                OffsetDateTime.ofInstant(sampledAt, ZoneOffset.UTC),
                newerEvent,
                olderEvent,
                thirdEvent);
        jdbcTemplate.update(
                "UPDATE telemetry_reading SET recorded_at = CURRENT_TIMESTAMP - INTERVAL '100 days'"
                        + " WHERE client_event_id = ?",
                fourthEvent);
        var cleanup = telemetryService.cleanUp();
        assertThat(cleanup.deletedRedundantSamples()).isEqualTo(2);
        assertThat(cleanup.markedDownsampled()).isOne();
        assertThat(cleanup.deletedExpired()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM telemetry_reading WHERE retention_tier = 'DOWNSAMPLED'", Integer.class))
                .isOne();

        revoke(setup.buyer(), setup.businessId(), deviceId).andExpect(status().isNoContent());
        ingest(credential, batch(speedReading(UUID.randomUUID(), Instant.now(), "10")))
                .andExpect(status().isUnauthorized());
    }

    private ShipmentSetup createShipment() {
        Account buyer = register("telemetry-buyer@example.com", RegistrationType.BUSINESS_OWNER);
        Account outsider = register("telemetry-outsider@example.com", RegistrationType.BUSINESS_OWNER);
        Account fleet = register("telemetry-fleet@example.com", RegistrationType.TRANSPORTER);
        UUID businessId = createBusiness(buyer, "2026/860001/07");
        UUID fleetBusinessId = createBusiness(fleet, "2026/860002/07");
        transportService.registerTransporter(fleetBusinessId, "Telemetry Fleet", fleet.userId());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID demandId = demandCatalog.add(businessId, start, start.plus(2, ChronoUnit.HOURS));
        var vehicle = transportService.createVehicle(
                fleetBusinessId,
                new TransportService.CreateVehicle(
                        UUID.randomUUID(), "GP TELEMETRY", "Telemetry truck", amount("500"), amount("50")),
                fleet.userId());
        var driver = transportService.createDriver(
                fleetBusinessId,
                new TransportService.CreateDriver(UUID.randomUUID(), "Telemetry Driver", "DRV-TELEMETRY"),
                fleet.userId());
        var assignment = transportService.assignDriver(
                fleetBusinessId,
                new TransportService.AssignDriver(UUID.randomUUID(), vehicle.id(), driver.id()),
                fleet.userId());
        var offer = transportService.publishOffer(
                fleetBusinessId,
                new TransportService.PublishCapacityOffer(
                        UUID.randomUUID(),
                        vehicle.id(),
                        assignment.id(),
                        List.of(
                                new RoutePoint(0, "Johannesburg", -26.2041, 28.0473),
                                new RoutePoint(1, "Pretoria", -25.7479, 28.2293)),
                        20_000,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        start.plus(1, ChronoUnit.HOURS),
                        List.of(CargoRestriction.NO_HAZARDOUS_GOODS),
                        new Capacity(amount("100"), amount("10"))),
                fleet.userId());
        var search = matchingService.search(
                businessId,
                new CapacityMatchingService.SearchCapacity(
                        UUID.randomUUID(),
                        demandId,
                        new Capacity(amount("80"), amount("8")),
                        List.of(CargoTrait.DRY_GOODS)),
                buyer.userId());
        var reservation = matchingService.reserve(
                businessId,
                search.id(),
                new CapacityMatchingService.ReserveCapacity(UUID.randomUUID(), offer.id()),
                buyer.userId());
        var calculation = routingService.calculate(
                businessId,
                new RoutingService.CalculateRoutes(
                        UUID.randomUUID(),
                        null,
                        new GeoPoint("Johannesburg", -26.2041, 28.0473),
                        new GeoPoint("Pretoria", -25.7479, 28.2293),
                        List.of(),
                        new VehicleLimits(amount("5000"), amount("4.2"), amount("2.5"), amount("12")),
                        List.of()),
                buyer.userId());
        var assessment = scoringService.score(
                businessId,
                calculation.id(),
                new RouteScoringService.ScoreRoutes(UUID.randomUUID(), "HIGH_VALUE_ELECTRONICS", null),
                buyer.userId());
        var shipment = shipmentService.create(
                businessId,
                new ShipmentService.CreateShipment(
                        UUID.randomUUID(),
                        demandId,
                        search.id(),
                        reservation.id(),
                        assessment.id(),
                        assessment.recommendedCandidateId(),
                        "Telemetry test shipment",
                        UUID.randomUUID()),
                buyer.userId(),
                ShipmentActionSource.OPERATIONS);
        return new ShipmentSetup(buyer, outsider, businessId, shipment.id());
    }

    private Account register(String email, RegistrationType type) {
        var tokens = authService.register(email, "correct-horse-battery", type);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private ResultActions provision(Account account, UUID businessId, UUID shipmentId) throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/shipments/{shipmentId}/telemetry-devices", businessId, shipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Truck tracker\"}"));
    }

    private ResultActions provisionWithoutHumanToken(UUID businessId, UUID shipmentId) throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/shipments/{shipmentId}/telemetry-devices", businessId, shipmentId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Truck tracker\"}"));
    }

    private ResultActions ingest(String credential, String body) throws Exception {
        return mockMvc.perform(post("/api/telemetry/readings")
                .header(TelemetryController.DEVICE_CREDENTIAL_HEADER, credential)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions live(Account account, UUID businessId, UUID shipmentId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/shipments/{shipmentId}/telemetry/live", businessId, shipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions history(Account account, UUID businessId, UUID shipmentId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/shipments/{shipmentId}/telemetry", businessId, shipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions revoke(Account account, UUID businessId, UUID deviceId) throws Exception {
        return mockMvc.perform(delete("/api/businesses/{businessId}/telemetry-devices/{deviceId}", businessId, deviceId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private int readingCount(UUID deviceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telemetry_reading WHERE device_id = ?", Integer.class, deviceId);
    }

    private static String batch(String... readings) {
        return "{\"readings\":[" + String.join(",", readings) + "]}";
    }

    private static String fullReading(
            UUID clientEventId, Instant recordedAt, double latitude, double longitude, String speed, String fuel) {
        return """
            {
              "clientEventId":"%s",
              "recordedAt":"%s",
              "latitude":%s,
              "longitude":%s,
              "speedKilometresPerHour":%s,
              "fuelLitres":%s,
              "temperatureCelsius":6.5,
              "sealOpen":false,
              "batteryPercent":82.0,
              "networkStatus":"CONNECTED",
              "networkSignalDbm":-77
            }
            """.formatted(clientEventId, recordedAt, latitude, longitude, speed, fuel);
    }

    private static String speedReading(UUID clientEventId, Instant recordedAt, String speed) {
        return """
            {"clientEventId":"%s","recordedAt":"%s","speedKilometresPerHour":%s}
            """.formatted(clientEventId, recordedAt, speed);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}

    private record ShipmentSetup(Account buyer, Account outsider, UUID businessId, UUID shipmentId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class DemandTestConfiguration {
        @Bean
        @Primary
        TestDemandCatalog testDemandCatalog() {
            return new TestDemandCatalog();
        }

        @Bean
        TelemetryEventRecorder telemetryEventRecorder() {
            return new TelemetryEventRecorder();
        }
    }

    static final class TelemetryEventRecorder {
        private final List<TelemetryEvent.ReadingAccepted> readings = new CopyOnWriteArrayList<>();

        @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
        public void record(PublishedEvent<TelemetryEvent.ReadingAccepted> event) {
            readings.add(event.event());
        }

        List<TelemetryEvent.ReadingAccepted> readings() {
            return List.copyOf(readings);
        }

        void clear() {
            readings.clear();
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
                                    stop(businessId, "Midrand", -25.9992, 28.1263, windowStart, windowEnd),
                                    stop(businessId, "Kempton Park", -26.1000, 28.2333, windowStart, windowEnd))));
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
