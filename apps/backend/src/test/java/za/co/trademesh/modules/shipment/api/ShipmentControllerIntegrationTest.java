package za.co.trademesh.modules.shipment.api;

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
import org.springframework.test.web.servlet.ResultActions;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.aggregation.application.ConsolidatedDemandCatalog;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.routing.application.RouteScoringService;
import za.co.trademesh.modules.routing.application.RoutingService;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAssessment;
import za.co.trademesh.modules.routing.domain.VehicleLimits;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CapacityReservation;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.DriverVehicleAssignment;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(ShipmentControllerIntegrationTest.DemandTestConfiguration.class)
class ShipmentControllerIntegrationTest extends PostgresIntegrationTest {

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
    private TestDemandCatalog demandCatalog;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void cleanState() {
        demandCatalog.clear();
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
    }

    @Test
    void createsFromApprovedInputsControlsLifecycleAndKeepsAssignmentHistory() throws Exception {
        Account buyer = register("shipment-buyer@example.com", RegistrationType.BUSINESS_OWNER);
        Account outsider = register("shipment-outsider@example.com", RegistrationType.BUSINESS_OWNER);
        Account fleet = register("shipment-fleet@example.com", RegistrationType.TRANSPORTER);
        UUID buyerBusinessId = createBusiness(buyer, "2026/850001/07");
        UUID outsiderBusinessId = createBusiness(outsider, "2026/850002/07");
        UUID fleetBusinessId = createBusiness(fleet, "2026/850003/07");
        var transporter = transportService.registerTransporter(fleetBusinessId, "Shipment Fleet", fleet.userId());
        Instant windowStart = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID demandId = demandCatalog.add(buyerBusinessId, windowStart, windowStart.plus(2, ChronoUnit.HOURS));
        OfferSetup offer = createOffer(fleet, fleetBusinessId, windowStart);
        var search = matchingService.search(
                buyerBusinessId,
                new CapacityMatchingService.SearchCapacity(
                        UUID.randomUUID(),
                        demandId,
                        new Capacity(amount("80.000"), amount("8.000")),
                        List.of(CargoTrait.DRY_GOODS)),
                buyer.userId());
        CapacityReservation reservation = matchingService.reserve(
                buyerBusinessId,
                search.id(),
                new CapacityMatchingService.ReserveCapacity(
                        UUID.randomUUID(), offer.offer().id()),
                buyer.userId());
        RouteSetup routes = createRoutes(buyer, buyerBusinessId);
        UUID requestId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        String createBody = createBody(
                requestId,
                demandId,
                search.id(),
                reservation.id(),
                routes.assessment().id(),
                routes.assessment().recommendedCandidateId(),
                correlationId);

        String created = create(buyer, buyerBusinessId, createBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AWAITING_COLLECTION"))
                .andExpect(jsonPath("$.loadOrders.length()").value(2))
                .andExpect(jsonPath("$.reservedCapacity.weightKg").value(80.0))
                .andExpect(jsonPath("$.currentAssignment.transportAssignmentId")
                        .value(offer.assignment().id().toString()))
                .andExpect(jsonPath("$.currentAssignment.routeCandidateId")
                        .value(routes.assessment().recommendedCandidateId().toString()))
                .andExpect(
                        jsonPath("$.currentAssignment.routeGeometry.length()").value(5))
                .andExpect(jsonPath("$.currentAssignment.driverReference").doesNotExist())
                .andExpect(jsonPath("$.assignmentHistory.length()").value(1))
                .andExpect(jsonPath("$.transitionHistory.length()").value(1))
                .andExpect(jsonPath("$.transitionHistory[0].fromStatus").doesNotExist())
                .andExpect(jsonPath("$.transitionHistory[0].toStatus").value("AWAITING_COLLECTION"))
                .andExpect(jsonPath("$.transitionHistory[0].source").value("API"))
                .andExpect(jsonPath("$.riskScore").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID shipmentId = UUID.fromString(JsonPath.read(created, "$.shipmentId"));
        create(buyer, buyerBusinessId, createBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shipmentId").value(shipmentId.toString()));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transport_capacity_reservation WHERE id = ?",
                        String.class,
                        reservation.id()))
                .isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM transport_capacity_match_search WHERE id = ?", String.class, search.id()))
                .isEqualTo("ASSIGNED");
        jdbcTemplate.update(
                "UPDATE transport_capacity_reservation"
                        + " SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 hours',"
                        + " expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour' WHERE id = ?",
                reservation.id());
        assertThat(matchingService.expireDueReservations()).isZero();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT remaining_weight_kg FROM transport_capacity_offer WHERE id = ?",
                        BigDecimal.class,
                        offer.offer().id()))
                .isEqualByComparingTo("20.000");
        create(buyer, buyerBusinessId, createBody.replace("Initial approved shipment", "Changed request"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPMENT_REQUEST_CONFLICT"));
        getShipment(outsider, buyerBusinessId, shipmentId).andExpect(status().isForbidden());
        getShipment(outsider, outsiderBusinessId, shipmentId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SHIPMENT_NOT_FOUND"));

        UUID collectedCommand = UUID.randomUUID();
        String collected = transitionBody(collectedCommand, ShipmentStatus.COLLECTED, "Collection confirmed");
        transition(buyer, buyerBusinessId, shipmentId, collected)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COLLECTED"))
                .andExpect(jsonPath("$.transitionHistory.length()").value(2));
        transition(buyer, buyerBusinessId, shipmentId, collected)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transitionHistory.length()").value(2));
        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(collectedCommand, ShipmentStatus.IN_TRANSIT, "Changed command"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SHIPMENT_TRANSITION_CONFLICT"));
        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(UUID.randomUUID(), ShipmentStatus.DELIVERED, "Invalid shortcut"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_SHIPMENT_TRANSITION"));

        DriverVehicleAssignment replacementTransport = createAssignment(fleet, fleetBusinessId, "REPLACEMENT");
        UUID replacementRoute = routes.calculation().candidates().getFirst().id();
        if (replacementRoute.equals(routes.assessment().recommendedCandidateId())) {
            replacementRoute = routes.calculation().candidates().get(1).id();
        }
        UUID assignmentCommand = UUID.randomUUID();
        String assignmentBody = assignmentBody(
                assignmentCommand,
                replacementTransport.id(),
                routes.assessment().id(),
                replacementRoute,
                "Vehicle and route changed");
        changeAssignment(buyer, buyerBusinessId, shipmentId, assignmentBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentHistory.length()").value(2))
                .andExpect(jsonPath("$.assignmentHistory[0].endedAt").exists())
                .andExpect(jsonPath("$.currentAssignment.transportAssignmentId")
                        .value(replacementTransport.id().toString()))
                .andExpect(jsonPath("$.currentAssignment.routeCandidateId").value(replacementRoute.toString()));
        changeAssignment(buyer, buyerBusinessId, shipmentId, assignmentBody)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentHistory.length()").value(2));

        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(UUID.randomUUID(), ShipmentStatus.IN_TRANSIT, "Departed"))
                .andExpect(status().isOk());
        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(UUID.randomUUID(), ShipmentStatus.DELAYED, "Traffic delay"))
                .andExpect(status().isOk());
        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(UUID.randomUUID(), ShipmentStatus.IN_TRANSIT, "Moving again"))
                .andExpect(status().isOk());
        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(UUID.randomUUID(), ShipmentStatus.DELIVERED, "Delivery confirmed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"))
                .andExpect(jsonPath("$.transitionHistory.length()").value(6));
        transition(
                        buyer,
                        buyerBusinessId,
                        shipmentId,
                        transitionBody(UUID.randomUUID(), ShipmentStatus.DISPUTED, "Quantity disputed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISPUTED"))
                .andExpect(jsonPath("$.transitionHistory.length()").value(7));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM shipment_assignment WHERE shipment_id = ? AND ended_at IS NULL",
                        Integer.class,
                        shipmentId))
                .isOne();
        assertThat(transporter.id()).isEqualTo(offer.offer().transporterId());
    }

    private RouteSetup createRoutes(Account buyer, UUID businessId) {
        var calculation = routingService.calculate(
                businessId,
                new RoutingService.CalculateRoutes(
                        UUID.randomUUID(),
                        null,
                        new GeoPoint("Johannesburg", -26.2041, 28.0473),
                        new GeoPoint("Pretoria", -25.7479, 28.2293),
                        List.of(new GeoPoint("Midrand", -25.9992, 28.1263)),
                        new VehicleLimits(amount("5000.000"), amount("4.200"), amount("2.500"), amount("12.000")),
                        List.of()),
                buyer.userId());
        RouteAssessment assessment = scoringService.score(
                businessId,
                calculation.id(),
                new RouteScoringService.ScoreRoutes(UUID.randomUUID(), "HIGH_VALUE_ELECTRONICS", null),
                buyer.userId());
        return new RouteSetup(calculation, assessment);
    }

    private OfferSetup createOffer(Account fleet, UUID fleetBusinessId, Instant start) {
        DriverVehicleAssignment assignment = createAssignment(fleet, fleetBusinessId, "PRIMARY");
        CapacityOffer offer = transportService.publishOffer(
                fleetBusinessId,
                new TransportService.PublishCapacityOffer(
                        UUID.randomUUID(),
                        assignment.vehicleId(),
                        assignment.id(),
                        List.of(
                                new RoutePoint(0, "Johannesburg", -26.2041, 28.0473),
                                new RoutePoint(1, "Pretoria", -25.7479, 28.2293)),
                        20_000,
                        start,
                        start.plus(2, ChronoUnit.HOURS),
                        start.plus(1, ChronoUnit.HOURS),
                        List.of(CargoRestriction.NO_HAZARDOUS_GOODS),
                        new Capacity(amount("100.000"), amount("10.000"))),
                fleet.userId());
        return new OfferSetup(offer, assignment);
    }

    private DriverVehicleAssignment createAssignment(Account fleet, UUID fleetBusinessId, String label) {
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        var vehicle = transportService.createVehicle(
                fleetBusinessId,
                new TransportService.CreateVehicle(
                        UUID.randomUUID(),
                        "GP " + suffix,
                        label + " shipment truck",
                        amount("500.000"),
                        amount("50.000")),
                fleet.userId());
        var driver = transportService.createDriver(
                fleetBusinessId,
                new TransportService.CreateDriver(UUID.randomUUID(), label + " Driver", "DRV-" + suffix),
                fleet.userId());
        return transportService.assignDriver(
                fleetBusinessId,
                new TransportService.AssignDriver(UUID.randomUUID(), vehicle.id(), driver.id()),
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

    private ResultActions create(Account account, UUID businessId, String body) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/shipments", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getShipment(Account account, UUID businessId, UUID shipmentId) throws Exception {
        return mockMvc.perform(get("/api/businesses/{businessId}/shipments/{shipmentId}", businessId, shipmentId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions transition(Account account, UUID businessId, UUID shipmentId, String body) throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/shipments/{shipmentId}/transitions", businessId, shipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions changeAssignment(Account account, UUID businessId, UUID shipmentId, String body)
            throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/shipments/{shipmentId}/assignments", businessId, shipmentId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private static String createBody(
            UUID requestId,
            UUID demandId,
            UUID searchId,
            UUID reservationId,
            UUID assessmentId,
            UUID candidateId,
            UUID correlationId) {
        return """
            {
              "requestId":"%s",
              "demandGroupSuggestionId":"%s",
              "capacitySearchId":"%s",
              "capacityReservationId":"%s",
              "routeAssessmentId":"%s",
              "routeCandidateId":"%s",
              "reason":"Initial approved shipment",
              "correlationId":"%s"
            }
            """.formatted(requestId, demandId, searchId, reservationId, assessmentId, candidateId, correlationId);
    }

    private static String transitionBody(UUID commandId, ShipmentStatus target, String reason) {
        return """
            {"commandId":"%s","targetStatus":"%s","reason":"%s","correlationId":"%s"}
            """.formatted(commandId, target, reason, UUID.randomUUID());
    }

    private static String assignmentBody(
            UUID commandId, UUID transportAssignmentId, UUID assessmentId, UUID candidateId, String reason) {
        return """
            {
              "commandId":"%s",
              "transportAssignmentId":"%s",
              "routeAssessmentId":"%s",
              "routeCandidateId":"%s",
              "reason":"%s",
              "correlationId":"%s"
            }
            """.formatted(commandId, transportAssignmentId, assessmentId, candidateId, reason, UUID.randomUUID());
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}

    private record OfferSetup(CapacityOffer offer, DriverVehicleAssignment assignment) {}

    private record RouteSetup(
            za.co.trademesh.modules.routing.domain.RouteCalculation calculation, RouteAssessment assessment) {}

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
