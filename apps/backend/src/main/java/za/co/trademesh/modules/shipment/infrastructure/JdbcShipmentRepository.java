package za.co.trademesh.modules.shipment.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.shipment.domain.Shipment;
import za.co.trademesh.modules.shipment.domain.ShipmentActionSource;
import za.co.trademesh.modules.shipment.domain.ShipmentAssignment;
import za.co.trademesh.modules.shipment.domain.ShipmentCargoItem;
import za.co.trademesh.modules.shipment.domain.ShipmentLoadOrder;
import za.co.trademesh.modules.shipment.domain.ShipmentRepository;
import za.co.trademesh.modules.shipment.domain.ShipmentRoutePoint;
import za.co.trademesh.modules.shipment.domain.ShipmentStatus;
import za.co.trademesh.modules.shipment.domain.ShipmentTransition;

@Repository
class JdbcShipmentRepository implements ShipmentRepository {

    private static final String SHIPMENT_COLUMNS = """
        id, requested_by_business_id, client_request_id, input_fingerprint,
        demand_group_suggestion_id, capacity_search_id, capacity_reservation_id,
        capacity_offer_id, transporter_id, reserved_weight_kg,
        reserved_volume_cubic_metres, status, created_by_user_id, created_at, updated_at
        """;
    private static final String ASSIGNMENT_COLUMNS = """
        id, command_id, input_fingerprint, sequence, transporter_id,
        transport_assignment_id, vehicle_id, vehicle_registration_number,
        vehicle_description, driver_id, driver_display_name, driver_reference,
        route_assessment_id, route_calculation_id, route_candidate_id,
        cargo_profile, route_algorithm_version, route_score, route_confidence,
        ST_AsText(route_geometry) AS route_geometry_wkt,
        route_distance_metres, route_duration_seconds, route_toll_estimate_zar,
        started_at, ended_at, reason, correlation_id, source, actor_user_id
        """;
    private static final String TRANSITION_COLUMNS = """
        id, command_id, input_fingerprint, from_status, to_status,
        actor_user_id, occurred_at, reason, correlation_id, source
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcShipmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(Shipment shipment) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO shipment_record (
                id, requested_by_business_id, client_request_id, input_fingerprint,
                demand_group_suggestion_id, capacity_search_id, capacity_reservation_id,
                capacity_offer_id, transporter_id, reserved_weight_kg,
                reserved_volume_cubic_metres, status, created_by_user_id, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                shipment.id(),
                shipment.requestedByBusinessId(),
                shipment.clientRequestId(),
                shipment.inputFingerprint(),
                shipment.demandGroupSuggestionId(),
                shipment.capacitySearchId(),
                shipment.capacityReservationId(),
                shipment.capacityOfferId(),
                shipment.transporterId(),
                shipment.reservedWeightKg(),
                shipment.reservedVolumeCubicMetres(),
                shipment.status().name(),
                shipment.createdByUserId(),
                time(shipment.createdAt()),
                time(shipment.updatedAt()));
        if (written != 1) {
            return false;
        }
        shipment.loadOrders().forEach(order -> saveLoadOrder(shipment.id(), order));
        shipment.assignments().forEach(assignment -> saveAssignment(shipment.id(), assignment));
        shipment.transitions().forEach(transition -> saveInitialTransition(shipment.id(), transition));
        return true;
    }

    @Override
    public Optional<Shipment> findById(UUID businessId, UUID shipmentId) {
        return find(
                "SELECT " + SHIPMENT_COLUMNS + " FROM shipment_record WHERE requested_by_business_id = ? AND id = ?",
                businessId,
                shipmentId);
    }

    @Override
    public Optional<Shipment> findById(UUID shipmentId) {
        return find("SELECT " + SHIPMENT_COLUMNS + " FROM shipment_record WHERE id = ?", shipmentId);
    }

    @Override
    public Optional<Shipment> findByParticipantBusinessId(UUID businessId, UUID shipmentId) {
        return find("""
                SELECT %s
                  FROM shipment_record shipment
                 WHERE shipment.id = ?
                   AND (
                       shipment.requested_by_business_id = ?
                       OR EXISTS (
                           SELECT 1
                             FROM shipment_load_order load_order
                            WHERE load_order.shipment_id = shipment.id
                              AND load_order.buyer_business_id = ?
                       )
                       OR EXISTS (
                           SELECT 1
                             FROM transport_transporter transporter
                            WHERE transporter.id = shipment.transporter_id
                              AND transporter.business_id = ?
                       )
                   )
                """.formatted(SHIPMENT_COLUMNS), shipmentId, businessId, businessId, businessId);
    }

    @Override
    public List<Shipment> findOperational(int limit) {
        return jdbcTemplate.query(
                "SELECT " + SHIPMENT_COLUMNS
                        + " FROM shipment_record WHERE status IN"
                        + " ('AWAITING_COLLECTION', 'COLLECTED', 'IN_TRANSIT', 'DELAYED')"
                        + " ORDER BY updated_at, id LIMIT ?",
                this::mapShipment,
                limit);
    }

    @Override
    public Optional<Shipment> findByIdForUpdate(UUID businessId, UUID shipmentId) {
        return find(
                "SELECT " + SHIPMENT_COLUMNS
                        + " FROM shipment_record WHERE requested_by_business_id = ? AND id = ? FOR UPDATE",
                businessId,
                shipmentId);
    }

    @Override
    public Optional<Shipment> findByRequestId(UUID businessId, UUID requestId) {
        return find(
                "SELECT " + SHIPMENT_COLUMNS
                        + " FROM shipment_record WHERE requested_by_business_id = ? AND client_request_id = ?",
                businessId,
                requestId);
    }

    @Override
    public Optional<ShipmentTransition> findTransitionByCommandId(UUID shipmentId, UUID commandId) {
        return jdbcTemplate
                .query(
                        "SELECT " + TRANSITION_COLUMNS
                                + " FROM shipment_transition WHERE shipment_id = ? AND command_id = ?",
                        this::mapTransition,
                        shipmentId,
                        commandId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean addTransition(UUID shipmentId, ShipmentStatus expectedStatus, ShipmentTransition transition) {
        int inserted = jdbcTemplate.update(
                """
            INSERT INTO shipment_transition (
                id, shipment_id, command_id, input_fingerprint, sequence,
                from_status, to_status, actor_user_id, occurred_at,
                reason, correlation_id, source
            ) VALUES (
                ?, ?, ?, ?,
                (SELECT COALESCE(MAX(sequence) + 1, 0) FROM shipment_transition WHERE shipment_id = ?),
                ?, ?, ?, ?, ?, ?, ?
            )
            ON CONFLICT DO NOTHING
            """,
                transition.id(),
                shipmentId,
                transition.commandId(),
                transition.inputFingerprint(),
                shipmentId,
                transition.fromStatus().name(),
                transition.toStatus().name(),
                transition.actorUserId(),
                time(transition.occurredAt()),
                transition.reason(),
                transition.correlationId(),
                transition.source().name());
        if (inserted != 1) {
            return false;
        }
        return jdbcTemplate.update(
                        """
                    UPDATE shipment_record
                       SET status = ?, updated_at = ?
                     WHERE id = ? AND status = ?
                    """,
                        transition.toStatus().name(),
                        time(transition.occurredAt()),
                        shipmentId,
                        expectedStatus.name())
                == 1;
    }

    @Override
    public Optional<ShipmentAssignment> findAssignmentByCommandId(UUID shipmentId, UUID commandId) {
        return jdbcTemplate
                .query(
                        "SELECT " + ASSIGNMENT_COLUMNS
                                + " FROM shipment_assignment WHERE shipment_id = ? AND command_id = ?",
                        this::mapAssignment,
                        shipmentId,
                        commandId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean replaceAssignment(
            UUID shipmentId, UUID currentAssignmentId, ShipmentAssignment replacement, Instant endedAt) {
        int ended = jdbcTemplate.update("""
            UPDATE shipment_assignment
               SET ended_at = ?
             WHERE shipment_id = ? AND id = ? AND ended_at IS NULL
            """, time(endedAt), shipmentId, currentAssignmentId);
        if (ended != 1) {
            return false;
        }
        saveAssignment(shipmentId, replacement);
        return jdbcTemplate.update(
                        "UPDATE shipment_record SET updated_at = ? WHERE id = ?",
                        time(replacement.startedAt()),
                        shipmentId)
                == 1;
    }

    private Optional<Shipment> find(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapShipment, parameters).stream().findFirst();
    }

    private void saveLoadOrder(UUID shipmentId, ShipmentLoadOrder order) {
        jdbcTemplate.update(
                """
            INSERT INTO shipment_load_order (
                shipment_id, sequence, order_id, buyer_business_id, destination_label,
                destination_latitude, destination_longitude,
                delivery_window_start, delivery_window_end
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                shipmentId,
                order.sequence(),
                order.orderId(),
                order.buyerBusinessId(),
                order.destinationLabel(),
                order.latitude(),
                order.longitude(),
                time(order.deliveryWindowStart()),
                time(order.deliveryWindowEnd()));
        for (int index = 0; index < order.cargoItems().size(); index++) {
            ShipmentCargoItem item = order.cargoItems().get(index);
            jdbcTemplate.update("""
                INSERT INTO shipment_load_cargo_item (
                    shipment_id, order_sequence, sequence, product_code, unit_of_measure
                ) VALUES (?, ?, ?, ?, ?)
                """, shipmentId, order.sequence(), index, item.productCode(), item.unitOfMeasure());
        }
    }

    private void saveAssignment(UUID shipmentId, ShipmentAssignment assignment) {
        jdbcTemplate.update(
                """
            INSERT INTO shipment_assignment (
                id, shipment_id, command_id, input_fingerprint, sequence,
                transporter_id, transport_assignment_id, vehicle_id,
                vehicle_registration_number, vehicle_description,
                driver_id, driver_display_name, driver_reference,
                route_assessment_id, route_calculation_id, route_candidate_id,
                cargo_profile, route_algorithm_version, route_score, route_confidence,
                route_geometry, route_distance_metres, route_duration_seconds,
                route_toll_estimate_zar, started_at, ended_at, reason,
                correlation_id, source, actor_user_id
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ST_GeomFromText(?, 4326), ?, ?, ?, ?, ?, ?, ?, ?, ?
            )
            """,
                assignment.id(),
                shipmentId,
                assignment.commandId(),
                assignment.inputFingerprint(),
                assignment.sequence(),
                assignment.transporterId(),
                assignment.transportAssignmentId(),
                assignment.vehicleId(),
                assignment.vehicleRegistrationNumber(),
                assignment.vehicleDescription(),
                assignment.driverId(),
                assignment.driverDisplayName(),
                assignment.driverReference(),
                assignment.routeAssessmentId(),
                assignment.routeCalculationId(),
                assignment.routeCandidateId(),
                assignment.cargoProfile(),
                assignment.routeAlgorithmVersion(),
                assignment.routeScore(),
                assignment.routeConfidence(),
                lineString(assignment.routeGeometry()),
                assignment.routeDistanceMetres(),
                assignment.routeDurationSeconds(),
                assignment.routeTollEstimateZar(),
                time(assignment.startedAt()),
                nullableTime(assignment.endedAt()),
                assignment.reason(),
                assignment.correlationId(),
                assignment.source().name(),
                assignment.actorUserId());
    }

    private void saveInitialTransition(UUID shipmentId, ShipmentTransition transition) {
        jdbcTemplate.update(
                """
            INSERT INTO shipment_transition (
                id, shipment_id, command_id, input_fingerprint, sequence,
                from_status, to_status, actor_user_id, occurred_at,
                reason, correlation_id, source
            ) VALUES (?, ?, ?, ?, 0, ?, ?, ?, ?, ?, ?, ?)
            """,
                transition.id(),
                shipmentId,
                transition.commandId(),
                transition.inputFingerprint(),
                transition.fromStatus() == null ? null : transition.fromStatus().name(),
                transition.toStatus().name(),
                transition.actorUserId(),
                time(transition.occurredAt()),
                transition.reason(),
                transition.correlationId(),
                transition.source().name());
    }

    private Shipment mapShipment(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID shipmentId = resultSet.getObject("id", UUID.class);
        List<ShipmentLoadOrder> loadOrders =
                jdbcTemplate.query("""
            SELECT sequence, order_id, buyer_business_id, destination_label,
                   destination_latitude, destination_longitude,
                   delivery_window_start, delivery_window_end
              FROM shipment_load_order
             WHERE shipment_id = ?
             ORDER BY sequence
            """, (loadSet, loadRow) -> mapLoadOrder(shipmentId, loadSet), shipmentId);
        List<ShipmentAssignment> assignments = jdbcTemplate.query(
                "SELECT " + ASSIGNMENT_COLUMNS + " FROM shipment_assignment WHERE shipment_id = ? ORDER BY sequence",
                this::mapAssignment,
                shipmentId);
        List<ShipmentTransition> transitions = jdbcTemplate.query(
                "SELECT " + TRANSITION_COLUMNS + " FROM shipment_transition WHERE shipment_id = ? ORDER BY sequence",
                this::mapTransition,
                shipmentId);
        return new Shipment(
                shipmentId,
                resultSet.getObject("requested_by_business_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                resultSet.getObject("demand_group_suggestion_id", UUID.class),
                resultSet.getObject("capacity_search_id", UUID.class),
                resultSet.getObject("capacity_reservation_id", UUID.class),
                resultSet.getObject("capacity_offer_id", UUID.class),
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getBigDecimal("reserved_weight_kg"),
                resultSet.getBigDecimal("reserved_volume_cubic_metres"),
                ShipmentStatus.valueOf(resultSet.getString("status")),
                loadOrders,
                assignments,
                transitions,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                instant(resultSet, "updated_at"));
    }

    private ShipmentLoadOrder mapLoadOrder(UUID shipmentId, ResultSet resultSet) throws SQLException {
        int orderSequence = resultSet.getInt("sequence");
        List<ShipmentCargoItem> cargoItems = jdbcTemplate.query(
                """
            SELECT product_code, unit_of_measure
              FROM shipment_load_cargo_item
             WHERE shipment_id = ? AND order_sequence = ?
             ORDER BY sequence
            """,
                (itemSet, itemRow) ->
                        new ShipmentCargoItem(itemSet.getString("product_code"), itemSet.getString("unit_of_measure")),
                shipmentId,
                orderSequence);
        return new ShipmentLoadOrder(
                orderSequence,
                resultSet.getObject("order_id", UUID.class),
                resultSet.getObject("buyer_business_id", UUID.class),
                resultSet.getString("destination_label"),
                resultSet.getDouble("destination_latitude"),
                resultSet.getDouble("destination_longitude"),
                instant(resultSet, "delivery_window_start"),
                instant(resultSet, "delivery_window_end"),
                cargoItems);
    }

    private ShipmentAssignment mapAssignment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new ShipmentAssignment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                resultSet.getInt("sequence"),
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getObject("transport_assignment_id", UUID.class),
                resultSet.getObject("vehicle_id", UUID.class),
                resultSet.getString("vehicle_registration_number"),
                resultSet.getString("vehicle_description"),
                resultSet.getObject("driver_id", UUID.class),
                resultSet.getString("driver_display_name"),
                resultSet.getString("driver_reference"),
                resultSet.getObject("route_assessment_id", UUID.class),
                resultSet.getObject("route_calculation_id", UUID.class),
                resultSet.getObject("route_candidate_id", UUID.class),
                resultSet.getString("cargo_profile"),
                resultSet.getString("route_algorithm_version"),
                resultSet.getBigDecimal("route_score"),
                resultSet.getBigDecimal("route_confidence"),
                parseLineString(resultSet.getString("route_geometry_wkt")),
                resultSet.getLong("route_distance_metres"),
                resultSet.getLong("route_duration_seconds"),
                resultSet.getBigDecimal("route_toll_estimate_zar"),
                instant(resultSet, "started_at"),
                nullableInstant(resultSet, "ended_at"),
                resultSet.getString("reason"),
                resultSet.getObject("correlation_id", UUID.class),
                ShipmentActionSource.valueOf(resultSet.getString("source")),
                resultSet.getObject("actor_user_id", UUID.class));
    }

    private ShipmentTransition mapTransition(ResultSet resultSet, int rowNumber) throws SQLException {
        String fromStatus = resultSet.getString("from_status");
        return new ShipmentTransition(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                fromStatus == null ? null : ShipmentStatus.valueOf(fromStatus),
                ShipmentStatus.valueOf(resultSet.getString("to_status")),
                resultSet.getObject("actor_user_id", UUID.class),
                instant(resultSet, "occurred_at"),
                resultSet.getString("reason"),
                resultSet.getObject("correlation_id", UUID.class),
                ShipmentActionSource.valueOf(resultSet.getString("source")));
    }

    private static String lineString(List<ShipmentRoutePoint> geometry) {
        String coordinates = geometry.stream()
                .map(point -> decimal(point.longitude()) + " " + decimal(point.latitude()))
                .collect(java.util.stream.Collectors.joining(","));
        return "LINESTRING(" + coordinates + ")";
    }

    private static List<ShipmentRoutePoint> parseLineString(String value) {
        int opening = value.indexOf('(');
        int closing = value.lastIndexOf(')');
        if (opening < 0 || closing <= opening) {
            throw new IllegalStateException("Stored shipment route geometry is invalid");
        }
        List<ShipmentRoutePoint> points = new ArrayList<>();
        Arrays.stream(value.substring(opening + 1, closing).split(","))
                .map(String::strip)
                .forEach(coordinate -> {
                    String[] values = coordinate.split("\\s+");
                    if (values.length != 2) {
                        throw new IllegalStateException("Stored shipment route coordinate is invalid");
                    }
                    points.add(new ShipmentRoutePoint(Double.parseDouble(values[1]), Double.parseDouble(values[0])));
                });
        return List.copyOf(points);
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static OffsetDateTime nullableTime(Instant value) {
        return value == null ? null : time(value);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
