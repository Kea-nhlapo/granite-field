package za.co.trademesh.modules.transport.infrastructure;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityOffer;
import za.co.trademesh.modules.transport.domain.CapacityOfferStatus;
import za.co.trademesh.modules.transport.domain.CargoRestriction;
import za.co.trademesh.modules.transport.domain.Driver;
import za.co.trademesh.modules.transport.domain.DriverStatus;
import za.co.trademesh.modules.transport.domain.DriverVehicleAssignment;
import za.co.trademesh.modules.transport.domain.OfferRouteFit;
import za.co.trademesh.modules.transport.domain.RoutePoint;
import za.co.trademesh.modules.transport.domain.TransportRepository;
import za.co.trademesh.modules.transport.domain.TransporterProfile;
import za.co.trademesh.modules.transport.domain.TransporterStatus;
import za.co.trademesh.modules.transport.domain.Vehicle;
import za.co.trademesh.modules.transport.domain.VehicleStatus;

@Repository
class JdbcTransportRepository implements TransportRepository {

    private static final String TRANSPORTER_COLUMNS =
            "id, business_id, display_name, status, created_by_user_id, created_at";
    private static final String VEHICLE_COLUMNS = """
        id, transporter_id, client_request_id, registration_number, description,
        maximum_weight_kg, maximum_volume_cubic_metres, status,
        created_by_user_id, created_at
        """;
    private static final String DRIVER_COLUMNS = """
        id, transporter_id, client_request_id, display_name, driver_reference,
        status, created_by_user_id, created_at
        """;
    private static final String ASSIGNMENT_COLUMNS = """
        id, transporter_id, client_request_id, vehicle_id, driver_id,
        started_at, ended_at, assigned_by_user_id, ended_by_user_id
        """;
    private static final String OFFER_COLUMNS = """
        id, transporter_id, client_request_id, vehicle_id, driver_assignment_id,
        corridor_radius_metres, departure_window_start, departure_window_end,
        expires_at, total_weight_kg, remaining_weight_kg,
        total_volume_cubic_metres, remaining_volume_cubic_metres,
        status, created_by_user_id, created_at, cancelled_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcTransportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TransporterProfile> findTransporterByBusinessId(UUID businessId) {
        return jdbcTemplate
                .query(
                        "SELECT " + TRANSPORTER_COLUMNS + " FROM transport_transporter WHERE business_id = ?",
                        this::mapTransporter,
                        businessId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean saveTransporter(TransporterProfile transporter) {
        return jdbcTemplate.update(
                        """
            INSERT INTO transport_transporter (
                id, business_id, display_name, status, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (business_id) DO NOTHING
            """,
                        transporter.id(),
                        transporter.businessId(),
                        transporter.displayName(),
                        transporter.status().name(),
                        transporter.createdByUserId(),
                        time(transporter.createdAt()))
                == 1;
    }

    @Override
    public Optional<Vehicle> findVehicle(UUID transporterId, UUID vehicleId) {
        return vehicle(
                "SELECT " + VEHICLE_COLUMNS + " FROM transport_vehicle WHERE transporter_id = ? AND id = ?",
                transporterId,
                vehicleId);
    }

    @Override
    public Optional<Vehicle> findVehicleByRequestId(UUID transporterId, UUID requestId) {
        return vehicle(
                "SELECT " + VEHICLE_COLUMNS
                        + " FROM transport_vehicle WHERE transporter_id = ? AND client_request_id = ?",
                transporterId,
                requestId);
    }

    @Override
    public boolean saveVehicle(Vehicle vehicle) {
        return jdbcTemplate.update(
                        """
            INSERT INTO transport_vehicle (
                id, transporter_id, client_request_id, registration_number, description,
                maximum_weight_kg, maximum_volume_cubic_metres, status,
                created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        vehicle.id(),
                        vehicle.transporterId(),
                        vehicle.clientRequestId(),
                        vehicle.registrationNumber(),
                        vehicle.description(),
                        vehicle.maximumWeightKg(),
                        vehicle.maximumVolumeCubicMetres(),
                        vehicle.status().name(),
                        vehicle.createdByUserId(),
                        time(vehicle.createdAt()))
                == 1;
    }

    @Override
    public Optional<Driver> findDriver(UUID transporterId, UUID driverId) {
        return driver(
                "SELECT " + DRIVER_COLUMNS + " FROM transport_driver WHERE transporter_id = ? AND id = ?",
                transporterId,
                driverId);
    }

