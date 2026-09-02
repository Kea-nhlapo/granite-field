package za.co.trademesh.modules.handover.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.handover.domain.DeliveryDisputeResolution;
import za.co.trademesh.modules.handover.domain.HandoverAttempt;
import za.co.trademesh.modules.handover.domain.HandoverChallenge;
import za.co.trademesh.modules.handover.domain.HandoverConfirmation;
import za.co.trademesh.modules.handover.domain.HandoverLocation;
import za.co.trademesh.modules.handover.domain.HandoverParty;
import za.co.trademesh.modules.handover.domain.HandoverRepository;
import za.co.trademesh.modules.handover.domain.HandoverState;
import za.co.trademesh.modules.handover.domain.HandoverType;
import za.co.trademesh.modules.handover.domain.QuantityOutcome;

@Repository
class JdbcHandoverRepository implements HandoverRepository {

    private static final String CHALLENGE_COLUMNS = """
        id, shipment_id, business_id, handover_type, delivery_order_id, state,
        nonce_hash, initiator_user_id, counterparty_user_id, expected_location_label,
        ST_Y(expected_location::geometry) AS expected_latitude,
        ST_X(expected_location::geometry) AS expected_longitude,
        expected_quantity, expected_unit_of_measure, location_tolerance_metres,
        expires_at, completed_at, correlation_id, created_at
        """;
    private static final String CONFIRMATION_COLUMNS = """
        id, challenge_id, command_id, input_fingerprint, actor_user_id, party,
        observed_at, received_at, ST_Y(location::geometry) AS latitude,
        ST_X(location::geometry) AS longitude, distance_metres, captured_quantity,
        photo_url, quantity_outcome, quantity_note
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcHandoverRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int expireActive(UUID shipmentId, HandoverType type, UUID deliveryOrderId, Instant now) {
        return jdbcTemplate.update("""
            UPDATE handover_challenge
               SET state = 'EXPIRED', completed_at = ?
             WHERE shipment_id = ? AND handover_type = ?
               AND delivery_order_id IS NOT DISTINCT FROM ?
               AND state = 'PENDING' AND expires_at < ?
            """, time(now), shipmentId, type.name(), deliveryOrderId, time(now));
    }

    @Override
    public Optional<HandoverChallenge> findActive(UUID shipmentId, HandoverType type, UUID deliveryOrderId) {
        return find(
                "SELECT " + CHALLENGE_COLUMNS
                        + " FROM handover_challenge WHERE shipment_id = ? AND handover_type = ?"
                        + " AND delivery_order_id IS NOT DISTINCT FROM ? AND state = 'PENDING'",
                shipmentId,
                type.name(),
                deliveryOrderId);
    }

    @Override
    public boolean save(HandoverChallenge challenge) {
        return jdbcTemplate.update(
                        """
            INSERT INTO handover_challenge (
                id, shipment_id, business_id, handover_type, delivery_order_id, state,
                nonce_hash, initiator_user_id, counterparty_user_id, expected_location_label,
                expected_location, expected_quantity, expected_unit_of_measure,
                location_tolerance_metres, expires_at, completed_at, correlation_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        challenge.id(),
                        challenge.shipmentId(),
                        challenge.businessId(),
                        challenge.type().name(),
                        challenge.deliveryOrderId(),
                        challenge.state().name(),
                        challenge.nonceHash(),
                        challenge.initiatorUserId(),
                        challenge.counterpartyUserId(),
                        challenge.expectedLocation().label(),
                        challenge.expectedLocation().longitude(),
                        challenge.expectedLocation().latitude(),
                        challenge.expectedQuantity(),
                        challenge.unitOfMeasure(),
                        challenge.locationToleranceMetres(),
                        time(challenge.expiresAt()),
                        nullableTime(challenge.completedAt()),
                        challenge.correlationId(),
                        time(challenge.createdAt()))
                == 1;
    }

    @Override
    public Optional<HandoverChallenge> findOwned(UUID businessId, UUID shipmentId, UUID challengeId) {
        return find(
                "SELECT " + CHALLENGE_COLUMNS
                        + " FROM handover_challenge WHERE business_id = ? AND shipment_id = ? AND id = ?",
                businessId,
                shipmentId,
                challengeId);
    }

    @Override
    public List<HandoverChallenge> findByShipment(UUID shipmentId) {
        return jdbcTemplate
                .query(
                        "SELECT " + CHALLENGE_COLUMNS
                                + " FROM handover_challenge WHERE shipment_id = ? ORDER BY created_at, id",
                        this::mapChallengeBase,
                        shipmentId)
                .stream()
                .map(this::withConfirmations)
                .toList();
    }

    @Override
    public Optional<HandoverChallenge> findByNonceHashForUpdate(String nonceHash) {
        return find(
                "SELECT " + CHALLENGE_COLUMNS + " FROM handover_challenge WHERE nonce_hash = ? FOR UPDATE", nonceHash);
    }

    @Override
    public Optional<HandoverConfirmation> findConfirmationByCommandId(UUID commandId) {
        return jdbcTemplate
                .query(
                        "SELECT " + CONFIRMATION_COLUMNS + " FROM handover_confirmation WHERE command_id = ?",
                        this::mapConfirmation,
                        commandId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean saveConfirmation(HandoverConfirmation confirmation) {
        return jdbcTemplate.update(
                        """
            INSERT INTO handover_confirmation (
                id, challenge_id, command_id, input_fingerprint, actor_user_id, party,
                observed_at, received_at, location, distance_metres, captured_quantity,
                photo_url, quantity_outcome, quantity_note
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        confirmation.id(),
                        confirmation.challengeId(),
                        confirmation.commandId(),
                        confirmation.inputFingerprint(),
                        confirmation.actorUserId(),
                        confirmation.party().name(),
                        time(confirmation.observedAt()),
                        time(confirmation.receivedAt()),
                        confirmation.longitude(),
                        confirmation.latitude(),
                        confirmation.distanceMetres(),
                        confirmation.capturedQuantity(),
                        confirmation.photoUrl(),
                        confirmation.quantityOutcome().name(),
                        confirmation.quantityNote())
                == 1;
    }

    @Override
    public boolean changeState(UUID challengeId, HandoverState expected, HandoverState target, Instant completedAt) {
        return jdbcTemplate.update(
                        "UPDATE handover_challenge SET state = ?, completed_at = ? WHERE id = ? AND state = ?",
                        target.name(),
                        time(completedAt),
                        challengeId,
                        expected.name())
                == 1;
    }

    @Override
    public Set<UUID> findFinalizedDeliveryOrderIds(UUID shipmentId) {
        return Set.copyOf(jdbcTemplate.queryForList("""
            SELECT delivery_order_id
              FROM handover_challenge
             WHERE shipment_id = ? AND handover_type = 'DELIVERY'
               AND state IN ('COMPLETED', 'DISPUTED')
            """, UUID.class, shipmentId));
    }

    @Override
    public Optional<DeliveryDisputeResolution> findResolution(UUID businessId, UUID shipmentId) {
        return jdbcTemplate.query("""
                    SELECT id, shipment_id, business_id, command_id, input_fingerprint,
                           resolved_amount, resolved_by_user_id, resolved_at
                      FROM handover_delivery_resolution
                     WHERE business_id = ? AND shipment_id = ?
                    """, this::mapResolution, businessId, shipmentId).stream()
                .findFirst();
    }

    @Override
    public Optional<DeliveryDisputeResolution> findResolutionByCommandId(UUID commandId) {
        return jdbcTemplate.query("""
                    SELECT id, shipment_id, business_id, command_id, input_fingerprint,
                           resolved_amount, resolved_by_user_id, resolved_at
                      FROM handover_delivery_resolution
                     WHERE command_id = ?
                    """, this::mapResolution, commandId).stream().findFirst();
    }

    @Override
    public boolean saveResolution(DeliveryDisputeResolution resolution) {
        return jdbcTemplate.update(
                        """
                    INSERT INTO handover_delivery_resolution (
                        id, shipment_id, business_id, command_id, input_fingerprint,
                        resolved_amount, resolved_by_user_id, resolved_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """,
                        resolution.id(),
                        resolution.shipmentId(),
                        resolution.businessId(),
                        resolution.commandId(),
                        resolution.inputFingerprint(),
                        resolution.resolvedAmount(),
                        resolution.resolvedByUserId(),
                        time(resolution.resolvedAt()))
                == 1;
    }

    @Override
    public void saveAttempt(HandoverAttempt attempt) {
        jdbcTemplate.update(
                """
            INSERT INTO handover_attempt (
                id, challenge_id, actor_user_id, outcome, attempted_at,
                observed_at, latitude, longitude, detail
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                attempt.id(),
                attempt.challengeId(),
                attempt.actorUserId(),
                attempt.outcome().name(),
                time(attempt.attemptedAt()),
                nullableTime(attempt.observedAt()),
                attempt.latitude(),
                attempt.longitude(),
                attempt.detail());
    }

    private Optional<HandoverChallenge> find(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapChallengeBase, parameters).stream()
                .findFirst()
                .map(this::withConfirmations);
    }

    private HandoverChallenge withConfirmations(HandoverChallenge challenge) {
        List<HandoverConfirmation> confirmations = jdbcTemplate.query(
                "SELECT " + CONFIRMATION_COLUMNS
                        + " FROM handover_confirmation WHERE challenge_id = ? ORDER BY received_at, id",
                this::mapConfirmation,
                challenge.id());
        return new HandoverChallenge(
                challenge.id(),
                challenge.shipmentId(),
                challenge.businessId(),
                challenge.type(),
                challenge.deliveryOrderId(),
                challenge.state(),
                challenge.nonceHash(),
                challenge.initiatorUserId(),
                challenge.counterpartyUserId(),
                challenge.expectedLocation(),
                challenge.expectedQuantity(),
                challenge.unitOfMeasure(),
                challenge.locationToleranceMetres(),
                challenge.expiresAt(),
                challenge.completedAt(),
                challenge.correlationId(),
                challenge.createdAt(),
                confirmations);
    }

    private HandoverChallenge mapChallengeBase(ResultSet resultSet, int rowNumber) throws SQLException {
        return new HandoverChallenge(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                HandoverType.valueOf(resultSet.getString("handover_type")),
                resultSet.getObject("delivery_order_id", UUID.class),
                HandoverState.valueOf(resultSet.getString("state")),
                resultSet.getString("nonce_hash").strip(),
                resultSet.getObject("initiator_user_id", UUID.class),
                resultSet.getObject("counterparty_user_id", UUID.class),
                new HandoverLocation(
                        resultSet.getString("expected_location_label"),
                        resultSet.getDouble("expected_latitude"),
                        resultSet.getDouble("expected_longitude")),
                resultSet.getBigDecimal("expected_quantity"),
                resultSet.getString("expected_unit_of_measure"),
                resultSet.getInt("location_tolerance_metres"),
                instant(resultSet, "expires_at"),
                nullableInstant(resultSet, "completed_at"),
                resultSet.getObject("correlation_id", UUID.class),
                instant(resultSet, "created_at"),
                List.of());
    }

    private HandoverConfirmation mapConfirmation(ResultSet resultSet, int rowNumber) throws SQLException {
        return new HandoverConfirmation(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("challenge_id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                resultSet.getObject("actor_user_id", UUID.class),
                HandoverParty.valueOf(resultSet.getString("party")),
                instant(resultSet, "observed_at"),
                instant(resultSet, "received_at"),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude"),
                resultSet.getDouble("distance_metres"),
                resultSet.getBigDecimal("captured_quantity"),
                resultSet.getString("photo_url"),
                QuantityOutcome.valueOf(resultSet.getString("quantity_outcome")),
                resultSet.getString("quantity_note"));
    }

    private DeliveryDisputeResolution mapResolution(ResultSet resultSet, int rowNumber) throws SQLException {
        return new DeliveryDisputeResolution(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getObject("command_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                resultSet.getBigDecimal("resolved_amount"),
                resultSet.getObject("resolved_by_user_id", UUID.class),
                instant(resultSet, "resolved_at"));
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
