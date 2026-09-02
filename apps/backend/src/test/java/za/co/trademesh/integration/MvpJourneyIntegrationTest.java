package za.co.trademesh.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.aggregation.application.DemandAggregationService;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.document.application.DocumentComparisonService;
import za.co.trademesh.modules.document.application.DocumentService;
import za.co.trademesh.modules.document.domain.ConfirmedDocumentField;
import za.co.trademesh.modules.document.domain.DocumentMismatchRule;
import za.co.trademesh.modules.document.domain.DocumentState;
import za.co.trademesh.modules.document.domain.DocumentType;
import za.co.trademesh.modules.handover.application.HandoverService;
import za.co.trademesh.modules.handover.domain.CaptureMode;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.modules.handover.domain.QuantityOutcome;
import za.co.trademesh.modules.insurance.application.InsuranceEvidencePackage;
import za.co.trademesh.modules.insurance.application.InsuranceService;
import za.co.trademesh.modules.insurance.domain.InsurancePurpose;
import za.co.trademesh.modules.procurement.application.ProcurementService;
import za.co.trademesh.modules.procurement.domain.ConfirmedOrder;
import za.co.trademesh.modules.procurement.domain.ProductRequest;
import za.co.trademesh.modules.procurement.domain.UnitOfMeasure;
import za.co.trademesh.modules.risk.application.RiskService;
import za.co.trademesh.modules.risk.domain.RiskRule;
import za.co.trademesh.modules.routing.application.RouteScoringService;
import za.co.trademesh.modules.routing.application.RoutingService;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.VehicleLimits;
import za.co.trademesh.modules.shipment.application.ShipmentService;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.shipment.domain.ShipmentLoadOrder;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.modules.supplier.application.SupplierInvitationService;
import za.co.trademesh.modules.telemetry.application.TelemetryService;
import za.co.trademesh.modules.transport.application.CapacityMatchingService;
import za.co.trademesh.modules.transport.application.TransportService;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityMatchStatus;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.CargoTrait;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.shared.events.outbox.OutboxWorker;
import za.co.trademesh.shared.security.AccountRole;
import za.co.trademesh.shared.storage.FileCategory;
import za.co.trademesh.shared.storage.FileStorageService;
import za.co.trademesh.shared.storage.support.InMemoryObjectStorage;
import za.co.trademesh.support.DeterministicTelemetrySimulator;
import za.co.trademesh.support.MutableTestClock;
import za.co.trademesh.support.PostgresIntegrationTest;

@Import(MvpJourneyIntegrationTest.JourneyConfiguration.class)
@TestPropertySource(
        properties = {
            "trademesh.outbox.enabled=false",
            "trademesh.documents.extraction.provider=mock",
            "spring.task.scheduling.enabled=false"
        })
class MvpJourneyIntegrationTest extends PostgresIntegrationTest {

    private static final Instant START = Instant.parse("2030-06-01T08:00:00Z");
    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private AuthService auth;

    @Autowired
    private RegisteredBusinessOnboardingService onboarding;

    @Autowired
    private SupplierInvitationService invitations;

    @Autowired
    private FileStorageService files;

    @Autowired
    private DocumentService documents;

    @Autowired
    private DocumentComparisonService comparisons;

    @Autowired
    private ProcurementService procurement;

    @Autowired
    private DemandAggregationService aggregation;

    @Autowired
    private TransportService transport;

    @Autowired
    private CapacityMatchingService matching;

    @Autowired
    private RoutingService routing;

    @Autowired
    private RouteScoringService routeScoring;

    @Autowired
    private ShipmentService shipments;

    @Autowired
    private TelemetryService telemetry;

    @Autowired
    private RiskService risk;

    @Autowired
    private HandoverService handovers;

    @Autowired
    private InsuranceService insurance;

    @Autowired
    private OutboxWorker outbox;

    @Autowired
    private MutableTestClock clock;

    @Autowired
    private InMemoryObjectStorage objectStorage;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetBefore() {
        resetScenario();
    }

    @AfterEach
    void resetAfter() {
        resetScenario();
    }