    @Override
    public Optional<Driver> findDriverByRequestId(UUID transporterId, UUID requestId) {
        return driver(
                "SELECT " + DRIVER_COLUMNS
                        + " FROM transport_driver WHERE transporter_id = ? AND client_request_id = ?",
                transporterId,
                requestId);
    }

    @Override
    public boolean saveDriver(Driver driver) {
        return jdbcTemplate.update(
                        """
            INSERT INTO transport_driver (
                id, transporter_id, client_request_id, display_name, driver_reference,
                status, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        driver.id(),
                        driver.transporterId(),
                        driver.clientRequestId(),
                        driver.displayName(),
                        driver.driverReference(),
                        driver.status().name(),
                        driver.createdByUserId(),
                        time(driver.createdAt()))
                == 1;
    }

    @Override
    public Optional<DriverVehicleAssignment> findAssignment(UUID transporterId, UUID assignmentId) {
        return assignment(
                "SELECT " + ASSIGNMENT_COLUMNS
                        + " FROM transport_driver_vehicle_assignment WHERE transporter_id = ? AND id = ?",
                transporterId,
                assignmentId);
    }

    @Override
    public Optional<DriverVehicleAssignment> findAssignmentByRequestId(UUID transporterId, UUID requestId) {
        return assignment(
                "SELECT " + ASSIGNMENT_COLUMNS
                        + " FROM transport_driver_vehicle_assignment"
                        + " WHERE transporter_id = ? AND client_request_id = ?",
                transporterId,
                requestId);
    }

    @Override
    public boolean saveAssignment(DriverVehicleAssignment assignment) {
        return jdbcTemplate.update(
                        """
            INSERT INTO transport_driver_vehicle_assignment (
                id, transporter_id, client_request_id, vehicle_id, driver_id,
                started_at, ended_at, assigned_by_user_id, ended_by_user_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        assignment.id(),
                        assignment.transporterId(),
                        assignment.clientRequestId(),
                        assignment.vehicleId(),
                        assignment.driverId(),
                        time(assignment.startedAt()),
                        nullableTime(assignment.endedAt()),
                        assignment.assignedByUserId(),
                        assignment.endedByUserId())
                == 1;
    }

    @Override
    public boolean endAssignment(UUID transporterId, UUID assignmentId, UUID endedByUserId, Instant endedAt) {
        return jdbcTemplate.update("""
            UPDATE transport_driver_vehicle_assignment
               SET ended_at = ?, ended_by_user_id = ?
             WHERE transporter_id = ? AND id = ? AND ended_at IS NULL AND started_at <= ?
            """, time(endedAt), endedByUserId, transporterId, assignmentId, time(endedAt)) == 1;
    }

    @Override
    public List<DriverVehicleAssignment> findVehicleAssignmentHistory(UUID transporterId, UUID vehicleId) {
        return jdbcTemplate.query(
                "SELECT " + ASSIGNMENT_COLUMNS
                        + " FROM transport_driver_vehicle_assignment"
                        + " WHERE transporter_id = ? AND vehicle_id = ? ORDER BY started_at, id",
                this::mapAssignment,
                transporterId,
                vehicleId);
    }

    @Override
    public Optional<CapacityOffer> findOffer(UUID transporterId, UUID offerId) {
        return offer(
                "SELECT " + OFFER_COLUMNS + " FROM transport_capacity_offer WHERE transporter_id = ? AND id = ?",
                transporterId,
                offerId);
    }

    @Override
    public Optional<CapacityOffer> findOfferByRequestId(UUID transporterId, UUID requestId) {
        return offer(
                "SELECT " + OFFER_COLUMNS
                        + " FROM transport_capacity_offer WHERE transporter_id = ? AND client_request_id = ?",
                transporterId,
                requestId);
    }

    @Override
    public boolean saveOffer(CapacityOffer offer) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO transport_capacity_offer (
                id, transporter_id, client_request_id, vehicle_id, driver_assignment_id,
                route_corridor, corridor_radius_metres,
                departure_window_start, departure_window_end, expires_at,
                total_weight_kg, remaining_weight_kg,
                total_volume_cubic_metres, remaining_volume_cubic_metres,
                status, created_by_user_id, created_at, cancelled_at
            ) VALUES (?, ?, ?, ?, ?, ST_GeogFromText(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                offer.id(),
                offer.transporterId(),
                offer.clientRequestId(),
                offer.vehicleId(),
                offer.driverAssignmentId(),
                lineString(offer.routePoints()),
                offer.corridorRadiusMetres(),
                time(offer.departureWindowStart()),
                time(offer.departureWindowEnd()),
                time(offer.expiresAt()),
                offer.totalCapacity().weightKg(),
                offer.remainingCapacity().weightKg(),
                offer.totalCapacity().volumeCubicMetres(),
                offer.remainingCapacity().volumeCubicMetres(),
                offer.status().name(),
                offer.createdByUserId(),
                time(offer.createdAt()),
                nullableTime(offer.cancelledAt()));
        if (written != 1) {
            return false;
        }
        for (RoutePoint point : offer.routePoints()) {
            jdbcTemplate.update("""
                INSERT INTO transport_capacity_offer_route_point (
                    offer_id, point_sequence, label, location
                ) VALUES (?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
                """, offer.id(), point.sequence(), point.label(), point.longitude(), point.latitude());
        }
        for (CargoRestriction restriction : offer.restrictions()) {
            jdbcTemplate.update(
                    "INSERT INTO transport_capacity_offer_restriction (offer_id, restriction) VALUES (?, ?)",
                    offer.id(),
                    restriction.name());
        }
        return true;
    }

    @Override
    public boolean cancelOffer(UUID transporterId, UUID offerId, Instant cancelledAt) {
        return jdbcTemplate.update("""
            UPDATE transport_capacity_offer
               SET status = 'CANCELLED', cancelled_at = ?
             WHERE transporter_id = ? AND id = ? AND status = 'ACTIVE' AND expires_at > ?
            """, time(cancelledAt), transporterId, offerId, time(cancelledAt)) == 1;
    }

    @Override
    public void expireOffer(UUID transporterId, UUID offerId, Instant now) {
        jdbcTemplate.update("""
            UPDATE transport_capacity_offer
               SET status = 'EXPIRED'
             WHERE transporter_id = ? AND id = ? AND status = 'ACTIVE' AND expires_at <= ?
            """, transporterId, offerId, time(now));
    }

    @Override
    public List<CapacityOffer> findAvailableOffers(Instant now, int limit) {
        return jdbcTemplate.query(
                "SELECT " + OFFER_COLUMNS
                        + " FROM transport_capacity_offer"
                        + " WHERE status = 'ACTIVE' AND expires_at > ? AND departure_window_end > ?"
                        + " AND remaining_weight_kg > 0 AND remaining_volume_cubic_metres > 0"
                        + " ORDER BY departure_window_start, id LIMIT ?",
                this::mapOffer,
                time(now),
                time(now),
                limit);
    }

    @Override
    public Optional<OfferRouteFit> measureRouteFit(UUID offerId, List<RoutePoint> destinations) {
        if (destinations == null || destinations.isEmpty()) {
            return Optional.empty();
        }
        String values = java.util.stream.IntStream.range(0, destinations.size())
                .mapToObj(ignored -> "(?, ?)")
                .collect(Collectors.joining(", "));
        String sql = """
            WITH destination(longitude, latitude) AS (VALUES %s)
            SELECT MAX(ST_Distance(
                       offer.route_corridor,
                       ST_SetSRID(ST_MakePoint(destination.longitude, destination.latitude), 4326)::geography
                   )) AS maximum_distance_metres,
                   SUM(ST_Distance(
                       offer.route_corridor,
                       ST_SetSRID(ST_MakePoint(destination.longitude, destination.latitude), 4326)::geography
                   )) * 2 AS estimated_added_distance_metres
              FROM transport_capacity_offer offer
              CROSS JOIN destination
             WHERE offer.id = ?
            """.formatted(values);
        List<Object> parameters = new java.util.ArrayList<>();
        destinations.forEach(point -> {
            parameters.add(point.longitude());
            parameters.add(point.latitude());
        });
        parameters.add(offerId);
        OfferRouteFit fit = jdbcTemplate.query(
                sql,
                resultSet -> {
                    if (!resultSet.next() || resultSet.getObject("maximum_distance_metres") == null) {
                        return null;
                    }
                    return new OfferRouteFit(
                            resultSet.getDouble("maximum_distance_metres"),
                            resultSet.getDouble("estimated_added_distance_metres"));
                },
                parameters.toArray());
        return Optional.ofNullable(fit);
    }

    @Override
    public boolean tryReserveCapacity(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres, Instant now) {
        return jdbcTemplate.update("""
            UPDATE transport_capacity_offer
               SET remaining_weight_kg = remaining_weight_kg - ?,
                   remaining_volume_cubic_metres = remaining_volume_cubic_metres - ?
             WHERE id = ?
               AND status = 'ACTIVE'
               AND expires_at > ?
               AND remaining_weight_kg >= ?
               AND remaining_volume_cubic_metres >= ?
            """, weightKg, volumeCubicMetres, offerId, time(now), weightKg, volumeCubicMetres)
                == 1;
    }

    @Override
    public boolean releaseCapacity(UUID offerId, BigDecimal weightKg, BigDecimal volumeCubicMetres) {
        return jdbcTemplate.update("""
            UPDATE transport_capacity_offer
               SET remaining_weight_kg = remaining_weight_kg + ?,
                   remaining_volume_cubic_metres = remaining_volume_cubic_metres + ?
             WHERE id = ?
               AND remaining_weight_kg + ? <= total_weight_kg
               AND remaining_volume_cubic_metres + ? <= total_volume_cubic_metres
            """, weightKg, volumeCubicMetres, offerId, weightKg, volumeCubicMetres) == 1;
    }

    private Optional<Vehicle> vehicle(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapVehicle, parameters).stream().findFirst();
    }

