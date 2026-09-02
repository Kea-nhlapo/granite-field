package za.co.trademesh.modules.risk.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import za.co.trademesh.modules.telemetry.application.TelemetryService;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@Import(RiskControllerIntegrationTest.DemandTestConfiguration.class)
class RiskControllerIntegrationTest extends PostgresIntegrationTest {

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

    @BeforeEach
    @AfterEach
    void cleanState() {
        demandCatalog.clear();
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
    void consumesTelemetryPersistsOneActiveIndicatorAndProtectsTheReviewApi() throws Exception {
        ShipmentSetup setup = createShipment();
        transition(setup, ShipmentStatus.COLLECTED);
        transition(setup, ShipmentStatus.IN_TRANSIT);
        var deviceOne = telemetryService.provision(
                setup.businessId(),
                setup.shipmentId(),
                "Primary tracker",
                setup.buyer().userId());
        var deviceTwo = telemetryService.provision(
                setup.businessId(),
                setup.shipmentId(),
                "Replacement tracker",
                setup.buyer().userId());
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        telemetryService.ingest(
                deviceOne.rawCredential(),
                List.of(
                        reading(now.minus(20, ChronoUnit.MINUTES), "0", null, false),
                        reading(now.minus(15, ChronoUnit.MINUTES), "0", null, false),
                        reading(now.minus(10, ChronoUnit.MINUTES), "0", "310", false),
                        reading(now.minus(5, ChronoUnit.MINUTES), "0", null, false)));
        telemetryService.ingest(deviceTwo.rawCredential(), List.of(reading(now, "0", "265", true)));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM risk_indicator WHERE shipment_id = ?", Integer.class, setup.shipmentId()))
                .isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM risk_indicator WHERE shipment_id = ? AND rule_code = 'ROUTE_DEVIATION'",
                        Integer.class,
                        setup.shipmentId()))
                .isOne();

        mockMvc.perform(get("/api/internal/risk/shipments/{shipmentId}/indicators", setup.shipmentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(setup.buyer())))
                .andExpect(status().isForbidden());

        Account analyst = internalRiskAnalyst();
        mockMvc.perform(get("/api/internal/risk/shipments/{shipmentId}/indicators", setup.shipmentId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(analyst)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.indicators.length()").value(5))
                .andExpect(jsonPath("$.indicators[0].ruleVersion").value("operational-risk/v1"))
                .andExpect(jsonPath("$.indicators[0].evidence").isArray())
                .andExpect(jsonPath("$.indicators[0].riskScore").doesNotExist());

        UUID indicatorId = jdbcTemplate.queryForObject(
                "SELECT id FROM risk_indicator WHERE shipment_id = ? AND rule_code = 'UNEXPECTED_SEAL_OPENING'",
                UUID.class,
                setup.shipmentId());
        UUID commandId = UUID.randomUUID();
        String transition = """
            {"commandId":"%s","targetState":"ACKNOWLEDGED","note":"Driver contacted."}
            """.formatted(commandId);
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/internal/risk/indicators/{indicatorId}/transitions", indicatorId)
                            .header(HttpHeaders.AUTHORIZATION, bearer(analyst))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(transition))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.state").value("ACKNOWLEDGED"))
                    .andExpect(jsonPath("$.reviewHistory.length()").value(2));
        }
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM risk_indicator_transition WHERE indicator_id = ?",
                        Integer.class,
                        indicatorId))
                .isEqualTo(2);
    }

    private ShipmentSetup createShipment() {
        Account buyer = register("risk-buyer@example.com", RegistrationType.BUSINESS_OWNER);
        Account fleet = register("risk-fleet@example.com", RegistrationType.TRANSPORTER);
        UUID businessId = createBusiness(buyer, "2026/870001/07");
        UUID fleetBusinessId = createBusiness(fleet, "2026/870002/07");
        transportService.registerTransporter(fleetBusinessId, "Risk Fleet", fleet.userId());
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID demandId = demandCatalog.add(businessId, start, start.plus(2, ChronoUnit.HOURS));
        var vehicle = transportService.createVehicle(
                fleetBusinessId,
                new TransportService.CreateVehicle(
                        UUID.randomUUID(), "GP RISK 01", "Risk truck", amount("500"), amount("50")),
                fleet.userId());
        var driver = transportService.createDriver(
                fleetBusinessId,
                new TransportService.CreateDriver(UUID.randomUUID(), "Risk Driver", "DRV-RISK"),
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
                        "Risk test shipment",
                        UUID.randomUUID()),
                buyer.userId(),
                ShipmentActionSource.OPERATIONS);
        return new ShipmentSetup(buyer, businessId, shipment.id());
    }

    private void transition(ShipmentSetup setup, ShipmentStatus status) {
        shipmentService.transition(
                setup.businessId(),
                setup.shipmentId(),
                new ShipmentService.TransitionShipment(UUID.randomUUID(), status, "Risk test", UUID.randomUUID()),
                setup.buyer().userId(),
                ShipmentActionSource.OPERATIONS);
    }

    private Account internalRiskAnalyst() {
        String email = "risk-analyst@example.com";
        Account account = register(email, RegistrationType.BUSINESS_OWNER);
        jdbcTemplate.update(
                "INSERT INTO access_user_role (user_id, role) VALUES (?, 'INTERNAL_RISK_ANALYST')", account.userId());
        var tokens = authService.login(email, "correct-horse-battery");
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private Account register(String email, RegistrationType type) {
        var tokens = authService.register(email, "correct-horse-battery", type);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private static TelemetryService.ReadingInput reading(
            Instant recordedAt, String speed, String fuel, Boolean sealOpen) {
        return new TelemetryService.ReadingInput(
                UUID.randomUUID(),
                recordedAt,
                -24.0000,
                30.0000,
                amount(speed),
                amount(fuel),
                null,
                sealOpen,
                null,
                null,
                null);
    }

    private static BigDecimal amount(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}

    private record ShipmentSetup(Account buyer, UUID businessId, UUID shipmentId) {}

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
