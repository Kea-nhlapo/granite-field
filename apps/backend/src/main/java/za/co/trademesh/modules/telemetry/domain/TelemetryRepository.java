package za.co.trademesh.modules.telemetry.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TelemetryRepository {

    boolean saveDevice(TelemetryDevice device);

    Optional<TelemetryDevice> findDevice(UUID businessId, UUID deviceId);

    Optional<TelemetryDevice> findDeviceForUpdate(UUID deviceId);

    boolean revokeDevice(UUID businessId, UUID deviceId, Instant revokedAt);

    void markDeviceSeen(UUID deviceId, Instant seenAt);

    Map<UUID, StoredReadingIdentity> findStoredReadings(UUID deviceId, Set<UUID> clientEventIds);

    long countReceivedSince(UUID deviceId, Instant since);

    void saveReading(TelemetryReading reading);

    void updateLivePosition(TelemetryReading reading);

    Optional<TelemetryLivePosition> findLivePosition(UUID shipmentId);

    List<TelemetryReading> findRecentReadings(UUID shipmentId, int limit);

    int downsample(Instant recordedBefore, Instant retainedAfter, Duration bucket, int limit);

    int markDownsampled(Instant recordedBefore, Instant retainedAfter, Duration bucket, int limit);

    int deleteExpired(Instant recordedBefore, int limit);

    record StoredReadingIdentity(UUID readingId, String inputFingerprint) {}
}