    @Test
    void completesTheSameEvidenceBackedJourneyEveryTime() {
        Account buyer = register("demo-buyer@trademesh.test", RegistrationType.BUSINESS_OWNER);
        Account supplier = register("demo-supplier@trademesh.test", RegistrationType.SUPPLIER);
        Account fleet = register("demo-fleet@trademesh.test", RegistrationType.TRANSPORTER);
        Account receiver = register("demo-receiver@trademesh.test", RegistrationType.BUSINESS_OWNER);
        Account insurer = insurer("demo-insurer@trademesh.test");

        UUID buyerBusinessId = business(buyer, "2030/100001/07");
        UUID nearbyBusinessId = business(buyer, "2030/100002/07");
        UUID fleetBusinessId = business(fleet, "2030/100003/07");

        Instant deliveryStart = START.plus(Duration.ofDays(1));
        Instant deliveryEnd = deliveryStart.plus(Duration.ofHours(4));
        ProductRequest anchorRequest = request(
                buyerBusinessId,
                buyer,
                "anchor-request",
                "Tembisa",
                -25.9960,
                28.2260,
                deliveryStart,
                deliveryEnd,
                "20");

        var invitation =
                invitations.invite(buyerBusinessId, anchorRequest.id(), "demo-supplier@trademesh.test", buyer.userId());
        assertThat(invitations
                        .viewGuestInvitation(invitation.rawToken(), "demo-view")
                        .invitation()
                        .id())
                .isEqualTo(invitation.invitation().id());

        UUID purchaseOrderDocument = confirmedDocument(
                buyerBusinessId, buyer, "purchase-order.pdf", "purchase-order-20", DocumentType.PURCHASE_ORDER, "20");
        UUID quoteDocument = confirmedDocument(
                buyerBusinessId, buyer, "supplier-quote.pdf", "supplier-quote-24", DocumentType.QUOTE, "24");

        var comparison = comparisons.compare(
                buyerBusinessId,
                new DocumentComparisonService.CompareDocuments(
                        id("document-comparison"), purchaseOrderDocument, quoteDocument),
                buyer.userId());
        assertThat(comparison.indicators())
                .singleElement()
                .extracting(indicator -> indicator.rule())
                .isEqualTo(DocumentMismatchRule.DOCUMENT_QUANTITY_MISMATCH);

        invitations.submitResponse(invitation.rawToken(), anchorRequest.id(), quoteDocument, "demo-response");
        invitations.convert(
                invitation.profile().id(), supplier.userId(), null, invitation.rawToken(), "demo-conversion");

        ConfirmedOrder anchorOrder = quoteAndConfirm(
                buyerBusinessId, buyer, anchorRequest, invitation.profile().id(), quoteDocument, "anchor-quote");

        ProductRequest nearbyRequest = request(
                nearbyBusinessId,
                buyer,
                "nearby-request",
                "Midrand",
                -25.9992,
                28.1263,
                deliveryStart.plus(Duration.ofMinutes(20)),
                deliveryEnd.plus(Duration.ofMinutes(20)),
                "12");
        UUID nearbyQuoteDocument = confirmedDocument(
                nearbyBusinessId, buyer, "nearby-quote.pdf", "nearby-quote-12", DocumentType.QUOTE, "12");
        ConfirmedOrder nearbyOrder = quoteAndConfirm(
                nearbyBusinessId, buyer, nearbyRequest, invitation.profile().id(), nearbyQuoteDocument, "nearby-quote");

        var demand = aggregation.suggest(
                buyerBusinessId,
                new DemandAggregationService.SuggestDemandGroup(id("aggregate-orders"), anchorOrder.id()),
                buyer.userId());
        assertThat(demand.includedOrderCount()).isEqualTo(2);
        assertThat(demand.orderEvaluations())
                .filteredOn(evaluation -> evaluation.included())
                .extracting(evaluation -> evaluation.orderId())
                .containsExactlyInAnyOrder(anchorOrder.id(), nearbyOrder.id());

        var transporter = transport.registerTransporter(fleetBusinessId, "Demo Shared Fleet", fleet.userId());
        var vehicle = transport.createVehicle(
                fleetBusinessId,
                new TransportService.CreateVehicle(
                        id("demo-vehicle"), "GP DEMO 01", "Five-ton curtain-side truck", amount("5000"), amount("35")),
                fleet.userId());
        var driver = transport.createDriver(
                fleetBusinessId,
                new TransportService.CreateDriver(id("demo-driver"), "Demo Driver", "DRV-DEMO-01"),
                fleet.userId());
        var driverAssignment = transport.assignDriver(
                fleetBusinessId,
                new TransportService.AssignDriver(id("demo-driver-assignment"), vehicle.id(), driver.id()),
                fleet.userId());
        var offer = transport.publishOffer(
                fleetBusinessId,
                new TransportService.PublishCapacityOffer(
                        id("demo-capacity-offer"),
                        vehicle.id(),
                        driverAssignment.id(),
                        List.of(
                                new RoutePoint(0, "Johannesburg", -26.2041, 28.0473),
                                new RoutePoint(1, "Midrand", -25.9992, 28.1263),
                                new RoutePoint(2, "Pretoria", -25.7479, 28.2293)),
                        25_000,
                        deliveryStart,
                        deliveryEnd,
                        deliveryStart.plus(Duration.ofHours(1)),
                        List.of(CargoRestriction.NO_HAZARDOUS_GOODS),
                        new Capacity(amount("1300"), amount("20"))),
                fleet.userId());

        var capacitySearch = matching.search(
                buyerBusinessId,
                new CapacityMatchingService.SearchCapacity(
                        id("capacity-search"),
                        demand.id(),
                        new Capacity(amount("400"), amount("8")),
                        List.of(CargoTrait.DRY_GOODS)),
                buyer.userId());
        assertThat(capacitySearch.status()).isEqualTo(CapacityMatchStatus.MATCHED);
        assertThat(capacitySearch.candidates())
                .filteredOn(candidate -> candidate.compatible())
                .extracting(candidate -> candidate.offerId())
                .contains(offer.id());
        var reservation = matching.reserve(
                buyerBusinessId,
                capacitySearch.id(),
                new CapacityMatchingService.ReserveCapacity(id("capacity-reservation"), offer.id()),
                buyer.userId());

        var routeCalculation = routing.calculate(
                buyerBusinessId,
                new RoutingService.CalculateRoutes(
                        id("route-calculation"),
                        null,
                        new GeoPoint("Johannesburg", -26.2041, 28.0473),
                        new GeoPoint("Pretoria", -25.7479, 28.2293),
                        List.of(new GeoPoint("Midrand", -25.9992, 28.1263)),
                        new VehicleLimits(amount("5000"), amount("4.2"), amount("2.5"), amount("12")),
                        List.of()),
                buyer.userId());
        var routeAssessment = routeScoring.score(
                buyerBusinessId,
                routeCalculation.id(),
                new RouteScoringService.ScoreRoutes(id("route-score"), "HIGH_VALUE_ELECTRONICS", null),
                buyer.userId());
        assertThat(routeAssessment.candidates())
                .filteredOn(candidate -> candidate.candidateId().equals(routeAssessment.recommendedCandidateId()))
                .singleElement()
                .satisfies(candidate -> assertThat(candidate.reasons()).isNotEmpty());

        Shipment shipment = shipments.create(
                buyerBusinessId,
                new ShipmentService.CreateShipment(
                        id("shipment"),
                        demand.id(),
                        capacitySearch.id(),
                        reservation.id(),
                        routeAssessment.id(),
                        routeAssessment.recommendedCandidateId(),
                        "Approved shared delivery",
                        id("shipment-correlation")),
                buyer.userId(),
                ShipmentActionSource.OPERATIONS);
        assertThat(shipment.status()).isEqualTo(ShipmentStatus.AWAITING_COLLECTION);
        assertThat(shipment.loadOrders()).hasSize(2);
        assertThat(shipment.transporterId()).isEqualTo(transporter.id());

        completeHandover(buyerBusinessId, shipment.id(), null, HandoverType.COLLECTION, buyer, supplier, "collection");
        assertThat(shipments.get(buyerBusinessId, shipment.id()).status()).isEqualTo(ShipmentStatus.COLLECTED);
        shipments.transition(
                buyerBusinessId,
                shipment.id(),
                new ShipmentService.TransitionShipment(
                        id("depart"), ShipmentStatus.IN_TRANSIT, "Truck departed", id("depart-correlation")),
                buyer.userId(),
                ShipmentActionSource.OPERATIONS);

        var issuedDevice = telemetry.provision(buyerBusinessId, shipment.id(), "Demo truck tracker", buyer.userId());
        var simulation = new DeterministicTelemetrySimulator(telemetry, clock)
                .run(issuedDevice.rawCredential(), START.plus(Duration.ofMinutes(10)));
        assertThat(simulation.receipts()).hasSize(4);
        assertThat(risk.listForShipment(shipment.id()))
                .extracting(indicator -> indicator.rule())
                .contains(RiskRule.ROUTE_DEVIATION, RiskRule.STATIONARY_FUEL_DROP);

        clock.set(simulation.completedAt().plus(Duration.ofMinutes(1)));
        for (ShipmentLoadOrder order :
                shipments.get(buyerBusinessId, shipment.id()).loadOrders()) {
            completeHandover(
                    buyerBusinessId,
                    shipment.id(),
                    order.orderId(),
                    HandoverType.DELIVERY,
                    buyer,
                    receiver,
                    "delivery-" + order.sequence());
        }
        assertThat(shipments.get(buyerBusinessId, shipment.id()).status()).isEqualTo(ShipmentStatus.DELIVERED);

        var insuranceCase = insurance.createCase(
                new InsuranceService.CreateCase(
                        id("insurance-case"), shipment.id(), InsurancePurpose.CLAIM_REVIEW, insurer.userId()),
                insurer.userId());
        InsuranceEvidencePackage evidence = insurance.viewEvidence(insuranceCase.id(), insurer.userId());

        assertThat(evidence.missingEvidence()).isEmpty();
        assertThat(evidence.orders()).hasSize(2);
        assertThat(evidence.sourceDocuments()).hasSize(2);
        assertThat(evidence.actualRoute().points()).hasSize(4);
        assertThat(evidence.handovers()).hasSize(3);
        assertThat(evidence.riskIndicators())
                .extracting(indicator -> indicator.rule())
                .contains(RiskRule.ROUTE_DEVIATION.name(), RiskRule.STATIONARY_FUEL_DROP.name());

        var timeline = evidence.evidenceTimeline().entries();
        assertThat(timeline).extracting(entry -> entry.sequence()).isSorted();
        assertThat(timeline)
                .allSatisfy(entry -> assertThat(entry.integrity().name()).isEqualTo("VERIFIED"));
        assertThat(timeline)
                .extracting(entry -> entry.type())
                .containsSubsequence(
                        "SHIPMENT_CREATED",
                        "HANDOVER_CHALLENGE_ISSUED",
                        "SHIPMENT_STATUS_CHANGED",
                        "HANDOVER_FINALIZED",
                        "SHIPMENT_STATUS_CHANGED",
                        "RISK_INDICATOR_OPENED",
                        "RISK_INDICATOR_OPENED",
                        "HANDOVER_CHALLENGE_ISSUED",
                        "HANDOVER_FINALIZED",
                        "HANDOVER_CHALLENGE_ISSUED",
                        "SHIPMENT_STATUS_CHANGED",
                        "HANDOVER_FINALIZED");
        assertThat(timeline.stream()
                        .filter(entry -> entry.type().equals("SHIPMENT_STATUS_CHANGED"))
                        .map(entry -> entry.metadata().get("toStatus")))
                .containsExactly("COLLECTED", "IN_TRANSIT", "DELIVERED");
    }

