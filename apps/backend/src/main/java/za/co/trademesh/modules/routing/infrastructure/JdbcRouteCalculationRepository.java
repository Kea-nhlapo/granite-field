package za.co.trademesh.modules.routing.infrastructure;

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
import za.co.trademesh.modules.routing.domain.CandidateRoute;
import za.co.trademesh.modules.routing.domain.GeoPoint;
import za.co.trademesh.modules.routing.domain.RouteAvoidance;
import za.co.trademesh.modules.routing.domain.RouteCalculation;
import za.co.trademesh.modules.routing.domain.RouteCalculationRepository;
import za.co.trademesh.modules.routing.domain.RouteSegment;
import za.co.trademesh.modules.routing.domain.VehicleLimits;

@Repository
class JdbcRouteCalculationRepository implements RouteCalculationRepository {

    private static final String CALCULATION_COLUMNS = """
        id, requested_by_business_id, client_request_id, recalculation_of_id,
        input_fingerprint, origin_label, origin_latitude, origin_longitude,
        destination_label, destination_latitude, destination_longitude,
        maximum_weight_kg, maximum_height_metres, maximum_width_metres,
        maximum_length_metres, provider_name, provider_version,
        fallback_used, fallback_reason, created_by_user_id, created_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcRouteCalculationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean save(RouteCalculation calculation) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO routing_calculation (
                id, requested_by_business_id, client_request_id, recalculation_of_id,
                input_fingerprint, origin_label, origin_latitude, origin_longitude,
                destination_label, destination_latitude, destination_longitude,
                maximum_weight_kg, maximum_height_metres, maximum_width_metres,
                maximum_length_metres, provider_name, provider_version,
                fallback_used, fallback_reason, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                calculation.id(),
                calculation.requestedByBusinessId(),
                calculation.clientRequestId(),
                calculation.recalculationOfId(),
                calculation.inputFingerprint(),
                calculation.origin().label(),
                calculation.origin().latitude(),
                calculation.origin().longitude(),
                calculation.destination().label(),
                calculation.destination().latitude(),
                calculation.destination().longitude(),
                calculation.vehicleLimits().maximumWeightKg(),
                calculation.vehicleLimits().maximumHeightMetres(),
                calculation.vehicleLimits().maximumWidthMetres(),
                calculation.vehicleLimits().maximumLengthMetres(),
                calculation.providerName(),
                calculation.providerVersion(),
                calculation.fallbackUsed(),
                calculation.fallbackReason(),
                calculation.createdByUserId(),
                time(calculation.createdAt()));
        if (written != 1) {
            return false;
        }
        for (int index = 0; index < calculation.waypoints().size(); index++) {
            GeoPoint waypoint = calculation.waypoints().get(index);
            jdbcTemplate.update(
                    """
                INSERT INTO routing_waypoint (calculation_id, sequence, label, latitude, longitude)
                VALUES (?, ?, ?, ?, ?)
                """, calculation.id(), index, waypoint.label(), waypoint.latitude(), waypoint.longitude());
        }
        calculation
                .avoidances()
                .forEach(avoidance -> jdbcTemplate.update(
                        "INSERT INTO routing_avoidance (calculation_id, avoidance) VALUES (?, ?)",
                        calculation.id(),
                        avoidance.name()));
        calculation.candidates().forEach(candidate -> saveCandidate(calculation.id(), candidate));
        return true;
    }

    @Override
    public Optional<RouteCalculation> findById(UUID businessId, UUID calculationId) {
        return find(
                "SELECT " + CALCULATION_COLUMNS
                        + " FROM routing_calculation WHERE requested_by_business_id = ? AND id = ?",
                businessId,
                calculationId);
    }

    @Override
    public Optional<RouteCalculation> findByRequestId(UUID businessId, UUID requestId) {
        return find(
                "SELECT " + CALCULATION_COLUMNS
                        + " FROM routing_calculation WHERE requested_by_business_id = ? AND client_request_id = ?",
                businessId,
                requestId);
    }

    private Optional<RouteCalculation> find(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapCalculation, parameters).stream()
                .findFirst();
    }

    private void saveCandidate(UUID calculationId, CandidateRoute candidate) {
        jdbcTemplate.update(
                """
            INSERT INTO routing_candidate (
                id, calculation_id, sequence, provider_candidate_key, label,
                geometry, distance_metres, duration_seconds, toll_estimate_zar
            ) VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?)
            """,
                candidate.id(),
                calculationId,
                candidate.sequence(),
                candidate.providerCandidateKey(),
                candidate.label(),
                lineString(candidate.geometry()),
                candidate.distanceMetres(),
                candidate.durationSeconds(),
                candidate.tollEstimateZar());
        candidate
                .segments()
                .forEach(segment -> jdbcTemplate.update(
                        """
            INSERT INTO routing_segment (
                id, candidate_id, sequence, from_label, to_label, geometry,
                distance_metres, duration_seconds, toll_estimate_zar
            ) VALUES (?, ?, ?, ?, ?, ST_GeomFromText(?, 4326), ?, ?, ?)
            """,
                        segment.id(),
                        candidate.id(),
                        segment.sequence(),
                        segment.fromLabel(),
                        segment.toLabel(),
                        lineString(segment.geometry()),
                        segment.distanceMetres(),
                        segment.durationSeconds(),
                        segment.tollEstimateZar()));
    }

    private RouteCalculation mapCalculation(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID calculationId = resultSet.getObject("id", UUID.class);
        List<GeoPoint> waypoints = jdbcTemplate.query(
                """
            SELECT label, latitude, longitude
              FROM routing_waypoint
             WHERE calculation_id = ?
             ORDER BY sequence
            """,
                (waypointSet, waypointNumber) -> new GeoPoint(
                        waypointSet.getString("label"),
                        waypointSet.getDouble("latitude"),
                        waypointSet.getDouble("longitude")),
                calculationId);
        List<RouteAvoidance> avoidances = jdbcTemplate.query(
                """
            SELECT avoidance
              FROM routing_avoidance
             WHERE calculation_id = ?
             ORDER BY avoidance
            """,
                (avoidanceSet, avoidanceNumber) -> RouteAvoidance.valueOf(avoidanceSet.getString("avoidance")),
                calculationId);
        List<CandidateRoute> candidates = jdbcTemplate.query("""
            SELECT id, sequence, provider_candidate_key, label,
                   ST_AsText(geometry) AS geometry_wkt,
                   distance_metres, duration_seconds, toll_estimate_zar
              FROM routing_candidate
             WHERE calculation_id = ?
             ORDER BY sequence
            """, this::mapCandidate, calculationId);
        return new RouteCalculation(
                calculationId,
                resultSet.getObject("requested_by_business_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getObject("recalculation_of_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                new GeoPoint(
                        resultSet.getString("origin_label"),
                        resultSet.getDouble("origin_latitude"),
                        resultSet.getDouble("origin_longitude")),
                new GeoPoint(
                        resultSet.getString("destination_label"),
                        resultSet.getDouble("destination_latitude"),
                        resultSet.getDouble("destination_longitude")),
                waypoints,
                new VehicleLimits(
                        resultSet.getBigDecimal("maximum_weight_kg"),
                        resultSet.getBigDecimal("maximum_height_metres"),
                        resultSet.getBigDecimal("maximum_width_metres"),
                        resultSet.getBigDecimal("maximum_length_metres")),
                avoidances,
                resultSet.getString("provider_name"),
                resultSet.getString("provider_version"),
                resultSet.getBoolean("fallback_used"),
                resultSet.getString("fallback_reason"),
                candidates,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private CandidateRoute mapCandidate(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID candidateId = resultSet.getObject("id", UUID.class);
        List<RouteSegment> segments = jdbcTemplate.query(
                """
            SELECT id, sequence, from_label, to_label,
                   ST_AsText(geometry) AS geometry_wkt,
                   distance_metres, duration_seconds, toll_estimate_zar
              FROM routing_segment
             WHERE candidate_id = ?
             ORDER BY sequence
            """,
                (segmentSet, segmentNumber) -> new RouteSegment(
                        segmentSet.getObject("id", UUID.class),
                        segmentSet.getInt("sequence"),
                        segmentSet.getString("from_label"),
                        segmentSet.getString("to_label"),
                        parseLineString(segmentSet.getString("geometry_wkt")),
                        segmentSet.getLong("distance_metres"),
                        segmentSet.getLong("duration_seconds"),
                        segmentSet.getBigDecimal("toll_estimate_zar")),
                candidateId);
        return new CandidateRoute(
                candidateId,
                resultSet.getInt("sequence"),
                resultSet.getString("provider_candidate_key"),
                resultSet.getString("label"),
                parseLineString(resultSet.getString("geometry_wkt")),
                resultSet.getLong("distance_metres"),
                resultSet.getLong("duration_seconds"),
                resultSet.getBigDecimal("toll_estimate_zar"),
                segments);
    }

    private static String lineString(List<GeoPoint> geometry) {
        String coordinates = geometry.stream()
                .map(point -> decimal(point.longitude()) + " " + decimal(point.latitude()))
                .collect(java.util.stream.Collectors.joining(","));
        return "LINESTRING(" + coordinates + ")";
    }

    private static List<GeoPoint> parseLineString(String value) {
        int opening = value.indexOf('(');
        int closing = value.lastIndexOf(')');
        if (opening < 0 || closing <= opening) {
            throw new IllegalStateException("Stored route geometry is invalid");
        }
        List<GeoPoint> points = new ArrayList<>();
        Arrays.stream(value.substring(opening + 1, closing).split(","))
                .map(String::strip)
                .forEach(coordinate -> {
                    String[] values = coordinate.split("\\s+");
                    if (values.length != 2) {
                        throw new IllegalStateException("Stored route coordinate is invalid");
                    }
                    points.add(new GeoPoint(null, Double.parseDouble(values[1]), Double.parseDouble(values[0])));
                });
        return List.copyOf(points);
    }

    private static String decimal(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static OffsetDateTime time(Instant value) {
        return OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getObject(column, OffsetDateTime.class).toInstant();
    }
}
