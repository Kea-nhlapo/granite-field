package za.co.trademesh.modules.transport.infrastructure;

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
import za.co.trademesh.modules.transport.domain.Capacity;
import za.co.trademesh.modules.transport.domain.CapacityConstraintOutcome;
import za.co.trademesh.modules.transport.domain.CapacityConstraintResult;
import za.co.trademesh.modules.transport.domain.CapacityMatchCandidate;
import za.co.trademesh.modules.transport.domain.CapacityMatchConstraint;
import za.co.trademesh.modules.transport.domain.CapacityMatchSearch;
import za.co.trademesh.modules.transport.domain.CapacityMatchStatus;
import za.co.trademesh.modules.transport.domain.CapacityMatchingRepository;
import za.co.trademesh.modules.transport.domain.CapacityReservation;
import za.co.trademesh.modules.transport.domain.CapacityReservationStatus;
import za.co.trademesh.modules.transport.domain.CapacityScoreComponent;
import za.co.trademesh.modules.transport.domain.CargoTrait;

@Repository
class JdbcCapacityMatchingRepository implements CapacityMatchingRepository {

    private static final String SEARCH_COLUMNS = """
        id, requested_by_business_id, client_request_id, demand_group_suggestion_id,
        input_fingerprint, algorithm_version, required_weight_kg,
        required_volume_cubic_metres, delivery_window_start, delivery_window_end,
        order_count, status, created_by_user_id, created_at
        """;
    private static final String CANDIDATE_COLUMNS = """
        offer_id, transporter_id, compatible, candidate_rank,
        available_weight_kg, available_volume_cubic_metres,
        added_distance_metres, timing_overlap_seconds,
        estimated_cost_zar, score
        """;
    private static final String RESERVATION_COLUMNS = """
        id, match_search_id, client_request_id, offer_id,
        reserved_weight_kg, reserved_volume_cubic_metres,
        status, expires_at, created_by_user_id, created_at, released_at
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcCapacityMatchingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean saveSearch(CapacityMatchSearch search) {
        int written = jdbcTemplate.update(
                """
            INSERT INTO transport_capacity_match_search (
                id, requested_by_business_id, client_request_id, demand_group_suggestion_id,
                input_fingerprint, algorithm_version, required_weight_kg,
                required_volume_cubic_metres, delivery_window_start, delivery_window_end,
                order_count, status, created_by_user_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                search.id(),
                search.requestedByBusinessId(),
                search.clientRequestId(),
                search.demandGroupSuggestionId(),
                search.inputFingerprint(),
                search.algorithmVersion(),
                search.requiredCapacity().weightKg(),
                search.requiredCapacity().volumeCubicMetres(),
                time(search.deliveryWindowStart()),
                time(search.deliveryWindowEnd()),
                search.orderCount(),
                search.status().name(),
                search.createdByUserId(),
                time(search.createdAt()));
        if (written != 1) {
            return false;
        }
        search.cargoTraits()
                .forEach(trait -> jdbcTemplate.update(
                        "INSERT INTO transport_capacity_match_cargo_trait (match_search_id, cargo_trait) VALUES (?, ?)",
                        search.id(),
                        trait.name()));
        search.candidates().forEach(candidate -> saveCandidate(search.id(), candidate));
        return true;
    }

    @Override
    public Optional<CapacityMatchSearch> findSearch(UUID businessId, UUID searchId) {
        return search(
                "SELECT " + SEARCH_COLUMNS
                        + " FROM transport_capacity_match_search WHERE requested_by_business_id = ? AND id = ?",
                businessId,
                searchId);
    }

    @Override
    public Optional<CapacityMatchSearch> findSearchForUpdate(UUID businessId, UUID searchId) {
        return search(
                "SELECT " + SEARCH_COLUMNS
                        + " FROM transport_capacity_match_search"
                        + " WHERE requested_by_business_id = ? AND id = ? FOR UPDATE",
                businessId,
                searchId);
    }

    @Override
    public Optional<CapacityMatchSearch> findSearchForUpdateById(UUID searchId) {
        return search(
                "SELECT " + SEARCH_COLUMNS + " FROM transport_capacity_match_search WHERE id = ? FOR UPDATE", searchId);
    }

    @Override
    public Optional<CapacityMatchSearch> findSearchByRequestId(UUID businessId, UUID requestId) {
        return search(
                "SELECT " + SEARCH_COLUMNS
                        + " FROM transport_capacity_match_search"
                        + " WHERE requested_by_business_id = ? AND client_request_id = ?",
                businessId,
                requestId);
    }

    @Override
    public Optional<CapacityMatchCandidate> findCandidate(UUID searchId, UUID offerId) {
        return jdbcTemplate
                .query(
                        "SELECT " + CANDIDATE_COLUMNS
                                + " FROM transport_capacity_match_candidate"
                                + " WHERE match_search_id = ? AND offer_id = ?",
                        (resultSet, rowNumber) -> mapCandidate(searchId, resultSet),
                        searchId,
                        offerId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean saveReservation(CapacityReservation reservation) {
        return jdbcTemplate.update(
                        """
            INSERT INTO transport_capacity_reservation (
                id, match_search_id, client_request_id, offer_id,
                reserved_weight_kg, reserved_volume_cubic_metres,
                status, expires_at, created_by_user_id, created_at, released_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        reservation.id(),
                        reservation.matchSearchId(),
                        reservation.clientRequestId(),
                        reservation.offerId(),
                        reservation.reservedCapacity().weightKg(),
                        reservation.reservedCapacity().volumeCubicMetres(),
                        reservation.status().name(),
                        time(reservation.expiresAt()),
                        reservation.createdByUserId(),
                        time(reservation.createdAt()),
                        nullableTime(reservation.releasedAt()))
                == 1;
    }

    @Override
    public Optional<CapacityReservation> findReservation(UUID searchId) {
        return reservation(
                "SELECT " + RESERVATION_COLUMNS + " FROM transport_capacity_reservation WHERE match_search_id = ?",
                searchId);
    }

    @Override
    public Optional<CapacityReservation> findReservationForUpdate(UUID reservationId) {
        return reservation(
                "SELECT " + RESERVATION_COLUMNS + " FROM transport_capacity_reservation WHERE id = ? FOR UPDATE",
                reservationId);
    }

    @Override
    public boolean markSearchStatus(UUID searchId, CapacityMatchStatus expected, CapacityMatchStatus updated) {
        return jdbcTemplate.update(
                        "UPDATE transport_capacity_match_search SET status = ? WHERE id = ? AND status = ?",
                        updated.name(),
                        searchId,
                        expected.name())
                == 1;
    }

    @Override
    public boolean markReservationTerminal(UUID reservationId, CapacityReservationStatus status, Instant releasedAt) {
        return jdbcTemplate.update("""
            UPDATE transport_capacity_reservation
               SET status = ?, released_at = ?
             WHERE id = ? AND status = 'ACTIVE'
            """, status.name(), time(releasedAt), reservationId) == 1;
    }

    @Override
    public boolean markReservationConsumed(UUID reservationId, Instant now) {
        return jdbcTemplate.update(
                        "UPDATE transport_capacity_reservation SET status = 'CONSUMED'"
                                + " WHERE id = ? AND status = 'ACTIVE' AND expires_at > ?",
                        reservationId,
                        time(now))
                == 1;
    }

    @Override
    public List<UUID> findExpiredActiveReservationIds(Instant now, int limit) {
        return jdbcTemplate.query(
                """
            SELECT id
              FROM transport_capacity_reservation
             WHERE status = 'ACTIVE' AND expires_at <= ?
             ORDER BY expires_at, id
             LIMIT ?
            """, (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class), time(now), limit);
    }

    private void saveCandidate(UUID searchId, CapacityMatchCandidate candidate) {
        jdbcTemplate.update(
                """
            INSERT INTO transport_capacity_match_candidate (
                match_search_id, offer_id, transporter_id, compatible, candidate_rank,
                available_weight_kg, available_volume_cubic_metres,
                added_distance_metres, timing_overlap_seconds, estimated_cost_zar, score
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                searchId,
                candidate.offerId(),
                candidate.transporterId(),
                candidate.compatible(),
                candidate.rank(),
                candidate.availableCapacity().weightKg(),
                candidate.availableCapacity().volumeCubicMetres(),
                candidate.addedDistanceMetres(),
                candidate.timingOverlapSeconds(),
                candidate.estimatedCostZar(),
                candidate.score());
        candidate
                .constraintResults()
                .forEach(result -> jdbcTemplate.update(
                        """
            INSERT INTO transport_capacity_match_constraint_result (
                match_search_id, offer_id, constraint_code, outcome, explanation
            ) VALUES (?, ?, ?, ?, ?)
            """,
                        searchId,
                        candidate.offerId(),
                        result.constraint().name(),
                        result.outcome().name(),
                        result.explanation()));
        candidate
                .scoreComponents()
                .forEach(component -> jdbcTemplate.update(
                        """
            INSERT INTO transport_capacity_match_score_component (
                match_search_id, offer_id, component_code, raw_value,
                normalized_value, component_weight, contribution, explanation
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
                        searchId,
                        candidate.offerId(),
                        component.code(),
                        component.rawValue(),
                        component.normalizedValue(),
                        component.weight(),
                        component.contribution(),
                        component.explanation()));
    }

    private Optional<CapacityMatchSearch> search(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapSearch, parameters).stream().findFirst();
    }

    private Optional<CapacityReservation> reservation(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapReservation, parameters).stream()
                .findFirst();
    }

    private CapacityMatchSearch mapSearch(ResultSet resultSet, int rowNumber) throws SQLException {
        UUID searchId = resultSet.getObject("id", UUID.class);
        List<CargoTrait> traits = jdbcTemplate.query(
                """
            SELECT cargo_trait
              FROM transport_capacity_match_cargo_trait
             WHERE match_search_id = ?
             ORDER BY cargo_trait
            """, (traitSet, traitNumber) -> CargoTrait.valueOf(traitSet.getString("cargo_trait")), searchId);
        List<CapacityMatchCandidate> candidates = jdbcTemplate.query(
                "SELECT " + CANDIDATE_COLUMNS
                        + " FROM transport_capacity_match_candidate"
                        + " WHERE match_search_id = ?"
                        + " ORDER BY compatible DESC, candidate_rank NULLS LAST, offer_id",
                (candidateSet, candidateNumber) -> mapCandidate(searchId, candidateSet),
                searchId);
        return new CapacityMatchSearch(
                searchId,
                resultSet.getObject("requested_by_business_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getObject("demand_group_suggestion_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                resultSet.getString("algorithm_version"),
                new Capacity(
                        resultSet.getBigDecimal("required_weight_kg"),
                        resultSet.getBigDecimal("required_volume_cubic_metres")),
                traits,
                instant(resultSet, "delivery_window_start"),
                instant(resultSet, "delivery_window_end"),
                resultSet.getInt("order_count"),
                CapacityMatchStatus.valueOf(resultSet.getString("status")),
                candidates,
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"));
    }

    private CapacityMatchCandidate mapCandidate(UUID searchId, ResultSet resultSet) throws SQLException {
        UUID offerId = resultSet.getObject("offer_id", UUID.class);
        List<CapacityConstraintResult> constraints = jdbcTemplate.query(
                """
            SELECT constraint_code, outcome, explanation
              FROM transport_capacity_match_constraint_result
             WHERE match_search_id = ? AND offer_id = ?
             ORDER BY constraint_code
            """,
                (constraintSet, constraintNumber) -> new CapacityConstraintResult(
                        CapacityMatchConstraint.valueOf(constraintSet.getString("constraint_code")),
                        CapacityConstraintOutcome.valueOf(constraintSet.getString("outcome")),
                        constraintSet.getString("explanation")),
                searchId,
                offerId);
        List<CapacityScoreComponent> components = jdbcTemplate.query(
                """
            SELECT component_code, raw_value, normalized_value,
                   component_weight, contribution, explanation
              FROM transport_capacity_match_score_component
             WHERE match_search_id = ? AND offer_id = ?
             ORDER BY component_code
            """,
                (componentSet, componentNumber) -> new CapacityScoreComponent(
                        componentSet.getString("component_code"),
                        componentSet.getDouble("raw_value"),
                        componentSet.getDouble("normalized_value"),
                        componentSet.getDouble("component_weight"),
                        componentSet.getDouble("contribution"),
                        componentSet.getString("explanation")),
                searchId,
                offerId);
        Integer rank = resultSet.getObject("candidate_rank", Integer.class);
        return new CapacityMatchCandidate(
                offerId,
                resultSet.getObject("transporter_id", UUID.class),
                resultSet.getBoolean("compatible"),
                rank,
                new Capacity(
                        resultSet.getBigDecimal("available_weight_kg"),
                        resultSet.getBigDecimal("available_volume_cubic_metres")),
                resultSet.getDouble("added_distance_metres"),
                resultSet.getLong("timing_overlap_seconds"),
                resultSet.getBigDecimal("estimated_cost_zar"),
                resultSet.getDouble("score"),
                constraints,
                components);
    }

    private CapacityReservation mapReservation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new CapacityReservation(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("match_search_id", UUID.class),
                resultSet.getObject("client_request_id", UUID.class),
                resultSet.getObject("offer_id", UUID.class),
                new Capacity(
                        resultSet.getBigDecimal("reserved_weight_kg"),
                        resultSet.getBigDecimal("reserved_volume_cubic_metres")),
                CapacityReservationStatus.valueOf(resultSet.getString("status")),
                instant(resultSet, "expires_at"),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "released_at"));
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