    private ProductRequest request(
            UUID businessId,
            Account owner,
            String key,
            String destination,
            double latitude,
            double longitude,
            Instant windowStart,
            Instant windowEnd,
            String quantity) {
        return procurement.createRequest(
                businessId,
                new ProcurementService.CreateRequest(
                        id(key),
                        destination,
                        latitude,
                        longitude,
                        windowStart,
                        windowEnd,
                        List.of(new ProcurementService.RequestItem(
                                id(key + "-item"),
                                "DRY-COOKING-OIL",
                                "Cases of cooking oil",
                                amount(quantity),
                                UnitOfMeasure.CASE))),
                owner.userId());
    }

    private UUID confirmedDocument(
            UUID businessId, Account owner, String filename, String sourceContent, DocumentType type, String quantity) {
        var stored = files.upload(
                businessId,
                owner.userId(),
                FileCategory.INVOICE,
                filename,
                "application/pdf",
                ("%PDF-1.7\n" + sourceContent).getBytes(StandardCharsets.US_ASCII));
        var registered =
                documents.register(businessId, stored.id(), type, id(filename + "-registration"), owner.userId());
        drainOutbox();
        assertThat(documents
                        .get(businessId, registered.document().id())
                        .document()
                        .state())
                .isEqualTo(DocumentState.PARSED);
        return documents
                .confirm(
                        businessId,
                        registered.document().id(),
                        id(filename + "-confirmation"),
                        List.of(new ConfirmedDocumentField("items[0].quantity", quantity)),
                        owner.userId())
                .document()
                .id();
    }

