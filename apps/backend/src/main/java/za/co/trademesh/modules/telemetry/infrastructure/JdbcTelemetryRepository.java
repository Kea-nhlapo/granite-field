package za.co.trademesh.modules.telemetry.infrastructure;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import za.co.trademesh.modules.telemetry.domain.TelemetryDevice;
import za.co.trademesh.modules.telemetry.domain.TelemetryDeviceStatus;
import za.co.trademesh.modules.telemetry.domain.TelemetryLivePosition;
import za.co.trademesh.modules.telemetry.domain.TelemetryNetworkStatus;
import za.co.trademesh.modules.telemetry.domain.TelemetryReading;
import za.co.trademesh.modules.telemetry.domain.TelemetryRepository;
import za.co.trademesh.modules.telemetry.domain.TelemetryRetentionTier;

@Repository
class JdbcTelemetryRepository implements TelemetryRepository {

    private static final String DEVICE_COLUMNS = """
        id, business_id, shipment_id, display_name, credential_hash, status,
        created_by_user_id, created_at, last_seen_at, revoked_at
        """;
    private static final String READING_COLUMNS = """
        id, device_id, shipment_id, client_event_id, input_fingerprint,
        recorded_at, received_at,
        CASE WHEN position IS NULL THEN NULL ELSE ST_Y(position) END AS latitude,
        CASE WHEN position IS NULL THEN NULL ELSE ST_X(position) END AS longitude,
        speed_kph, fuel_litres, temperature_celsius, seal_open,
        battery_percent, network_status, network_signal_dbm, retention_tier
        """;

    private final JdbcTemplate jdbcTemplate;

    JdbcTelemetryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean saveDevice(TelemetryDevice device) {
        return jdbcTemplate.update(
                        """
            INSERT INTO telemetry_device (
                id, business_id, shipment_id, display_name, credential_hash,
                status, created_by_user_id, created_at, last_seen_at, revoked_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """,
                        device.id(),
                        device.businessId(),
                        device.shipmentId(),
                        device.displayName(),
                        device.credentialHash(),
                        device.status().name(),
                        device.createdByUserId(),
                        time(device.createdAt()),
                        nullableTime(device.lastSeenAt()),
                        nullableTime(device.revokedAt()))
                == 1;
    }

    @Override
    public Optional<TelemetryDevice> findDevice(UUID businessId, UUID deviceId) {
        return findDevice(
                "SELECT " + DEVICE_COLUMNS + " FROM telemetry_device WHERE business_id = ? AND id = ?",
                businessId,
                deviceId);
    }

    @Override
    public Optional<TelemetryDevice> findDeviceForUpdate(UUID deviceId) {
        return findDevice("SELECT " + DEVICE_COLUMNS + " FROM telemetry_device WHERE id = ? FOR UPDATE", deviceId);
    }

    @Override
    public boolean revokeDevice(UUID businessId, UUID deviceId, Instant revokedAt) {
        return jdbcTemplate.update("""
            UPDATE telemetry_device
               SET status = 'REVOKED', revoked_at = ?
             WHERE business_id = ? AND id = ? AND status = 'ACTIVE'
            """, time(revokedAt), businessId, deviceId) == 1;
    }

    @Override
    public void markDeviceSeen(UUID deviceId, Instant seenAt) {
        jdbcTemplate.update(
                "UPDATE telemetry_device SET last_seen_at = ? WHERE id = ? AND status = 'ACTIVE'",
                time(seenAt),
                deviceId);
    }

    @Override
    public Map<UUID, StoredReadingIdentity> findStoredReadings(UUID deviceId, Set<UUID> clientEventIds) {
        Map<UUID, StoredReadingIdentity> stored = new HashMap<>();
        clientEventIds.forEach(clientEventId -> jdbcTemplate
                .query(
                        "SELECT id, input_fingerprint FROM telemetry_reading"
                                + " WHERE device_id = ? AND client_event_id = ?",
                        (resultSet, rowNumber) -> new StoredReadingIdentity(
                                resultSet.getObject("id", UUID.class),
                                resultSet.getString("input_fingerprint").strip()),
                        deviceId,
                        clientEventId)
                .stream()
                .findFirst()
                .ifPresent(identity -> stored.put(clientEventId, identity)));
        return Map.copyOf(stored);
    }