    private Optional<Driver> driver(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapDriver, parameters).stream().findFirst();
    }

    private Optional<DriverVehicleAssignment> assignment(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapAssignment, parameters).stream().findFirst();
    }

    private Optional<CapacityOffer> offer(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapOffer, parameters).stream().findFirst();
    }

    private TransporterProfile mapTransporter(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TransporterProfile(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getString("display_name"),
                TransporterStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private Vehicle mapVehicle(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Vehicle(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getString("registration_number"),
                resultSet.getString("description"),
                resultSet.getBigDecimal("maximum_weight_kg"),
                resultSet.getBigDecimal("maximum_volume_cubic_metres"),
                VehicleStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private Driver mapDriver(ResultSet resultSet, int rowNumber) throws SQLException {
        return new Driver(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("driver_reference"),
                DriverStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private DriverVehicleAssignment mapAssignment(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DriverVehicleAssignment(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getObject("vehicle_id", UUID.class),
                resultSet.getObject("driver_id", UUID.class),
                instant(resultSet, "started_at"),
                nullableInstant(resultSet, "ended_at"),
                resultSet.getObject("assigned_by_user_id", UUID.class),
                resultSet.getObject("ended_by_user_id", UUID.class));
    }

    private CapacityOffer mapOffer(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID offerId = resultSet.getObject("id", UUID.class);
        List<RoutePoint> points = jdbcTemplate.query(
                """
            SELECT point_sequence, label,
                   ST_Y(location::geometry) AS latitude,
                   ST_X(location::geometry) AS longitude
              FROM transport_capacity_offer_route_point
             WHERE offer_id = ?
             ORDER BY point_sequence
            """,
                (pointSet, pointNumber) -> new RoutePoint(
                        pointSet.getInt("point_sequence"),
                        pointSet.getString("label"),
                        pointSet.getDouble("latitude"),
                        pointSet.getDouble("longitude")),
                offerId);
        List<CargoRestriction> restrictions = jdbcTemplate.query(
                """
            SELECT restriction
              FROM transport_capacity_offer_restriction
             WHERE offer_id = ?
             ORDER BY restriction
            """,
                (restrictionSet, restrictionNumber) ->
                        CargoRestriction.valueOf(restrictionSet.getString("restriction")),
                offerId);
        return new CapacityOffer(
                offerId,
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getObject("vehicle_id", UUID.class),
                resultSet.getObject("driver_assignment_id", UUID.class),
                points,
                resultSet.getInt("corridor_radius_metres"),
                instant(resultSet, "departure_window_start"),
                instant(resultSet, "departure_window_end"),
                instant(resultSet, "expires_at"),
                restrictions,
                new Capacity(
                        resultSet.getBigDecimal("total_weight_kg"),
                        resultSet.getBigDecimal("total_volume_cubic_metres")),
                new Capacity(
                        resultSet.getBigDecimal("remaining_weight_kg"),
                        resultSet.getBigDecimal("remaining_volume_cubic_metres")),
                CapacityOfferStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "cancelled_at"));
    }

    private static String lineString(List<RoutePoint> points) {
        return "SRID=4326;LINESTRING("
                + points.stream()
                        .map(point -> point.longitude() + " " + point.latitude())
                        .collect(Collectors.joining(","))
                + ")";
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
