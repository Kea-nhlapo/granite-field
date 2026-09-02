package za.co.trademesh.modules.procurement.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
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

@Repository
class JdbcProcurementRepository implements ProcurementRepository {

    private static final String REQUEST_COLUMNS = """
        id, buyer_business_id, status, destination_label,
        ST_Y(destination::geometry) AS destination_latitude,
        ST_X(destination::geometry) AS destination_longitude,
        delivery_window_start, delivery_window_end, created_by_user_id, created_at, updated_at
        """;

    private static final String QUOTE_COLUMNS = """
        id, request_id, buyer_business_id, supplier_profile_id, source_document_id,
        status, currency, subtotal, tax_amount, total, valid_until, created_by_user_id, created_at
        """;

    private static final String ORDER_COLUMNS = """
        id, request_id, source_quote_id, buyer_business_id, supplier_profile_id,
        source_document_id, status, currency, subtotal, tax_amount, total, destination_label,
        ST_Y(destination::geometry) AS destination_latitude,
        ST_X(destination::geometry) AS destination_longitude,
        delivery_window_start, delivery_window_end, confirmed_by_user_id, confirmed_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcProcurementRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean saveRequest(ProductRequest request, UUID clientRequestId) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO procurement_request (
                id, buyer_business_id, client_request_id, status, destination_label, destination,
                delivery_window_start, delivery_window_end, created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                request.id(),
                request.buyerBusinessId(),
                clientRequestId,
                request.status().name(),
                request.destinationLabel(),
                request.destinationLongitude(),
                request.destinationLatitude(),
                time(request.deliveryWindowStart()),
                time(request.deliveryWindowEnd()),
                request.createdByUserId(),
                time(request.createdAt()),
                time(request.updatedAt()));
        if (written != 1) {
            return false;
        }
        for (ProductRequestItem item : request.items()) {
            jdbcTemplate.update(
                    """
                INSERT INTO procurement_request_item (
                    id, request_id, product_code, description, quantity, unit_of_measure
                ) VALUES (?, ?, ?, ?, ?, ?)
                """,
                    item.id(),
                    request.id(),
                    item.productCode(),
                    item.description(),
                    item.quantity(),
                    item.unitOfMeasure().name());
        }
        return true;
    }

    @Override
    public Optional<ProductRequest> findRequest(UUID buyerBusinessId, UUID requestId) {
        return request(
                "SELECT " + REQUEST_COLUMNS + " FROM procurement_request WHERE buyer_business_id = ? AND id = ?",
                buyerBusinessId,
                requestId);
    }

    @Override
    public Optional<ProductRequest> findRequestForUpdate(UUID buyerBusinessId, UUID requestId) {
        return request(
                "SELECT " + REQUEST_COLUMNS
                        + " FROM procurement_request WHERE buyer_business_id = ? AND id = ? FOR UPDATE",
                buyerBusinessId,
                requestId);
    }

    @Override
    public Optional<ProductRequest> findRequestByClientRequestId(UUID buyerBusinessId, UUID clientRequestId) {
        return request(
                "SELECT " + REQUEST_COLUMNS
                        + " FROM procurement_request WHERE buyer_business_id = ? AND client_request_id = ?",
                buyerBusinessId,
                clientRequestId);
    }

    @Override
    public boolean markRequestQuoted(UUID requestId, Instant now) {
        return jdbcTemplate.update("""
            UPDATE procurement_request
               SET status = 'QUOTED', updated_at = ?
             WHERE id = ? AND status IN ('OPEN', 'QUOTED')
            """, time(now), requestId) == 1;
    }

    @Override
    public boolean cancelRequest(UUID requestId, Instant now) {
        return jdbcTemplate.update("""
            UPDATE procurement_request
               SET status = 'CANCELLED', updated_at = ?
             WHERE id = ? AND status IN ('OPEN', 'QUOTED')
            """, time(now), requestId) == 1;
    }

    @Override
    public boolean markRequestOrdered(UUID requestId, Instant now) {
        return jdbcTemplate.update("""
            UPDATE procurement_request
               SET status = 'ORDERED', updated_at = ?
             WHERE id = ? AND status = 'QUOTED'
            """, time(now), requestId) == 1;
    }

    @Override
    public boolean saveQuote(SupplierQuote quote, UUID clientRequestId) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO procurement_quote (
                id, request_id, buyer_business_id, supplier_profile_id, source_document_id,
                client_request_id, status, currency, subtotal, tax_amount, total,
                valid_until, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                quote.id(),
                quote.requestId(),
                quote.buyerBusinessId(),
                quote.supplierProfileId(),
                quote.sourceDocumentId(),
                clientRequestId,
                quote.status().name(),
                quote.currency(),
                quote.subtotal(),
                quote.taxAmount(),
                quote.total(),
                time(quote.validUntil()),
                quote.createdByUserId(),
                time(quote.createdAt()));
        if (written != 1) {
            return false;
        }
        for (QuoteLineItem item : quote.items()) {
            jdbcTemplate.update(
                    """
                INSERT INTO procurement_quote_item (
                    id, quote_id, request_item_id, description, quantity,
                    unit_of_measure, unit_price, line_total
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    item.id(),
                    quote.id(),
                    item.requestItemId(),
                    item.description(),
                    item.quantity(),
                    item.unitOfMeasure().name(),
                    item.unitPrice(),
                    item.lineTotal());
        }
        return true;
    }

    @Override
    public Optional<SupplierQuote> findQuote(UUID buyerBusinessId, UUID quoteId) {
        return quote(
                "SELECT " + QUOTE_COLUMNS + " FROM procurement_quote WHERE buyer_business_id = ? AND id = ?",
                buyerBusinessId,
                quoteId);
    }

    @Override
    public Optional<SupplierQuote> findQuoteByClientRequestId(
            UUID buyerBusinessId, UUID requestId, UUID clientRequestId) {
        return quote(
                "SELECT " + QUOTE_COLUMNS
                        + " FROM procurement_quote WHERE buyer_business_id = ? AND request_id = ? AND client_request_id = ?",
                buyerBusinessId,
                requestId,
                clientRequestId);
    }

    @Override
    public Optional<SupplierQuote> findQuoteBySourceDocument(UUID sourceDocumentId) {
        return quote(
                "SELECT " + QUOTE_COLUMNS + " FROM procurement_quote WHERE source_document_id = ?", sourceDocumentId);
    }

    @Override
    public boolean acceptQuote(UUID quoteId) {
        return jdbcTemplate.update(
                        "UPDATE procurement_quote SET status = 'ACCEPTED' WHERE id = ? AND status = 'ACTIVE'", quoteId)
                == 1;
    }

    @Override
    public boolean saveOrder(ConfirmedOrder order, UUID confirmationRequestId) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO procurement_order (
                id, request_id, source_quote_id, buyer_business_id, supplier_profile_id,
                source_document_id, confirmation_request_id, status, currency, subtotal,
                tax_amount, total, destination_label, destination, delivery_window_start,
                delivery_window_end, confirmed_by_user_id, confirmed_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                order.id(),
                order.requestId(),
                order.sourceQuoteId(),
                order.buyerBusinessId(),
                order.supplierProfileId(),
                order.sourceDocumentId(),
                confirmationRequestId,
                order.status().name(),
                order.currency(),
                order.subtotal(),
                order.taxAmount(),
                order.total(),
                order.destinationLabel(),
                order.destinationLongitude(),
                order.destinationLatitude(),
                time(order.deliveryWindowStart()),
                time(order.deliveryWindowEnd()),
                order.confirmedByUserId(),
                time(order.confirmedAt()));
        if (written != 1) {
            return false;
        }
        for (OrderLineItem item : order.items()) {
            jdbcTemplate.update(
                    """
                INSERT INTO procurement_order_item (
                    id, order_id, source_request_item_id, product_code, description,
                    quantity, unit_of_measure, unit_price, line_total
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                    item.id(),
                    order.id(),
                    item.sourceRequestItemId(),
                    item.productCode(),
                    item.description(),
                    item.quantity(),
                    item.unitOfMeasure().name(),
                    item.unitPrice(),
                    item.lineTotal());
        }
        return true;
    }

    @Override
    public Optional<ConfirmedOrder> findOrder(UUID buyerBusinessId, UUID orderId) {
        return order(
                "SELECT " + ORDER_COLUMNS + " FROM procurement_order WHERE buyer_business_id = ? AND id = ?",
                buyerBusinessId,
                orderId);
    }

    @Override
    public Optional<ConfirmedOrder> findOrderByConfirmationRequestId(UUID buyerBusinessId, UUID confirmationRequestId) {
        return order(
                "SELECT " + ORDER_COLUMNS
                        + " FROM procurement_order WHERE buyer_business_id = ? AND confirmation_request_id = ?",
                buyerBusinessId,
                confirmationRequestId);
    }

    @Override
    public Optional<ConfirmedOrder> findOrderByQuoteId(UUID quoteId) {
        return order("SELECT " + ORDER_COLUMNS + " FROM procurement_order WHERE source_quote_id = ?", quoteId);
    }

    private Optional<ProductRequest> request(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapRequest, parameters).stream().findFirst();
    }

    private ProductRequest mapRequest(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID requestId = resultSet.getObject("id", UUID.class);
        List<ProductRequestItem> items = jdbcTemplate.query(
                """
            SELECT id, product_code, description, quantity, unit_of_measure
              FROM procurement_request_item WHERE request_id = ? ORDER BY id
            """,
                (itemSet, itemNumber) -> new ProductRequestItem(
                        itemSet.getObject("id", UUID.class),
                        itemSet.getString("product_code"),
                        itemSet.getString("description"),
                        itemSet.getBigDecimal("quantity"),
                        UnitOfMeasure.valueOf(itemSet.getString("unit_of_measure"))),
                requestId);
        return new ProductRequest(
                requestId,
                resultSet.getObject("buyer_business_id", UUID.class),
                ProcurementRequestStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("destination_label"),
                resultSet.getDouble("destination_latitude"),
                resultSet.getDouble("destination_longitude"),
                instant(resultSet, "delivery_window_start"),
                instant(resultSet, "delivery_window_end"),
                items,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private Optional<SupplierQuote> quote(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapQuote, parameters).stream().findFirst();
    }

    private SupplierQuote mapQuote(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID quoteId = resultSet.getObject("id", UUID.class);
        List<QuoteLineItem> items = jdbcTemplate.query(
                """
            SELECT id, request_item_id, description, quantity, unit_of_measure, unit_price, line_total
              FROM procurement_quote_item WHERE quote_id = ? ORDER BY request_item_id
            """,
                (itemSet, itemNumber) -> new QuoteLineItem(
                        itemSet.getObject("id", UUID.class),
                        itemSet.getObject("request_item_id", UUID.class),
                        itemSet.getString("description"),
                        itemSet.getBigDecimal("quantity"),
                        UnitOfMeasure.valueOf(itemSet.getString("unit_of_measure")),
                        itemSet.getBigDecimal("unit_price"),
                        itemSet.getBigDecimal("line_total")),
                quoteId);
        return new SupplierQuote(
                quoteId,
                resultSet.getObject("request_id", UUID.class),
                resultSet.getObject("buyer_business_id", UUID.class),
                resultSet.getObject("supplier_profile_id", UUID.class),
                resultSet.getObject("source_document_id", UUID.class),
                QuoteStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("currency").strip(),
                resultSet.getBigDecimal("subtotal"),
                resultSet.getBigDecimal("tax_amount"),
                resultSet.getBigDecimal("total"),
                instant(resultSet, "valid_until"),
                items,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private Optional<ConfirmedOrder> order(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapOrder, parameters).stream().findFirst();
    }

    private ConfirmedOrder mapOrder(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID orderId = resultSet.getObject("id", UUID.class);
        List<OrderLineItem> items = jdbcTemplate.query(
                """
            SELECT id, source_request_item_id, product_code, description, quantity,
                   unit_of_measure, unit_price, line_total
              FROM procurement_order_item WHERE order_id = ? ORDER BY source_request_item_id
            """,
                (itemSet, itemNumber) -> new OrderLineItem(
                        itemSet.getObject("id", UUID.class),
                        itemSet.getObject("source_request_item_id", UUID.class),
                        itemSet.getString("product_code"),
                        itemSet.getString("description"),
                        itemSet.getBigDecimal("quantity"),
                        UnitOfMeasure.valueOf(itemSet.getString("unit_of_measure")),
                        itemSet.getBigDecimal("unit_price"),
                        itemSet.getBigDecimal("line_total")),
                orderId);
        return new ConfirmedOrder(
                orderId,
                resultSet.getObject("request_id", UUID.class),
                resultSet.getObject("source_quote_id", UUID.class),
                resultSet.getObject("buyer_business_id", UUID.class),
                resultSet.getObject("supplier_profile_id", UUID.class),
                resultSet.getObject("source_document_id", UUID.class),
                OrderStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("currency").strip(),
                resultSet.getBigDecimal("subtotal"),
                resultSet.getBigDecimal("tax_amount"),
                resultSet.getBigDecimal("total"),
                resultSet.getString("destination_label"),
                resultSet.getDouble("destination_latitude"),
                resultSet.getDouble("destination_longitude"),
                instant(resultSet, "delivery_window_start"),
                instant(resultSet, "delivery_window_end"),
                items,
                resultSet.getObject("confirmed_by_user_id", UUID.class),
                instant(resultSet, "confirmed_at"));
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