    @Override
    public long countReceivedSince(UUID deviceId, Instant since) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM telemetry_reading WHERE device_id = ? AND received_at >= ?",
                Long.class,
                deviceId,
                time(since));
        return count == null ? 0 : count;
    }

    @Override
    public void saveReading(TelemetryReading reading) {
        jdbcTemplate.update(
                """
            INSERT INTO telemetry_reading (
                id, device_id, shipment_id, client_event_id, input_fingerprint,
                recorded_at, received_at, position, speed_kph, fuel_litres,
                temperature_celsius, seal_open, battery_percent,
                network_status, network_signal_dbm, retention_tier
            ) VALUES (
                ?, ?, ?, ?, ?, ?, ?,
                ST_SetSRID(ST_MakePoint(?, ?), 4326),
                ?, ?, ?, ?, ?, ?, ?, ?
            )
            """,
                reading.id(),
                reading.deviceId(),
                reading.shipmentId(),
                reading.clientEventId(),
                reading.inputFingerprint(),
                time(reading.recordedAt()),
                time(reading.receivedAt()),
                reading.longitude(),
                reading.latitude(),
                reading.speedKilometresPerHour(),
                reading.fuelLitres(),
                reading.temperatureCelsius(),
                reading.sealOpen(),
                reading.batteryPercent(),
                nullableName(reading.networkStatus()),
                reading.networkSignalDbm(),
                reading.retentionTier().name());
    }

    @Override
    public void updateLivePosition(TelemetryReading reading) {
        if (!reading.hasPosition()) {
            return;
        }
        jdbcTemplate.update(
                """
            INSERT INTO telemetry_live_position (
                shipment_id, device_id, reading_id, recorded_at, received_at,
                position, speed_kph, battery_percent, network_status, network_signal_dbm
            ) VALUES (?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326), ?, ?, ?, ?)
            ON CONFLICT (shipment_id) DO UPDATE SET
                device_id = EXCLUDED.device_id,
                reading_id = EXCLUDED.reading_id,
                recorded_at = EXCLUDED.recorded_at,
                received_at = EXCLUDED.received_at,
                position = EXCLUDED.position,
                speed_kph = EXCLUDED.speed_kph,
                battery_percent = EXCLUDED.battery_percent,
                network_status = EXCLUDED.network_status,
                network_signal_dbm = EXCLUDED.network_signal_dbm
            WHERE (EXCLUDED.recorded_at, EXCLUDED.received_at, EXCLUDED.reading_id)
                > (telemetry_live_position.recorded_at,
                   telemetry_live_position.received_at,
                   telemetry_live_position.reading_id)
            """,
                reading.shipmentId(),
                reading.deviceId(),
                reading.id(),
                time(reading.recordedAt()),
                time(reading.receivedAt()),
                reading.longitude(),
                reading.latitude(),
                reading.speedKilometresPerHour(),
                reading.batteryPercent(),
                nullableName(reading.networkStatus()),
                reading.networkSignalDbm());
    }

    @Override
    public Optional<TelemetryLivePosition> findLivePosition(UUID shipmentId) {
        return jdbcTemplate.query("""
            SELECT shipment_id, device_id, reading_id, recorded_at, received_at,
                   ST_Y(position) AS latitude, ST_X(position) AS longitude,
                   speed_kph, battery_percent, network_status, network_signal_dbm
              FROM telemetry_live_position
             WHERE shipment_id = ?
            """, this::mapLivePosition, shipmentId).stream()
                .findFirst();
    }

    @Override
    public List<TelemetryReading> findRecentReadings(UUID shipmentId, int limit) {
        return jdbcTemplate.query(
                "SELECT " + READING_COLUMNS
                        + " FROM telemetry_reading WHERE shipment_id = ?"
                        + " ORDER BY recorded_at DESC, received_at DESC, id DESC LIMIT ?",
                this::mapReading,
                shipmentId,
                limit);
    }

    @Override
    public int downsample(Instant recordedBefore, Instant retainedAfter, Duration bucket, int limit) {
        return jdbcTemplate.update("""
            WITH ranked AS (
                SELECT id,
                       ROW_NUMBER() OVER (
                           PARTITION BY shipment_id, device_id,
                               FLOOR(EXTRACT(EPOCH FROM recorded_at) / ?)
                           ORDER BY recorded_at DESC, received_at DESC, id DESC
                       ) AS sample_rank
                  FROM telemetry_reading
                 WHERE recorded_at < ? AND recorded_at >= ?
            ), due AS (
                SELECT id FROM ranked WHERE sample_rank > 1 LIMIT ?
            )
            DELETE FROM telemetry_reading reading
             USING due
             WHERE reading.id = due.id
            """, bucket.toSeconds(), time(recordedBefore), time(retainedAfter), limit);
    }

    @Override
    public int markDownsampled(Instant recordedBefore, Instant retainedAfter, Duration bucket, int limit) {
        return jdbcTemplate.update("""
            WITH bucketed AS (
                SELECT id, retention_tier,
                       COUNT(*) OVER (
                           PARTITION BY shipment_id, device_id,
                               FLOOR(EXTRACT(EPOCH FROM recorded_at) / ?)
                       ) AS bucket_count
                  FROM telemetry_reading
                 WHERE recorded_at < ? AND recorded_at >= ?
            ), due AS (
                SELECT id
                  FROM bucketed
                 WHERE retention_tier = 'RAW' AND bucket_count = 1
                 LIMIT ?
            )
            UPDATE telemetry_reading reading
               SET retention_tier = 'DOWNSAMPLED'
              FROM due
             WHERE reading.id = due.id
            """, bucket.toSeconds(), time(recordedBefore), time(retainedAfter), limit);
    }

    @Override
    public int deleteExpired(Instant recordedBefore, int limit) {
        return jdbcTemplate.update("""
            WITH due AS (
                SELECT id FROM telemetry_reading
                 WHERE recorded_at < ?
                 ORDER BY recorded_at
                 LIMIT ?
            )
            DELETE FROM telemetry_reading reading
             USING due
             WHERE reading.id = due.id
            """, time(recordedBefore), limit);
    }

    private Optional<TelemetryDevice> findDevice(String sql, Object... parameters) {
        return jdbcTemplate.query(sql, this::mapDevice, parameters).stream().findFirst();
    }

    private TelemetryDevice mapDevice(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TelemetryDevice(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("business_id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getString("display_name"),
                resultSet.getString("credential_hash").strip(),
                TelemetryDeviceStatus.valueOf(resultSet.getString("status")),
                resultSet.getObject("created_by_user_id", UUID.class),
                instant(resultSet, "created_at"),
                nullableInstant(resultSet, "last_seen_at"),
                nullableInstant(resultSet, "revoked_at"));
    }

    private TelemetryReading mapReading(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TelemetryReading(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("device_id", UUID.class),
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("client_event_id", UUID.class),
                resultSet.getString("input_fingerprint").strip(),
                instant(resultSet, "recorded_at"),
                instant(resultSet, "received_at"),
                resultSet.getObject("latitude", Double.class),
                resultSet.getObject("longitude", Double.class),
                resultSet.getBigDecimal("speed_kph"),
                resultSet.getBigDecimal("fuel_litres"),
                resultSet.getBigDecimal("temperature_celsius"),
                resultSet.getObject("seal_open", Boolean.class),
                resultSet.getBigDecimal("battery_percent"),
                nullableNetworkStatus(resultSet.getString("network_status")),
                resultSet.getObject("network_signal_dbm", Integer.class),
                TelemetryRetentionTier.valueOf(resultSet.getString("retention_tier")));
    }

    private TelemetryLivePosition mapLivePosition(ResultSet resultSet, int rowNumber) throws SQLException {
        return new TelemetryLivePosition(
                resultSet.getObject("shipment_id", UUID.class),
                resultSet.getObject("device_id", UUID.class),
                resultSet.getObject("reading_id", UUID.class),
                instant(resultSet, "recorded_at"),
                instant(resultSet, "received_at"),
                resultSet.getDouble("latitude"),
                resultSet.getDouble("longitude"),
                resultSet.getBigDecimal("speed_kph"),
                resultSet.getBigDecimal("battery_percent"),
                nullableNetworkStatus(resultSet.getString("network_status")),
                resultSet.getObject("network_signal_dbm", Integer.class));
    }

    private static TelemetryNetworkStatus nullableNetworkStatus(String value) {
        return value == null ? null : TelemetryNetworkStatus.valueOf(value);
    }

    private static String nullableName(Enum<?> value) {
        return value == null ? null : value.name();
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