    private ConfirmedOrder quoteAndConfirm(
            UUID businessId,
            Account owner,
            ProductRequest request,
            UUID supplierProfileId,
            UUID sourceDocumentId,
            String key) {
        var quote = procurement.createQuote(
                businessId,
                request.id(),
                new ProcurementService.CreateQuote(
                        id(key),
                        supplierProfileId,
                        sourceDocumentId,
                        "ZAR",
                        BigDecimal.ZERO,
                        START.plus(Duration.ofDays(2)),
                        List.of(new ProcurementService.QuoteItem(
                                request.items().getFirst().id(), amount("125.00")))),
                owner.userId());
        return procurement.confirmQuote(businessId, quote.id(), id(key + "-confirmation"), owner.userId());
    }

    private void completeHandover(
            UUID businessId,
            UUID shipmentId,
            UUID deliveryOrderId,
            HandoverType type,
            Account initiator,
            Account counterparty,
            String key) {
        var issued = handovers.issue(
                businessId,
                shipmentId,
                new HandoverService.IssueChallenge(type, deliveryOrderId, counterparty.userId()),
                initiator.userId());
        var location = issued.challenge().expectedLocation();
        handovers.confirm(
                confirmation(id(key + "-initiator"), issued.qrPayload(), location.latitude(), location.longitude()),
                initiator.userId());
        var completed = handovers.confirm(
                confirmation(id(key + "-counterparty"), issued.qrPayload(), location.latitude(), location.longitude()),
                counterparty.userId());
        assertThat(completed.state()).isEqualTo(HandoverState.COMPLETED);
    }

