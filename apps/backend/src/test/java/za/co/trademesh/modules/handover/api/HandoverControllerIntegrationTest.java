package za.co.trademesh.modules.handover.api;

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
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(HandoverControllerIntegrationTest.DemandTestConfiguration.class)
class HandoverControllerIntegrationTest extends PostgresIntegrationTest {

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
    private TestDemandCatalog demandCatalog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanState() {
        demandCatalog.clear();
        jdbcTemplate.update("DELETE FROM handover_attempt");
        jdbcTemplate.update("DELETE FROM handover_confirmation");
        jdbcTemplate.update("DELETE FROM handover_challenge");
        jdbcTemplate.update("DELETE FROM risk_indicator_transition");
        jdbcTemplate.update("DELETE FROM risk_indicator_evidence");
        jdbcTemplate.update("DELETE FROM risk_indicator");
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
    void verifiesCollectionAndDisputedDeliveryThroughOpaqueOneTimeChallenges() throws Exception {
        ShipmentSetup setup = createShipment();
        String collectionIssue = """
            {"type":"COLLECTION","counterpartyUserId":"%s"}
            """.formatted(setup.counterparty().userId());
        String issued = mockMvc.perform(post(
                                "/api/businesses/{businessId}/shipments/{shipmentId}/handovers/challenges",
                                setup.businessId(),
                                setup.shipmentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.buyer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(collectionIssue))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challenge.state").value("PENDING"))
                .andExpect(jsonPath("$.challenge.type").value("COLLECTION"))
                .andExpect(jsonPath("$.challenge.deliveryOrderId").doesNotExist())
                .andExpect(jsonPath("$.challenge.expectedLocation.latitude").value(-26.2041))
                .andExpect(jsonPath("$.challenge.confirmations.length()").value(0))
                .andExpect(jsonPath("$.challenge.nonceHash").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID challengeId = UUID.fromString(JsonPath.read(issued, "$.challenge.challengeId"));
        String qrPayload = JsonPath.read(issued, "$.qrPayload");
        assertThat(qrPayload)
                .startsWith("tmh_")
                .doesNotContain(setup.shipmentId().toString());
        String storedHash = jdbcTemplate.queryForObject(
                "SELECT nonce_hash FROM handover_challenge WHERE id = ?", String.class, challengeId);
        assertThat(storedHash).hasSize(64).isNotEqualTo(qrPayload);

        mockMvc.perform(get(
                                "/api/businesses/{businessId}/shipments/{shipmentId}/handovers/challenges/{challengeId}",
                                setup.businessId(),
                                setup.shipmentId(),
                                challengeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.buyer())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.qrPayload").doesNotExist())
                .andExpect(jsonPath("$.nonceHash").doesNotExist());

        mockMvc.perform(get(
                                "/api/businesses/{businessId}/shipments/{shipmentId}/handovers/challenges/{challengeId}",
                                setup.businessId(),
                                setup.shipmentId(),
                                challengeId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.outsider())))
                .andExpect(status().isForbidden());

        String outsiderAttempt =
                confirmationBody(UUID.randomUUID(), qrPayload, -26.2041, 28.0473, "MATCHED", "Not a participant");
        mockMvc.perform(post("/api/handovers/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.outsider()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(outsiderAttempt))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("HANDOVER_PARTICIPANT_MISMATCH"));

        UUID firstCommand = UUID.randomUUID();
        String firstConfirmation =
                confirmationBody(firstCommand, qrPayload, -26.2041, 28.0473, "MATCHED", "20 cases collected");
        for (int retry = 0; retry < 2; retry++) {
            mockMvc.perform(post("/api/handovers/confirmations")
                            .header(HttpHeaders.AUTHORIZATION, bearer(setup.buyer()))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(firstConfirmation))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("PENDING"))
                    .andExpect(jsonPath("$.confirmations.length()").value(1));
        }
        mockMvc.perform(post("/api/handovers/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.counterparty()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(
                                UUID.randomUUID(),
                                qrPayload,
                                -26.2041,
                                28.0473,
                                "MATCHED",
                                "Supplier agrees: 20 cases")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.confirmations.length()").value(2));
        assertThat(shipmentStatus(setup.shipmentId())).isEqualTo("COLLECTED");

        mockMvc.perform(post("/api/handovers/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.counterparty()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                confirmationBody(UUID.randomUUID(), qrPayload, -26.2041, 28.0473, "MATCHED", "Replay")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("HANDOVER_CHALLENGE_REPLAYED"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM handover_attempt WHERE challenge_id = ?", Integer.class, challengeId))
                .isEqualTo(4);

        shipmentService.transition(
                setup.businessId(),
                setup.shipmentId(),
                new ShipmentService.TransitionShipment(
                        UUID.randomUUID(), ShipmentStatus.IN_TRANSIT, "Vehicle departed", UUID.randomUUID()),
                setup.buyer().userId(),
                ShipmentActionSource.OPERATIONS);
        String deliveryIssue =
                """
            {"type":"DELIVERY","deliveryOrderId":"%s","counterpartyUserId":"%s"}
            """.formatted(setup.deliveryOrderId(), setup.counterparty().userId());
        String delivery = mockMvc.perform(post(
                                "/api/businesses/{businessId}/shipments/{shipmentId}/handovers/challenges",
                                setup.businessId(),
                                setup.shipmentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.buyer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(deliveryIssue))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challenge.type").value("DELIVERY"))
                .andExpect(jsonPath("$.challenge.deliveryOrderId")
                        .value(setup.deliveryOrderId().toString()))
                .andExpect(jsonPath("$.challenge.expectedLocation.label").value("Midrand"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String deliveryToken = JsonPath.read(delivery, "$.qrPayload");
        mockMvc.perform(post("/api/handovers/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.buyer()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(
                                UUID.randomUUID(),
                                deliveryToken,
                                -25.9992,
                                28.1263,
                                "MATCHED",
                                "Expected quantity received")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PENDING"));
        mockMvc.perform(post("/api/handovers/confirmations")
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.counterparty()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmationBody(
                                UUID.randomUUID(),
                                deliveryToken,
                                -25.9992,
                                28.1263,
                                "DISPUTED",
                                "Receiver counted 19 cases")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DISPUTED"))
                .andExpect(jsonPath("$.confirmations[1].quantityNote").value("Receiver counted 19 cases"));
        assertThat(shipmentStatus(setup.shipmentId())).isEqualTo("DISPUTED");
    }

    private ShipmentSetup createShipment() {
        Account buyer = register("handover-buyer@example.com", RegistrationType.BUSINESS_OWNER);
        Account fleet = register("handover-fleet@example.com", RegistrationType.TRANSPORTER);
        Account counterparty = register("handover-supplier@example.com", RegistrationType.SUPPLIER);
        Account outsider = register("handover-outsider@example.com", RegistrationType.BUSINESS_OWNER);
        UUID businessId = createBusiness(buyer, "2026/880001/07");
        UUID fleetBusinessId = createBusiness(fleet, "2026/880002/07");
        createBusiness(outsider, "2026/880003/07");
        transportService.registerTransporter(fleetBusinessId, "Handover Fleet", fleet.userId());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        DemandFixture demand = demandCatalog.add(businessId, start, start.plus(2, ChronoUnit.HOURS));
        var vehicle = transportService.createVehicle(
                fleetBusinessId,
                new TransportService.CreateVehicle(
                        UUID.randomUUID(), "GP HAND 01", "Handover truck", amount("500"), amount("50")),
                fleet.userId());
        var driver = transportService.createDriver(
                fleetBusinessId,
                new TransportService.CreateDriver(UUID.randomUUID(), "Handover Driver", "DRV-HAND"),
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
                        demand.suggestionId(),
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
                        demand.suggestionId(),
                        search.id(),
                        reservation.id(),
                        assessment.id(),
                        assessment.recommendedCandidateId(),
                        "Handover test shipment",
                        UUID.randomUUID()),
                buyer.userId(),
                ShipmentActionSource.OPERATIONS);
        return new ShipmentSetup(buyer, counterparty, outsider, businessId, shipment.id(), demand.deliveryOrderId());
    }

    private Account register(String email, RegistrationType type) {
        var tokens = authService.register(email, "correct-horse-battery", type);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private String shipmentStatus(UUID shipmentId) {
        return jdbcTemplate.queryForObject("SELECT status FROM shipment_record WHERE id = ?", String.class, shipmentId);
    }

    private static String confirmationBody(
            UUID commandId,
            String token,
            double latitude,
            double longitude,
            String quantityOutcome,
            String quantityNote) {
        return """
            {
              "commandId":"%s",
              "qrPayload":"%s",
              "captureMode":"ONLINE",
              "observedAt":"%s",
              "latitude":%s,
              "longitude":%s,
              "quantityOutcome":"%s",
              "quantityNote":"%s"
            }
            """.formatted(
                        commandId,
                        token,
                        Instant.now().truncatedTo(ChronoUnit.SECONDS),
                        latitude,
                        longitude,
                        quantityOutcome,
                        quantityNote);
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}

    private record DemandFixture(UUID suggestionId, UUID deliveryOrderId) {}

    private record ShipmentSetup(
            Account buyer,
            Account counterparty,
            Account outsider,
            UUID businessId,
            UUID shipmentId,
            UUID deliveryOrderId) {}

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

        DemandFixture add(UUID businessId, Instant windowStart, Instant windowEnd) {
            UUID suggestionId = UUID.randomUUID();
            UUID deliveryOrderId = UUID.randomUUID();
            demands.put(
                    suggestionId,
                    new ConsolidatedDemand(
                            suggestionId,
                            businessId,
                            UUID.randomUUID(),
                            List.of(
                                    stop(
                                            deliveryOrderId,
                                            businessId,
                                            "Midrand",
                                            -25.9992,
                                            28.1263,
                                            windowStart,
                                            windowEnd),
                                    stop(
                                            UUID.randomUUID(),
                                            businessId,
                                            "Kempton Park",
                                            -26.1000,
                                            28.2333,
                                            windowStart,
                                            windowEnd))));
            return new DemandFixture(suggestionId, deliveryOrderId);
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
                UUID orderId,
                UUID businessId,
                String label,
                double latitude,
                double longitude,
                Instant windowStart,
                Instant windowEnd) {
            return new DeliveryStop(
                    orderId,
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
