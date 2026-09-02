package za.co.trademesh.modules.procurement.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.procurement.application.AggregationOrderCatalog;

@Repository
class JdbcAggregationOrderCatalog implements AggregationOrderCatalog {

    private static final String CANDIDATE_COLUMNS = """
        candidate.id, candidate.buyer_business_id, candidate.supplier_profile_id,
        candidate.destination_label,
        ST_Y(candidate.destination::geometry) AS destination_latitude,
        ST_X(candidate.destination::geometry) AS destination_longitude,
        candidate.delivery_window_start, candidate.delivery_window_end
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcAggregationOrderCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<OrderCandidate> findConfirmedOrder(UUID buyerBusinessId, UUID orderId) {
        return jdbcTemplate
                .query(
                        "SELECT " + CANDIDATE_COLUMNS
                                + " FROM procurement_order candidate"
                                + " WHERE candidate.buyer_business_id = ? AND candidate.id = ?"
                                + " AND candidate.status = 'CONFIRMED'",
                        (resultSet, rowNumber) -> mapCandidate(resultSet, 0),
                        buyerBusinessId,
                        orderId)
                .stream()
                .findFirst();
    }

    @Override
    public List<OrderCandidate> findNearbyConfirmedOrders(UUID anchorOrderId, double searchRadiusMeters, int limit) {
        return jdbcTemplate.query(
                "SELECT " + CANDIDATE_COLUMNS
                        + ", ST_Distance(candidate.destination, anchor.destination) AS distance_meters"
                        + " FROM procurement_order candidate"
                        + " JOIN procurement_order anchor ON anchor.id = ? AND anchor.status = 'CONFIRMED'"
                        + " WHERE candidate.id <> anchor.id AND candidate.status = 'CONFIRMED'"
                        + " AND ST_DWithin(candidate.destination, anchor.destination, ?)"
                        + " ORDER BY distance_meters, candidate.id LIMIT ?",
                (resultSet, rowNumber) -> mapCandidate(resultSet, resultSet.getDouble("distance_meters")),
                anchorOrderId,
                searchRadiusMeters,
                limit);
    }

    private OrderCandidate mapCandidate(ResultSet resultSet, double distanceMeters) throws SQLException {
        UUID orderId = resultSet.getObject("id", UUID.class);
        List<CargoItem> items = jdbcTemplate.query(
                """
            SELECT product_code, unit_of_measure
              FROM procurement_order_item
             WHERE order_id = ?
             ORDER BY id
            """,
                (itemSet, itemNumber) ->
                        new CargoItem(itemSet.getString("product_code"), itemSet.getString("unit_of_measure")),
                orderId);
        return new OrderCandidate(
                orderId,
                resultSet.getObject("buyer_business_id", UUID.class),
                resultSet.getObject("supplier_profile_id", UUID.class),
                resultSet.getString("destination_label"),
                resultSet.getDouble("destination_latitude"),
                resultSet.getDouble("destination_longitude"),
                distanceMeters,
                instant(resultSet, "delivery_window_start"),
                instant(resultSet, "delivery_window_end"),
                items);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