    private HandoverService.ConfirmHandover confirmation(
            UUID commandId, String token, double latitude, double longitude) {
        return new HandoverService.ConfirmHandover(
                commandId,
                token,
                CaptureMode.ONLINE,
                clock.instant(),
                latitude,
                longitude,
                QuantityOutcome.MATCHED,
                "Quantity checked by both parties");
    }

    private Account register(String email, RegistrationType type) {
        var tokens = auth.register(email, PASSWORD, type);
        return new Account(tokens.userId());
    }

    private Account insurer(String email) {
        Account account = register(email, RegistrationType.BUSINESS_OWNER);
        jdbc.update(
                "INSERT INTO access_user_role (user_id, role) VALUES (?, ?)",
                account.userId(),
                AccountRole.INSURER.name());
        return account;
    }

    private UUID business(Account owner, String registrationNumber) {
        var started = onboarding.start(registrationNumber, owner.userId());
        return onboarding.confirm(started.id(), owner.userId()).id();
    }

    private void drainOutbox() {
        for (int attempt = 0; attempt < 10 && outbox.pollOnce() > 0; attempt++) {
            // A following poll handles work enqueued by the previous handler.
        }
    }

    private void resetScenario() {
        objectStorage.clear();
        clock.set(START);
        jdbc.execute("""
            DO $$
            DECLARE tables_to_clear text;
            BEGIN
              SELECT string_agg(format('%I.%I', schemaname, tablename), ', ')
                INTO tables_to_clear
                FROM pg_tables
               WHERE schemaname = 'public'
                 AND tablename NOT IN ('flyway_schema_history', 'spatial_ref_sys');
              IF tables_to_clear IS NOT NULL THEN
                EXECUTE 'TRUNCATE TABLE ' || tables_to_clear || ' RESTART IDENTITY CASCADE';
              END IF;
            END $$;
            """);
        jdbc.execute("ALTER SEQUENCE evidence_ledger_sequence RESTART WITH 1");
    }

    private static UUID id(String name) {
        return UUID.nameUUIDFromBytes(("trademesh-demo:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value);
    }

    private record Account(UUID userId) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class JourneyConfiguration {

        @Bean
        @Primary
        MutableTestClock mutableTestClock() {
            return new MutableTestClock(START);
        }

        @Bean
        @Primary
        InMemoryObjectStorage journeyObjectStorage() {
            return new InMemoryObjectStorage();
        }
    }
}
