package za.co.trademesh.modules.telemetry.infrastructure;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.telemetry.application.BackhaulCandidateCatalog;

@Repository
class JdbcBackhaulCandidateCatalog implements BackhaulCandidateCatalog {

    private final JdbcTemplate jdbcTemplate;

    JdbcBackhaulCandidateCatalog(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<Candidate> find(
            UUID currentShipmentId,
            double currentLatitude,
            double currentLongitude,
            Instant availableFrom,
            Instant availableThrough,
            double radiusMetres,
            int limit) {
        return jdbcTemplate.query(
                """
                SELECT shipment.id AS shipment_id,
                       shipment.requested_by_business_id AS business_id,
                       ST_Y(ST_StartPoint(assignment.route_geometry)) AS pickup_latitude,
                       ST_X(ST_StartPoint(assignment.route_geometry)) AS pickup_longitude,
                       ST_Y(ST_EndPoint(assignment.route_geometry)) AS destination_latitude,
                       ST_X(ST_EndPoint(assignment.route_geometry)) AS destination_longitude,
                       windows.window_start,
                       windows.window_end,
                       ROUND(ST_Distance(
                           ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                           ST_StartPoint(assignment.route_geometry)::geography
                       ))::BIGINT AS straight_line_pickup_distance_metres,
                       trust.average_rating,
                       trust.delivery_success_rate
                  FROM shipment_record shipment
                  JOIN shipment_assignment assignment
                    ON assignment.shipment_id = shipment.id
                   AND assignment.ended_at IS NULL
                  JOIN LATERAL (
                      SELECT MIN(delivery_window_start) AS window_start,
                             MAX(delivery_window_end) AS window_end
                        FROM shipment_load_order
                       WHERE shipment_id = shipment.id
                  ) windows ON TRUE
                  LEFT JOIN trust_public_summary trust
                    ON trust.business_id = shipment.requested_by_business_id
                 WHERE shipment.id <> ?
                   AND shipment.status = 'AWAITING_COLLECTION'
                   AND windows.window_end >= ?
                   AND windows.window_start <= ?
                   AND ST_DWithin(
                       ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography,
                       ST_StartPoint(assignment.route_geometry)::geography,
                       ?
                   )
                 ORDER BY straight_line_pickup_distance_metres, shipment.id
                 LIMIT ?
                """,
                (resultSet, rowNumber) -> new Candidate(
                        resultSet.getObject("shipment_id", UUID.class),
                        resultSet.getObject("business_id", UUID.class),
                        resultSet.getDouble("pickup_latitude"),
                        resultSet.getDouble("pickup_longitude"),
                        resultSet.getDouble("destination_latitude"),
                        resultSet.getDouble("destination_longitude"),
                        resultSet
                                .getObject("window_start", OffsetDateTime.class)
                                .toInstant(),
                        resultSet.getObject("window_end", OffsetDateTime.class).toInstant(),
                        resultSet.getLong("straight_line_pickup_distance_metres"),
                        resultSet.getBigDecimal("average_rating"),
                        resultSet.getBigDecimal("delivery_success_rate")),
                currentLongitude,
                currentLatitude,
                currentShipmentId,
                OffsetDateTime.ofInstant(availableFrom, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(availableThrough, ZoneOffset.UTC),
                currentLongitude,
                currentLatitude,
                radiusMetres,
                limit);
    }
}
