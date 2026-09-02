package za.co.trademesh.modules.procurement.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.document.application.ConfirmedDocumentReader;
import za.co.trademesh.modules.procurement.domain.ConfirmedOrder;
import za.co.trademesh.modules.procurement.domain.OrderLineItem;
import za.co.trademesh.modules.procurement.domain.OrderStatus;
import za.co.trademesh.modules.procurement.domain.ProcurementRepository;
import za.co.trademesh.modules.procurement.domain.ProcurementRequestStatus;
import za.co.trademesh.modules.procurement.domain.ProductRequest;
import za.co.trademesh.modules.procurement.domain.ProductRequestItem;
import za.co.trademesh.modules.procurement.domain.QuoteLineItem;
import za.co.trademesh.modules.procurement.domain.QuoteStatus;
import za.co.trademesh.modules.procurement.domain.SupplierQuote;
import za.co.trademesh.modules.procurement.domain.UnitOfMeasure;
import za.co.trademesh.modules.procurement.events.ProcurementEvent;
import za.co.trademesh.modules.supplier.application.SupplierDirectory;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class ProcurementService {

    private static final int MAX_ITEMS = 100;
    private static final int MAX_DESCRIPTION = 500;
    private static final int MAX_DESTINATION = 500;
    private static final int MAX_PRODUCT_CODE = 100;

    private final ProcurementRepository procurement;
    private final SupplierDirectory suppliers;
    private final ConfirmedDocumentReader documents;
    private final DomainEvents events;
    private final Clock clock;

    public ProcurementService(
            ProcurementRepository procurement,
            SupplierDirectory suppliers,
            ConfirmedDocumentReader documents,
            DomainEvents events,
            Clock clock) {
        this.procurement = procurement;
        this.suppliers = suppliers;
        this.documents = documents;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public ProductRequest createRequest(UUID buyerBusinessId, CreateRequest command, UUID actorUserId) {
        CreateRequest normalized = normalize(command);
        var existing = procurement.findRequestByClientRequestId(buyerBusinessId, normalized.requestId());
        if (existing.isPresent()) {
            return sameRequest(existing.get(), normalized);
        }

        Instant now = clock.instant();
        List<ProductRequestItem> items = normalized.items().stream()
                .map(item -> new ProductRequestItem(
                        item.itemId(), item.productCode(), item.description(), item.quantity(), item.unitOfMeasure()))
                .toList();
        ProductRequest request = new ProductRequest(
                UUID.randomUUID(),
                buyerBusinessId,
                ProcurementRequestStatus.OPEN,
                normalized.destinationLabel(),
                normalized.destinationLatitude(),
                normalized.destinationLongitude(),
                normalized.deliveryWindowStart(),
                normalized.deliveryWindowEnd(),
                items,
                actorUserId,
                now,
                now);
        if (!procurement.saveRequest(request, normalized.requestId())) {
            var concurrent = procurement.findRequestByClientRequestId(buyerBusinessId, normalized.requestId());
            if (concurrent.isPresent()) {
                return sameRequest(concurrent.get(), normalized);
            }
            throw ProcurementException.idempotencyConflict();
        }
        return request;
    }

    @Transactional(readOnly = true)
    public ProductRequest getRequest(UUID buyerBusinessId, UUID requestId) {
        return procurement.findRequest(buyerBusinessId, requestId).orElseThrow(ProcurementException::requestNotFound);
    }

    @Transactional
    public ProductRequest cancelRequest(UUID buyerBusinessId, UUID requestId) {
        ProductRequest request = procurement
                .findRequestForUpdate(buyerBusinessId, requestId)
                .orElseThrow(ProcurementException::requestNotFound);
        if (request.status() == ProcurementRequestStatus.CANCELLED) {
            return request;
        }
        if (request.status() == ProcurementRequestStatus.ORDERED
                || !procurement.cancelRequest(requestId, clock.instant())) {
            throw ProcurementException.stateConflict();
        }
        return getRequest(buyerBusinessId, requestId);
    }

    @Transactional
    public SupplierQuote createQuote(UUID buyerBusinessId, UUID requestId, CreateQuote command, UUID actorUserId) {
        CreateQuote normalized = normalize(command);
        var existing = procurement.findQuoteByClientRequestId(buyerBusinessId, requestId, normalized.requestId());
        if (existing.isPresent()) {
            return sameQuote(existing.get(), normalized);
        }

        ProductRequest request = procurement
                .findRequestForUpdate(buyerBusinessId, requestId)
                .orElseThrow(ProcurementException::requestNotFound);
        if (request.status() != ProcurementRequestStatus.OPEN && request.status() != ProcurementRequestStatus.QUOTED) {
            throw ProcurementException.stateConflict();
        }
        suppliers.find(normalized.supplierProfileId()).orElseThrow(ProcurementException::supplierNotFound);
        documents
                .find(buyerBusinessId, normalized.sourceDocumentId())
                .orElseThrow(ProcurementException::documentNotConfirmed);
        if (procurement.findQuoteBySourceDocument(normalized.sourceDocumentId()).isPresent()) {
            throw ProcurementException.sourceDocumentUsed();
        }

        List<QuoteLineItem> items = quoteItems(request, normalized.items());
        BigDecimal subtotal = items.stream()
                .map(QuoteLineItem::lineTotal)
                .reduce(zeroMoney(), BigDecimal::add)
                .setScale(4, RoundingMode.UNNECESSARY);
        BigDecimal total = subtotal.add(normalized.taxAmount()).setScale(4, RoundingMode.UNNECESSARY);
        Instant now = clock.instant();
        if (!normalized.validUntil().isAfter(now)) {
            throw ProcurementException.invalidQuote();
        }
        SupplierQuote quote = new SupplierQuote(
                UUID.randomUUID(),
                requestId,
                buyerBusinessId,
                normalized.supplierProfileId(),
                normalized.sourceDocumentId(),
                QuoteStatus.ACTIVE,
                normalized.currency(),
                subtotal,
                normalized.taxAmount(),
                total,
                normalized.validUntil(),
                items,
                actorUserId,
                now);
        if (!procurement.saveQuote(quote, normalized.requestId())) {
            var concurrent = procurement.findQuoteByClientRequestId(buyerBusinessId, requestId, normalized.requestId());
            if (concurrent.isPresent()) {
                return sameQuote(concurrent.get(), normalized);
            }
            throw ProcurementException.sourceDocumentUsed();
        }
        if (!procurement.markRequestQuoted(requestId, now)) {
            throw ProcurementException.stateConflict();
        }
        return quote;
    }

    @Transactional(readOnly = true)
    public SupplierQuote getQuote(UUID buyerBusinessId, UUID quoteId) {
        return procurement.findQuote(buyerBusinessId, quoteId).orElseThrow(ProcurementException::quoteNotFound);
    }

    @Transactional
    public ConfirmedOrder confirmQuote(
            UUID buyerBusinessId, UUID quoteId, UUID confirmationRequestId, UUID actorUserId) {
        var existing = procurement.findOrderByConfirmationRequestId(buyerBusinessId, confirmationRequestId);
        if (existing.isPresent()) {
            if (!existing.get().sourceQuoteId().equals(quoteId)) {
                throw ProcurementException.idempotencyConflict();
            }
            return existing.get();
        }

        SupplierQuote quote = getQuote(buyerBusinessId, quoteId);
        ProductRequest request = procurement
                .findRequestForUpdate(buyerBusinessId, quote.requestId())
                .orElseThrow(ProcurementException::requestNotFound);
        var existingAfterLock = procurement.findOrderByConfirmationRequestId(buyerBusinessId, confirmationRequestId);
        if (existingAfterLock.isPresent()) {
            if (!existingAfterLock.get().sourceQuoteId().equals(quoteId)) {
                throw ProcurementException.idempotencyConflict();
            }
            return existingAfterLock.get();
        }
        var concurrentOrder = procurement.findOrderByQuoteId(quoteId);
        if (concurrentOrder.isPresent()) {
            throw ProcurementException.stateConflict();
        }
        Instant now = clock.instant();
        if (request.status() != ProcurementRequestStatus.QUOTED
                || quote.status() != QuoteStatus.ACTIVE
                || !quote.validUntil().isAfter(now)) {
            throw ProcurementException.stateConflict();
        }

        Map<UUID, ProductRequestItem> requestedItems = new HashMap<>();
        request.items().forEach(item -> requestedItems.put(item.id(), item));
        List<OrderLineItem> snapshotItems = quote.items().stream()
                .map(item -> {
                    ProductRequestItem requested = requestedItems.get(item.requestItemId());
                    if (requested == null) {
                        throw ProcurementException.stateConflict();
                    }
                    return new OrderLineItem(
                            UUID.randomUUID(),
                            requested.id(),
                            requested.productCode(),
                            item.description(),
                            item.quantity(),
                            item.unitOfMeasure(),
                            item.unitPrice(),
                            item.lineTotal());
                })
                .toList();
        ConfirmedOrder order = new ConfirmedOrder(
                UUID.randomUUID(),
                request.id(),
                quote.id(),
                buyerBusinessId,
                quote.supplierProfileId(),
                quote.sourceDocumentId(),
                OrderStatus.CONFIRMED,
                quote.currency(),
                quote.subtotal(),
                quote.taxAmount(),
                quote.total(),
                request.destinationLabel(),
                request.destinationLatitude(),
                request.destinationLongitude(),
                request.deliveryWindowStart(),
                request.deliveryWindowEnd(),
                snapshotItems,
                actorUserId,
                now);
        if (!procurement.saveOrder(order, confirmationRequestId)) {
            var concurrent = procurement.findOrderByConfirmationRequestId(buyerBusinessId, confirmationRequestId);
            if (concurrent.isPresent() && concurrent.get().sourceQuoteId().equals(quoteId)) {
                return concurrent.get();
            }
            throw ProcurementException.stateConflict();
        }
        if (!procurement.acceptQuote(quoteId) || !procurement.markRequestOrdered(request.id(), now)) {
            throw ProcurementException.stateConflict();
        }
        events.publish(
                new ProcurementEvent.OrderConfirmed(
                        order.id(),
                        request.id(),
                        buyerBusinessId,
                        order.supplierProfileId(),
                        order.currency(),
                        order.total()),
                actorUserId.toString());
        return order;
    }

    @Transactional(readOnly = true)
    public ConfirmedOrder getOrder(UUID buyerBusinessId, UUID orderId) {
        return procurement.findOrder(buyerBusinessId, orderId).orElseThrow(ProcurementException::orderNotFound);
    }

    private static ProductRequest sameRequest(ProductRequest existing, CreateRequest command) {
        boolean same = existing.destinationLabel().equals(command.destinationLabel())
                && Double.compare(existing.destinationLatitude(), command.destinationLatitude()) == 0
                && Double.compare(existing.destinationLongitude(), command.destinationLongitude()) == 0
                && existing.deliveryWindowStart().equals(command.deliveryWindowStart())
                && existing.deliveryWindowEnd().equals(command.deliveryWindowEnd())
                && requestItemsMatch(existing.items(), command.items());
        if (!same) {
            throw ProcurementException.idempotencyConflict();
        }
        return existing;
    }

    private static SupplierQuote sameQuote(SupplierQuote existing, CreateQuote command) {
        boolean same = existing.supplierProfileId().equals(command.supplierProfileId())
                && existing.sourceDocumentId().equals(command.sourceDocumentId())
                && existing.currency().equals(command.currency())
                && existing.taxAmount().compareTo(command.taxAmount()) == 0
                && existing.validUntil().equals(command.validUntil())
                && quoteCommandsMatch(existing.items(), command.items());
        if (!same) {
            throw ProcurementException.idempotencyConflict();
        }
        return existing;
    }

    private static boolean requestItemsMatch(List<ProductRequestItem> existing, List<RequestItem> requested) {
        if (existing.size() != requested.size()) {
            return false;
        }
        Map<UUID, ProductRequestItem> byId = new HashMap<>();
        existing.forEach(item -> byId.put(item.id(), item));
        return requested.stream().allMatch(item -> {
            ProductRequestItem value = byId.get(item.itemId());
            return value != null
                    && java.util.Objects.equals(value.productCode(), item.productCode())
                    && value.description().equals(item.description())
                    && value.quantity().compareTo(item.quantity()) == 0
                    && value.unitOfMeasure() == item.unitOfMeasure();
        });
    }

    private static boolean quoteCommandsMatch(List<QuoteLineItem> existing, List<QuoteItem> requested) {
        if (existing.size() != requested.size()) {
            return false;
        }
        Map<UUID, QuoteLineItem> byRequestItem = new HashMap<>();
        existing.forEach(item -> byRequestItem.put(item.requestItemId(), item));
        return requested.stream().allMatch(item -> {
            QuoteLineItem value = byRequestItem.get(item.requestItemId());
            return value != null && value.unitPrice().compareTo(item.unitPrice()) == 0;
        });
    }

    private static List<QuoteLineItem> quoteItems(ProductRequest request, List<QuoteItem> commands) {
        if (commands.size() != request.items().size()) {
            throw ProcurementException.invalidQuote();
        }
        Map<UUID, ProductRequestItem> requested = new HashMap<>();
        request.items().forEach(item -> requested.put(item.id(), item));
        HashSet<UUID> seen = new HashSet<>();
        List<QuoteLineItem> result = new ArrayList<>();
        for (QuoteItem command : commands) {
            ProductRequestItem item = requested.get(command.requestItemId());
            if (item == null || !seen.add(command.requestItemId())) {
                throw ProcurementException.invalidQuote();
            }
            BigDecimal lineTotal = item.quantity().multiply(command.unitPrice()).setScale(4, RoundingMode.HALF_UP);
            result.add(new QuoteLineItem(
                    UUID.randomUUID(),
                    item.id(),
                    item.description(),
                    item.quantity(),
                    item.unitOfMeasure(),
                    command.unitPrice(),
                    lineTotal));
        }
        result.sort(Comparator.comparing(QuoteLineItem::requestItemId));
        return List.copyOf(result);
    }

    private static CreateRequest normalize(CreateRequest command) {
        if (command == null
                || command.requestId() == null
                || command.deliveryWindowStart() == null
                || command.deliveryWindowEnd() == null
                || command.destinationLatitude() < -90
                || command.destinationLatitude() > 90
                || command.destinationLongitude() < -180
                || command.destinationLongitude() > 180
                || command.items() == null
                || command.items().isEmpty()
                || command.items().size() > MAX_ITEMS) {
            throw ProcurementException.invalidRequest();
        }
        Instant windowStart = databaseTime(command.deliveryWindowStart());
        Instant windowEnd = databaseTime(command.deliveryWindowEnd());
        if (!windowEnd.isAfter(windowStart)) {
            throw ProcurementException.invalidRequest();
        }
        String destination = requiredText(command.destinationLabel(), MAX_DESTINATION, true);
        HashSet<UUID> ids = new HashSet<>();
        List<RequestItem> items = command.items().stream()
                .map(item -> {
                    if (item == null
                            || item.itemId() == null
                            || item.unitOfMeasure() == null
                            || !ids.add(item.itemId())) {
                        throw ProcurementException.invalidRequest();
                    }
                    return new RequestItem(
                            item.itemId(),
                            optionalText(item.productCode(), MAX_PRODUCT_CODE),
                            requiredText(item.description(), MAX_DESCRIPTION, true),
                            positiveDecimal(item.quantity(), ProcurementException.invalidRequest()),
                            item.unitOfMeasure());
                })
                .sorted(Comparator.comparing(RequestItem::itemId))
                .toList();
        return new CreateRequest(
                command.requestId(),
                destination,
                command.destinationLatitude(),
                command.destinationLongitude(),
                windowStart,
                windowEnd,
                items);
    }

    private static CreateQuote normalize(CreateQuote command) {
        if (command == null
                || command.requestId() == null
                || command.supplierProfileId() == null
                || command.sourceDocumentId() == null
                || command.validUntil() == null
                || command.items() == null
                || command.items().isEmpty()
                || command.items().size() > MAX_ITEMS) {
            throw ProcurementException.invalidQuote();
        }
        String currency;
        try {
            currency = Currency.getInstance(command.currency().strip().toUpperCase(Locale.ROOT))
                    .getCurrencyCode();
        } catch (RuntimeException invalidCurrency) {
            throw ProcurementException.invalidQuote();
        }
        BigDecimal tax = nonNegativeMoney(command.taxAmount());
        HashSet<UUID> ids = new HashSet<>();
        List<QuoteItem> items = command.items().stream()
                .map(item -> {
                    if (item == null || item.requestItemId() == null || !ids.add(item.requestItemId())) {
                        throw ProcurementException.invalidQuote();
                    }
                    return new QuoteItem(item.requestItemId(), nonNegativeMoney(item.unitPrice()));
                })
                .sorted(Comparator.comparing(QuoteItem::requestItemId))
                .toList();
        return new CreateQuote(
                command.requestId(),
                command.supplierProfileId(),
                command.sourceDocumentId(),
                currency,
                tax,
                databaseTime(command.validUntil()),
                items);
    }

    private static BigDecimal positiveDecimal(BigDecimal value, ProcurementException failure) {
        BigDecimal normalized = decimal(value, failure);
        if (normalized.signum() <= 0) {
            throw failure;
        }
        return normalized;
    }

    private static BigDecimal nonNegativeMoney(BigDecimal value) {
        BigDecimal normalized = decimal(value, ProcurementException.invalidQuote());
        if (normalized.signum() < 0) {
            throw ProcurementException.invalidQuote();
        }
        return normalized;
    }

    private static BigDecimal decimal(BigDecimal value, ProcurementException failure) {
        if (value == null) {
            throw failure;
        }
        try {
            BigDecimal normalized = value.setScale(4, RoundingMode.UNNECESSARY);
            if (normalized.precision() > 19) {
                throw failure;
            }
            return normalized;
        } catch (ArithmeticException tooPrecise) {
            throw failure;
        }
    }

    private static String requiredText(String value, int maximum, boolean rejectBlank) {
        if (value == null) {
            throw ProcurementException.invalidRequest();
        }
        String normalized = value.strip();
        if ((rejectBlank && normalized.isEmpty()) || normalized.length() > maximum) {
            throw ProcurementException.invalidRequest();
        }
        return normalized;
    }

    private static String optionalText(String value, int maximum) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.length() > maximum) {
            throw ProcurementException.invalidRequest();
        }
        return normalized;
    }

    private static BigDecimal zeroMoney() {
        return BigDecimal.ZERO.setScale(4);
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public record CreateRequest(
            UUID requestId,
            String destinationLabel,
            double destinationLatitude,
            double destinationLongitude,
            Instant deliveryWindowStart,
            Instant deliveryWindowEnd,
            List<RequestItem> items) {
        public CreateRequest {
            items = items == null ? null : List.copyOf(items);
        }
    }

    public record RequestItem(
            UUID itemId, String productCode, String description, BigDecimal quantity, UnitOfMeasure unitOfMeasure) {}

    public record CreateQuote(
            UUID requestId,
            UUID supplierProfileId,
            UUID sourceDocumentId,
            String currency,
            BigDecimal taxAmount,
            Instant validUntil,
            List<QuoteItem> items) {
        public CreateQuote {
            items = items == null ? null : List.copyOf(items);
        }
    }

    public record QuoteItem(UUID requestItemId, BigDecimal unitPrice) {}
}
