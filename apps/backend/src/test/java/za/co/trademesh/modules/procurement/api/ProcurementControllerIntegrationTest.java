package za.co.trademesh.modules.procurement.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
import za.co.trademesh.modules.business.application.RegisteredBusinessOnboardingService;
import za.co.trademesh.modules.procurement.events.ProcurementEvent;
import za.co.trademesh.shared.events.PublishedEvent;
import za.co.trademesh.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@RecordApplicationEvents
class ProcurementControllerIntegrationTest extends PostgresIntegrationTest {

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
    void movesFromMultiItemRequestToQuoteToImmutableConfirmedOrder() throws Exception {
        Account buyer = register("buyer@example.com");
        UUID businessId = createBusiness(buyer, "2026/610001/07");
        UUID browserRequestId = UUID.randomUUID();
        UUID drinks = UUID.randomUUID();
        UUID meal = UUID.randomUUID();
        Instant windowStart = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        Instant windowEnd = windowStart.plus(4, ChronoUnit.HOURS);
        String requestBody = requestBody(browserRequestId, drinks, meal, windowStart, windowEnd);

        String createdRequest = createRequest(buyer, businessId, requestBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.destination.label").value("Tembisa, Gauteng"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID productRequestId = UUID.fromString(JsonPath.read(createdRequest, "$.id"));

        createRequest(buyer, businessId, requestBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(productRequestId.toString()));
        createRequest(buyer, businessId, requestBody.replace("Tembisa, Gauteng", "Pretoria, Gauteng"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCUREMENT_REQUEST_CONFLICT"));

        UUID supplierId = createTemporarySupplier("supplier@example.com");
        UUID sourceDocumentId = createConfirmedDocument(businessId, buyer.userId());
        UUID quoteRequestId = UUID.randomUUID();
        String quoteBody = quoteBody(
                quoteRequestId,
                supplierId,
                sourceDocumentId,
                drinks,
                meal,
                Instant.now().plus(1, ChronoUnit.DAYS));

        String createdQuote = createQuote(buyer, businessId, productRequestId, quoteBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.money.currency").value("ZAR"))
                .andExpect(jsonPath("$.money.subtotal").value(1050.0))
                .andExpect(jsonPath("$.money.taxAmount").value(23.0))
                .andExpect(jsonPath("$.money.total").value(1073.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID quoteId = UUID.fromString(JsonPath.read(createdQuote, "$.id"));

        createQuote(buyer, businessId, productRequestId, quoteBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(quoteId.toString()));

        UUID confirmationRequestId = UUID.randomUUID();
        String orderJson = confirmQuote(buyer, businessId, quoteId, confirmationRequestId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.requestId").value(productRequestId.toString()))
                .andExpect(jsonPath("$.sourceQuoteId").value(quoteId.toString()))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.money.total").value(1073.0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID orderId = UUID.fromString(JsonPath.read(orderJson, "$.id"));

        confirmQuote(buyer, businessId, quoteId, confirmationRequestId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
        assertThat(orderConfirmedEventCount()).isOne();

        getRequest(buyer, businessId, productRequestId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ORDERED"));
        getQuote(buyer, businessId, quoteId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACCEPTED"));

        jdbcTemplate.update(
                "UPDATE procurement_quote_item SET unit_price = 1, line_total = quantity WHERE quote_id = ?", quoteId);
        jdbcTemplate.update(
                "UPDATE procurement_quote SET subtotal = 30, tax_amount = 0, total = 30 WHERE id = ?", quoteId);
        getOrder(buyer, businessId, orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.money.total").value(1073.0))
                .andExpect(jsonPath("$.destination.label").value("Tembisa, Gauteng"))
                .andExpect(jsonPath("$.items[0].description").isNotEmpty());

        cancelRequest(buyer, businessId, productRequestId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PROCUREMENT_STATE_CONFLICT"));
    }

    @Test
    void keepsRequestsTenantScopedAndRejectsInvalidCommercialData() throws Exception {
        Account buyer = register("buyer@example.com");
        Account outsider = register("outsider@example.com");
        UUID businessId = createBusiness(buyer, "2026/620001/07");
        UUID outsiderBusiness = createBusiness(outsider, "2026/620002/07");
        UUID firstItem = UUID.randomUUID();
        UUID secondItem = UUID.randomUUID();
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);
        String created = createRequest(
                        buyer,
                        businessId,
                        requestBody(UUID.randomUUID(), firstItem, secondItem, start, start.plusSeconds(3600)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID productRequestId = UUID.fromString(JsonPath.read(created, "$.id"));

        getRequest(outsider, businessId, productRequestId).andExpect(status().isForbidden());
        getRequest(outsider, outsiderBusiness, productRequestId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PRODUCT_REQUEST_NOT_FOUND"));

        createRequest(
                        buyer,
                        businessId,
                        requestBody(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), start, start))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PRODUCT_REQUEST"));

        UUID supplierId = createTemporarySupplier("quote-supplier@example.com");
        UUID sourceDocumentId = createConfirmedDocument(businessId, buyer.userId());
        String invalidCurrency = quoteBody(
                        UUID.randomUUID(),
                        supplierId,
                        sourceDocumentId,
                        firstItem,
                        secondItem,
                        Instant.now().plus(1, ChronoUnit.DAYS))
                .replace("\"currency\":\"ZAR\"", "\"currency\":\"ZZZ\"");
        createQuote(buyer, businessId, productRequestId, invalidCurrency)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUOTE"));

        String incompleteQuote = """
            {
              "requestId":"%s",
              "supplierProfileId":"%s",
              "sourceDocumentId":"%s",
              "currency":"ZAR",
              "taxAmount":0,
              "validUntil":"%s",
              "items":[{"requestItemId":"%s","unitPrice":40}]
            }
            """.formatted(
                UUID.randomUUID(), supplierId, sourceDocumentId, Instant.now().plus(1, ChronoUnit.DAYS), firstItem);
        createQuote(buyer, businessId, productRequestId, incompleteQuote)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUOTE"));
    }

    private Account register(String email) {
        var tokens = authService.register(email, "correct-horse-battery", RegistrationType.BUSINESS_OWNER);
        return new Account(tokens.userId(), tokens.accessToken());
    }

    private UUID createBusiness(Account owner, String registrationNumber) {
        var onboarding = onboardingService.start(registrationNumber, owner.userId());
        return onboardingService.confirm(onboarding.id(), owner.userId()).id();
    }

    private UUID createTemporarySupplier(String email) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO supplier_profile (
                id, normalized_email, profile_status, claimed_user_id,
                business_id, created_at, converted_at
            ) VALUES (?, ?, 'TEMPORARY', NULL, NULL, CURRENT_TIMESTAMP, NULL)
            """, id, email);
        return id;
    }

    private UUID createConfirmedDocument(UUID businessId, UUID actorUserId) {
        UUID storedFileId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID confirmationId = UUID.randomUUID();
        jdbcTemplate.update("""
            INSERT INTO stored_file (
                id, business_id, category, original_filename, object_key, content_type,
                extension, size_bytes, sha256, scan_status, storage_status,
                uploaded_by_user_id, created_at, stored_at
            ) VALUES (?, ?, 'INVOICE', 'quote.pdf', ?, 'application/pdf',
                      'pdf', 10, ?, 'CLEAN', 'AVAILABLE', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, storedFileId, businessId, "test/" + storedFileId, "0".repeat(64), actorUserId);
        jdbcTemplate.update("""
            INSERT INTO document_record (
                id, business_id, stored_file_id, client_request_id, document_type, state,
                processing_attempts, created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, 'QUOTE', 'CONFIRMED', 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """, documentId, businessId, storedFileId, UUID.randomUUID(), actorUserId);
        jdbcTemplate.update("""
            INSERT INTO document_confirmation (
                id, document_id, request_id, revision, confirmed_by_user_id, created_at
            ) VALUES (?, ?, ?, 1, ?, CURRENT_TIMESTAMP)
            """, confirmationId, documentId, UUID.randomUUID(), actorUserId);
        jdbcTemplate.update("""
            INSERT INTO document_confirmed_field (confirmation_id, field_path, field_value)
            VALUES (?, 'supplier.name', 'Demo Supplier')
            """, confirmationId);
        return documentId;
    }

    private ResultActions createRequest(Account account, UUID businessId, String body) throws Exception {
        return mockMvc.perform(post("/api/businesses/{businessId}/procurement/requests", businessId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getRequest(Account account, UUID businessId, UUID requestId) throws Exception {
        return mockMvc.perform(
                get("/api/businesses/{businessId}/procurement/requests/{requestId}", businessId, requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions cancelRequest(Account account, UUID businessId, UUID requestId) throws Exception {
        return mockMvc.perform(post(
                        "/api/businesses/{businessId}/procurement/requests/{requestId}/cancellation",
                        businessId,
                        requestId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions createQuote(Account account, UUID businessId, UUID requestId, String body) throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/procurement/requests/{requestId}/quotes", businessId, requestId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body));
    }

    private ResultActions getQuote(Account account, UUID businessId, UUID quoteId) throws Exception {
        return mockMvc.perform(get("/api/businesses/{businessId}/procurement/quotes/{quoteId}", businessId, quoteId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private ResultActions confirmQuote(Account account, UUID businessId, UUID quoteId, UUID confirmationRequestId)
            throws Exception {
        return mockMvc.perform(
                post("/api/businesses/{businessId}/procurement/quotes/{quoteId}/confirmations", businessId, quoteId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(account))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + confirmationRequestId + "\"}"));
    }

    private ResultActions getOrder(Account account, UUID businessId, UUID orderId) throws Exception {
        return mockMvc.perform(get("/api/businesses/{businessId}/procurement/orders/{orderId}", businessId, orderId)
                .header(HttpHeaders.AUTHORIZATION, bearer(account)));
    }

    private long orderConfirmedEventCount() {
        return applicationEvents.stream(PublishedEvent.class)
                .map(event -> ((PublishedEvent<?>) event).event())
                .filter(ProcurementEvent.OrderConfirmed.class::isInstance)
                .count();
    }

    private static String requestBody(
            UUID requestId, UUID drinks, UUID meal, Instant deliveryWindowStart, Instant deliveryWindowEnd) {
        return """
            {
              "requestId":"%s",
              "destinationLabel":"Tembisa, Gauteng",
              "destinationLatitude":-25.997,
              "destinationLongitude":28.226,
              "deliveryWindowStart":"%s",
              "deliveryWindowEnd":"%s",
              "items":[
                {
                  "itemId":"%s",
                  "productCode":"DRINK-001",
                  "description":"20 cases soft drinks",
                  "quantity":20,
                  "unitOfMeasure":"CASE"
                },
                {
                  "itemId":"%s",
                  "productCode":"MEAL-001",
                  "description":"10 bags maize meal",
                  "quantity":10,
                  "unitOfMeasure":"EACH"
                }
              ]
            }
            """.formatted(requestId, deliveryWindowStart, deliveryWindowEnd, drinks, meal);
    }

    private static String quoteBody(
            UUID requestId, UUID supplierId, UUID sourceDocumentId, UUID drinks, UUID meal, Instant validUntil) {
        return """
            {
              "requestId":"%s",
              "supplierProfileId":"%s",
              "sourceDocumentId":"%s",
              "currency":"ZAR",
              "taxAmount":23,
              "validUntil":"%s",
              "items":[
                {"requestItemId":"%s","unitPrice":40},
                {"requestItemId":"%s","unitPrice":25}
              ]
            }
            """.formatted(requestId, supplierId, sourceDocumentId, validUntil, drinks, meal);
    }

    private static String bearer(Account account) {
        return "Bearer " + account.accessToken();
    }

    private record Account(UUID userId, String accessToken) {}
}
