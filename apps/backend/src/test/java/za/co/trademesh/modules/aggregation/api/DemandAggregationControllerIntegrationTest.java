package za.co.trademesh.modules.aggregation.api;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import za.co.trademesh.modules.access.application.AuthService;
import za.co.trademesh.modules.access.domain.RegistrationType;
import za.co.trademesh.modules.aggregation.events.AggregationEvent;
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.shared.events.PublishedEvent;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@RecordApplicationEvents
class DemandAggregationControllerIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthService authService;

    @Autowired
    private RegisteredBusinessOnboardingService onboardingService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @BeforeEach
    @AfterEach
    void cleanState() {
        jdbcTemplate.update("DELETE FROM demand_group_constraint_result");
        jdbcTemplate.update("DELETE FROM demand_group_order_evaluation");
        jdbcTemplate.update("DELETE FROM demand_group_suggestion");
        jdbcTemplate.update("DELETE FROM procurement_order_item");
        jdbcTemplate.update("DELETE FROM procurement_order");
        jdbcTemplate.update("DELETE FROM procurement_quote_item");
        jdbcTemplate.update("DELETE FROM procurement_quote");
        jdbcTemplate.update("DELETE FROM procurement_request_item");
        jdbcTemplate.update("DELETE FROM procurement_request");
        jdbcTemplate.update("DELETE FROM document_confirmed_field");
        jdbcTemplate.update("DELETE FROM document_confirmation");
        jdbcTemplate.update("DELETE FROM document_extracted_field");
        jdbcTemplate.update("DELETE FROM document_extraction");
        jdbcTemplate.update("DELETE FROM document_state_transition");
        jdbcTemplate.update("DELETE FROM document_record");
        jdbcTemplate.update("DELETE FROM outbox_message");
        jdbcTemplate.update("DELETE FROM stored_file");
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
    void suggestsOnlyEligibleOrdersAndExplainsEveryNearbyExclusion() throws Exception {
        Account owner = register("aggregation-owner@example.com");
        Account outsider = register("aggregation-outsider@example.com");
        UUID anchorBusiness = createBusiness(owner, "2026/710001/07");
        UUID compatibleBusiness = createBusiness(owner, "2026/710002/07");
        UUID supplierMismatchBusiness = createBusiness(owner, "2026/710003/07");
        UUID distanceBusiness = createBusiness(owner, "2026/710004/07");
        UUID windowBusiness = createBusiness(owner, "2026/710005/07");
        UUID cargoBusiness = createBusiness(owner, "2026/710006/07");
        UUID outsideSearchBusiness = createBusiness(owner, "2026/710007/07");
        UUID outsiderBusiness = createBusiness(outsider, "2026/710008/07");
        UUID supplier = createSupplier("aggregation-supplier@example.com");
        UUID otherSupplier = createSupplier("other-aggregation-supplier@example.com");
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant end = start.plus(4, ChronoUnit.HOURS);

        UUID anchor = insertOrder(
                anchorBusiness, owner.userId(), supplier, -25.997, 28.226, start, end, "DRINK-001", "MEAL-001");
        UUID compatible = insertOrder(
                compatibleBusiness,
                owner.userId(),
                supplier,
                -25.980,
                28.240,
                start.plus(30, ChronoUnit.MINUTES),
                end.plus(30, ChronoUnit.MINUTES),
                "DRINK-001");
        UUID wrongSupplier = insertOrder(
                supplierMismatchBusiness, owner.userId(), otherSupplier, -25.985, 28.230, start, end, "DRINK-001");
        UUID tooFar = insertOrder(distanceBusiness, owner.userId(), supplier, -25.810, 28.226, start, end, "DRINK-001");
        UUID wrongWindow = insertOrder(
                windowBusiness,
                owner.userId(),
                supplier,
                -25.990,
                28.235,
                end.plus(1, ChronoUnit.HOURS),
                end.plus(3, ChronoUnit.HOURS),
                "DRINK-001");
        UUID wrongCargo =
                insertOrder(cargoBusiness, owner.userId(), supplier, -25.992, 28.229, start, end, "MEDICINE-001");
        UUID outsideSearch =
                insertOrder(outsideSearchBusiness, owner.userId(), supplier, -25.600, 28.226, start, end, "DRINK-001");
        UUID alternateAnchor =
                insertOrder(anchorBusiness, owner.userId(), supplier, -25.590, 28.226, start, end, "OTHER-001");

        UUID requestId = UUID.randomUUID();
        String response = suggest(owner, anchorBusiness, requestId, anchor)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.includedOrderCount").value(2))
                .andExpect(jsonPath("$.thresholds.maximumDistanceMeters").value(15000.0))
                .andExpect(jsonPath("$.orders.length()").value(6))
                .andExpect(jsonPath("$.orders[1].buyerBusinessId").doesNotExist())
                .andExpect(jsonPath("$.orders[1].destinationLabel").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID suggestionId = UUID.fromString(JsonPath.read(response, "$.suggestionId"));

        assertEvaluation(response, compatible, true, null);
        assertEvaluation(response, wrongSupplier, false, "SUPPLIER_OR_PICKUP_MISMATCH");
        assertEvaluation(response, tooFar, false, "DISTANCE_EXCEEDS_LIMIT");
        assertEvaluation(response, wrongWindow, false, "DELIVERY_WINDOWS_DO_NOT_OVERLAP");
        assertEvaluation(response, wrongCargo, false, "CARGO_NOT_COMPATIBLE");
        assertThat(orderIds(response)).doesNotContain(outsideSearch, alternateAnchor);

        String retry = suggest(owner, anchorBusiness, requestId, anchor)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String duplicateGroup = suggest(owner, anchorBusiness, UUID.randomUUID(), anchor)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(JsonPath.<String>read(retry, "$.suggestionId")).isEqualTo(suggestionId.toString());
        assertThat(JsonPath.<String>read(duplicateGroup, "$.suggestionId")).isEqualTo(suggestionId.toString());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM demand_group_suggestion", Integer.class))
                .isOne();
        assertThat(suggestionCreatedEventCount()).isOne();
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM procurement_order WHERE id = ?", String.class, compatible))
                .isEqualTo("CONFIRMED");

        suggest(owner, anchorBusiness, requestId, alternateAnchor)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("AGGREGATION_REQUEST_CONFLICT"));
        getSuggestion(owner, anchorBusiness, suggestionId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orders[0].role").value("ANCHOR"));
        getSuggestion(outsider, outsiderBusiness, suggestionId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AGGREGATION_SUGGESTION_NOT_FOUND"));
        suggest(outsider, anchorBusiness, UUID.randomUUID(), anchor).andExpect(status().isForbidden());
        suggest(outsider, outsiderBusiness, UUID.randomUUID(), anchor)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CONFIRMED_ORDER_NOT_FOUND"));
    }

    @Test
    void returnsANoMatchAssessmentWithoutChangingTheAnchorOrder() throws Exception {
        Account owner = register("no-match-owner@example.com");
        UUID businessId = createBusiness(owner, "2026/720001/07");
        UUID supplier = createSupplier("no-match-supplier@example.com");
        Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        UUID anchor = insertOrder(
                businessId,
                owner.userId(),
                supplier,
                -33.925,
                18.424,
                start,
                start.plus(2, ChronoUnit.HOURS),
                "SOLO-001");

        suggest(owner, businessId, UUID.randomUUID(), anchor)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("NO_MATCH"))
                .andExpect(jsonPath("$.includedOrderCount").value(1))
                .andExpect(jsonPath("$.score").value(0.0))
                .andExpect(jsonPath("$.orders.length()").value(1));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM procurement_order WHERE id = ?", String.class, anchor))
                .isEqualTo("CONFIRMED");
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private UUID createSupplier(String email) {
        UUID supplierId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO supplier_profile (
                id, normalized_email, profile_status, claimed_user_id,
                business_id, created_at, converted_at
            ) VALUES (?, ?, 'TEMPORARY', NULL, NULL, CURRENT_TIMESTAMP, NULL)
            """, supplierId, email);
        return supplierId;
    }

    private UUID insertOrder(
            UUID businessId,
            UUID actorUserId,
            UUID supplierId,
            double latitude,
            double longitude,
            Instant windowStart,
            Instant windowEnd,
            String... productCodes) {
        UUID requestId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID storedFileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        OffsetDateTime start = OffsetDateTime.ofInstant(windowStart, ZoneOffset.UTC);
        OffsetDateTime end = OffsetDateTime.ofInstant(windowEnd, ZoneOffset.UTC);
        BigDecimal total = BigDecimal.TEN.multiply(BigDecimal.valueOf(productCodes.length));
        String sha256 = storedFileId.toString().replace("-", "").repeat(2);

        jdbcTemplate.update(
                """
            INSERT INTO procurement_request (
                id, buyer_business_id, client_request_id, status, destination_label,
                destination, delivery_window_start, delivery_window_end,
                created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, 'ORDERED', 'Test destination',
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, requestId, businessId, UUID.randomUUID(), longitude, latitude, start, end, actorUserId);
        jdbcTemplate.update("""
            INSERT INTO stored_file (
                id, business_id, category, original_filename, object_key, content_type,
                extension, size_bytes, sha256, scan_status, storage_status,
                uploaded_by_user_id, created_at, stored_at
            ) VALUES (?, ?, 'INVOICE', 'quote.pdf', ?, 'application/pdf',
                      'pdf', 10, ?, 'CLEAN', 'AVAILABLE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, storedFileId, businessId, "aggregation/" + storedFileId, sha256, actorUserId);
        jdbcTemplate.update("""
            INSERT INTO document_record (
                id, business_id, stored_file_id, client_request_id, document_type, state,
                processing_attempts, created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'QUOTE', 'CONFIRMED', 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, documentId, businessId, storedFileId, UUID.randomUUID(), actorUserId);
        jdbcTemplate.update(
                """
            INSERT INTO procurement_quote (
                id, request_id, buyer_business_id, supplier_profile_id, source_document_id,
                client_request_id, status, currency, subtotal, tax_amount, total,
                valid_until, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, 'ACCEPTED', 'ZAR', ?, 0, ?,
                      CURRENT_TIMESTAMP + INTERVAL '1 day', ?, CURRENT_TIMESTAMP)
            """,
                quoteId,
                requestId,
                businessId,
                supplierId,
                documentId,
                UUID.randomUUID(),
                total,
                total,
                actorUserId);
        jdbcTemplate.update(
                """
            INSERT INTO procurement_order (
                id, request_id, source_quote_id, buyer_business_id, supplier_profile_id,
                source_document_id, confirmation_request_id, status, currency, subtotal,
                tax_amount, total, destination_label, destination, delivery_window_start,
                delivery_window_end, confirmed_by_user_id, confirmed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', 'ZAR', ?, 0, ?, 'Test destination',
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, CURRENT_TIMESTAMP)
            """,
                orderId,
                requestId,
                quoteId,
                businessId,
                supplierId,
                documentId,
                UUID.randomUUID(),
                total,
                total,
                longitude,
                latitude,
                start,
                end,
                actorUserId);

        for (String productCode : productCodes) {
            UUID requestItemId = UUID.randomUUID();
            jdbcTemplate.update("""
                INSERT INTO procurement_request_item (
                    id, request_id, product_code, description, quantity, unit_of_measure
                ) VALUES (?, ?, ?, 'Test cargo', 1, 'CASE')
                """, requestItemId, requestId, productCode);
            jdbcTemplate.update("""
                INSERT INTO procurement_quote_item (
                    id, quote_id, request_item_id, description, quantity,
                    unit_of_measure, unit_price, line_total
                ) VALUES (?, ?, ?, 'Test cargo', 1, 'CASE', 10, 10)
                """, UUID.randomUUID(), quoteId, requestItemId);
            jdbcTemplate.update("""
                INSERT INTO procurement_order_item (
                    id, order_id, source_request_item_id, product_code, description,
                    quantity, unit_of_measure, unit_price, line_total
                ) VALUES (?, ?, ?, ?, 'Test cargo', 1, 'CASE', 10, 10)
                """, UUID.randomUUID(), orderId, requestItemId, productCode);
        }
        return orderId;
    }

    private ResultActions suggest(Account account, UUID businessId, UUID requestId, UUID anchorOrderId)
            throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/aggregation/suggestions", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"requestId\":\"" + requestId + "\",\"anchorOrderId\":\"" + anchorOrderId + "\"}"));
    }

    private ResultActions getSuggestion(Account account, UUID businessId, UUID suggestionId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/aggregation/suggestions/{suggestionId}", businessId, suggestionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private static void assertEvaluation(
            String response, UUID orderId, boolean included, String expectedExclusionReason) {
        Map<String, Object> evaluation = evaluation(response, orderId);
        assertThat(evaluation.get("included")).isEqualTo(included);
        @SuppressWarnings("unchecked")
        List<String> exclusions = (List<String>) evaluation.get("exclusionReasons");
        if (expectedExclusionReason == null) {
            assertThat(exclusions).isEmpty();
        } else {
            assertThat(exclusions).contains(expectedExclusionReason);
        }
    }

    private static Map<String, Object> evaluation(String response, UUID orderId) {
        List<Map<String, Object>> evaluations = JsonPath.read(response, "$.orders");
        return evaluations.stream()
                .filter(value -> orderId.toString().equals(value.get("orderId")))
                .findFirst()
                .orElseThrow();
    }

    private static List<UUID> orderIds(String response) {
        List<String> ids = JsonPath.read(response, "$.orders[*].orderId");
        return ids.stream().map(UUID::fromString).toList();
    }

    private long suggestionCreatedEventCount() {
        return applicationEvents.stream(PublishedEvent.class)
                .map(event -> ((PublishedEvent<?>) event).event())
                .filter(AggregationEvent.SuggestionCreated.class::isInstance)
                .count();
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
