package za.co.trademesh.modules.telemetry.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.shipment.application.ShipmentAccessCatalog;
import za.co.trademesh.modules.telemetry.domain.TelemetryDevice;
import za.co.trademesh.modules.telemetry.domain.TelemetryDeviceStatus;
import za.co.trademesh.modules.telemetry.domain.TelemetryLivePosition;
import za.co.trademesh.modules.telemetry.domain.TelemetryNetworkStatus;
import za.co.trademesh.modules.telemetry.domain.TelemetryReading;
import za.co.trademesh.modules.telemetry.domain.TelemetryRepository;
import za.co.trademesh.modules.telemetry.domain.TelemetryRetentionTier;
import za.co.trademesh.modules.telemetry.events.TelemetryEvent;
import za.co.trademesh.shared.events.DomainEvents;

@Service
public class TelemetryService {

    private static final int MAX_DEVICE_NAME_LENGTH = 120;
    private static final int MAX_HISTORY_LIMIT = 500;

    private final ShipmentAccessCatalog shipments;
    private final TelemetryRepository telemetry;
    private final TelemetryDeviceCredentials credentials;
    private final TelemetryProperties properties;
    private final DomainEvents events;
    private final Clock clock;

    public TelemetryService(
            ShipmentAccessCatalog shipments,
            TelemetryRepository telemetry,
            TelemetryDeviceCredentials credentials,
            TelemetryProperties properties,
            DomainEvents events,
            Clock clock) {
        this.shipments = shipments;
        this.telemetry = telemetry;
        this.credentials = credentials;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public IssuedDevice provision(UUID businessId, UUID shipmentId, String displayName, UUID actorUserId) {
        UUID owner = requiredId(businessId);
        UUID shipment = requiredId(shipmentId);
        UUID actor = requiredId(actorUserId);
        String name = requiredText(displayName);
        if (name.length() > MAX_DEVICE_NAME_LENGTH) {
            throw TelemetryException.invalidRequest();
        }
        var access = shipments.findOwned(owner, shipment).orElseThrow(TelemetryException::shipmentNotFound);
        if (!access.acceptsTelemetry()) {
            throw TelemetryException.shipmentNotAcceptingReadings();
        }
        UUID deviceId = UUID.randomUUID();
        var issued = credentials.issue(deviceId);
        Instant now = databaseTime(clock.instant());
        TelemetryDevice device = new TelemetryDevice(
                deviceId,
                owner,
                shipment,
                name,
                issued.credentialHash(),
                TelemetryDeviceStatus.ACTIVE,
                actor,
                now,
                null,
                null);
        if (!telemetry.saveDevice(device)) {
            throw TelemetryException.invalidRequest();
        }
        return new IssuedDevice(device, issued.rawCredential());
    }

    @Transactional
    public void revoke(UUID businessId, UUID deviceId) {
        UUID owner = requiredId(businessId);
        TelemetryDevice device =
                telemetry.findDevice(owner, requiredId(deviceId)).orElseThrow(TelemetryException::deviceNotFound);
        if (device.status() == TelemetryDeviceStatus.ACTIVE) {
            telemetry.revokeDevice(owner, device.id(), databaseTime(clock.instant()));
        }
    }

    @Transactional
    public IngestionResult ingest(String rawCredential, List<ReadingInput> inputs) {
        if (inputs == null || inputs.isEmpty() || inputs.size() > properties.maximumBatchSize()) {
            throw TelemetryException.invalidRequest();
        }
        var supplied = credentials.parse(rawCredential);
        TelemetryDevice device = telemetry
                .findDeviceForUpdate(supplied.deviceId())
                .filter(candidate -> candidate.status() == TelemetryDeviceStatus.ACTIVE)
                .filter(candidate -> credentials.matches(supplied.credentialHash(), candidate.credentialHash()))
                .orElseThrow(TelemetryException::deviceAuthenticationFailed);
        var access = shipments
                .findOwned(device.businessId(), device.shipmentId())
                .orElseThrow(TelemetryException::deviceAuthenticationFailed);
        if (!access.acceptsTelemetry()) {
            throw TelemetryException.shipmentNotAcceptingReadings();
        }

        Instant receivedAt = databaseTime(clock.instant());
        List<NormalizedReading> normalized =
                inputs.stream().map(this::normalize).toList();
        Map<UUID, NormalizedReading> unique = new LinkedHashMap<>();
        for (NormalizedReading reading : normalized) {
            NormalizedReading first = unique.putIfAbsent(reading.clientEventId(), reading);
            if (first != null && !first.fingerprint().equals(reading.fingerprint())) {
                throw TelemetryException.clientEventConflict();
            }
        }

        Map<UUID, TelemetryRepository.StoredReadingIdentity> stored =
                telemetry.findStoredReadings(device.id(), unique.keySet());
        stored.forEach((clientEventId, identity) -> {
            if (!identity.inputFingerprint().equals(unique.get(clientEventId).fingerprint())) {
                throw TelemetryException.clientEventConflict();
            }
        });

        List<NormalizedReading> pending = unique.values().stream()
                .filter(reading -> !stored.containsKey(reading.clientEventId()))
                .toList();
        pending.forEach(reading -> validate(reading, receivedAt));
        long recentCount = telemetry.countReceivedSince(device.id(), receivedAt.minus(properties.rateLimitWindow()));
        if (recentCount + pending.size() > properties.maximumReadingsPerWindow()) {
            throw TelemetryException.rateLimited();
        }

        Map<UUID, UUID> readingIds = new LinkedHashMap<>();
        stored.forEach((clientEventId, identity) -> readingIds.put(clientEventId, identity.readingId()));
        Set<UUID> acceptedClientEvents = new HashSet<>();
        for (NormalizedReading input : pending) {
            TelemetryReading reading = input.toReading(UUID.randomUUID(), device, receivedAt);
            telemetry.saveReading(reading);
            telemetry.updateLivePosition(reading);
            readingIds.put(reading.clientEventId(), reading.id());
            acceptedClientEvents.add(reading.clientEventId());
            events.publish(
                    new TelemetryEvent.ReadingAccepted(
                            reading.id(),
                            reading.shipmentId(),
                            reading.deviceId(),
                            reading.recordedAt(),
                            reading.latitude(),
                            reading.longitude(),
                            reading.speedKilometresPerHour(),
                            reading.fuelLitres(),
                            reading.temperatureCelsius(),
                            reading.sealOpen(),
                            reading.batteryPercent(),
                            reading.networkStatus(),
                            reading.networkSignalDbm()),
                    device.id().toString());
        }
        telemetry.markDeviceSeen(device.id(), receivedAt);

        Set<UUID> emitted = new HashSet<>();
        List<ReadingReceipt> receipts = new ArrayList<>(normalized.size());
        for (NormalizedReading reading : normalized) {
            boolean firstOccurrence = emitted.add(reading.clientEventId());
            ReceiptStatus status = firstOccurrence && acceptedClientEvents.contains(reading.clientEventId())
                    ? ReceiptStatus.ACCEPTED
                    : ReceiptStatus.DUPLICATE;
            receipts.add(new ReadingReceipt(reading.clientEventId(), readingIds.get(reading.clientEventId()), status));
        }
        return new IngestionResult(List.copyOf(receipts));
    }

    @Transactional(readOnly = true)
    public TelemetryLivePosition getLivePosition(UUID businessId, UUID shipmentId) {
        requireOwned(businessId, shipmentId);
        return telemetry.findLivePosition(shipmentId).orElseThrow(TelemetryException::livePositionNotFound);
    }

    @Transactional(readOnly = true)
    public List<TelemetryReading> getRecentReadings(UUID businessId, UUID shipmentId, int limit) {
        requireOwned(businessId, shipmentId);
        if (limit < 1 || limit > MAX_HISTORY_LIMIT) {
            throw TelemetryException.invalidRequest();
        }
        return telemetry.findRecentReadings(shipmentId, limit);
    }

    @Transactional
    public CleanupResult cleanUp() {
        Instant now = databaseTime(clock.instant());
        Instant downsampleBefore = now.minus(properties.downsampleAfter());
        Instant retainAfter = now.minus(properties.retention());
        int deletedSamples = telemetry.downsample(
                downsampleBefore, retainAfter, properties.downsampleBucket(), properties.cleanupBatchSize());
        int markedSamples = telemetry.markDownsampled(
                downsampleBefore, retainAfter, properties.downsampleBucket(), properties.cleanupBatchSize());
        int deletedExpired = telemetry.deleteExpired(retainAfter, properties.cleanupBatchSize());
        return new CleanupResult(deletedSamples, markedSamples, deletedExpired);
    }

    private void requireOwned(UUID businessId, UUID shipmentId) {
        shipments
                .findOwned(requiredId(businessId), requiredId(shipmentId))
                .orElseThrow(TelemetryException::shipmentNotFound);
    }

    private NormalizedReading normalize(ReadingInput input) {
        if (input == null || input.clientEventId() == null || input.recordedAt() == null) {
            throw TelemetryException.invalidRequest();
        }
        Instant recordedAt = databaseTime(input.recordedAt());
        BigDecimal speed = decimal(input.speedKilometresPerHour());
        BigDecimal fuel = decimal(input.fuelLitres());
        BigDecimal temperature = decimal(input.temperatureCelsius());
        BigDecimal battery = decimal(input.batteryPercent());
        String fingerprint = fingerprint(
                input.clientEventId(),
                recordedAt,
                input.latitude(),
                input.longitude(),
                speed,
                fuel,
                temperature,
                input.sealOpen(),
                battery,
                input.networkStatus(),
                input.networkSignalDbm());
        return new NormalizedReading(
                input.clientEventId(),
                recordedAt,
                input.latitude(),
                input.longitude(),
                speed,
                fuel,
                temperature,
                input.sealOpen(),
                battery,
                input.networkStatus(),
                input.networkSignalDbm(),
                fingerprint);
    }

    private void validate(NormalizedReading reading, Instant receivedAt) {
        if (reading.recordedAt().isBefore(receivedAt.minus(properties.maximumReadingAge()))
                || reading.recordedAt().isAfter(receivedAt.plus(properties.futureClockSkew()))) {
            throw TelemetryException.invalidRequest();
        }
        if ((reading.latitude() == null) != (reading.longitude() == null)
                || !between(reading.latitude(), -90, 90)
                || !between(reading.longitude(), -180, 180)
                || !between(reading.speedKilometresPerHour(), "0", "350")
                || !between(reading.fuelLitres(), "0", "10000")
                || !between(reading.temperatureCelsius(), "-100", "150")
                || !between(reading.batteryPercent(), "0", "100")
                || (reading.networkSignalDbm() != null
                        && (reading.networkSignalDbm() < -200 || reading.networkSignalDbm() > 0))) {
            throw TelemetryException.invalidRequest();
        }
        if (reading.latitude() == null
                && reading.speedKilometresPerHour() == null
                && reading.fuelLitres() == null
                && reading.temperatureCelsius() == null
                && reading.sealOpen() == null
                && reading.batteryPercent() == null
                && reading.networkStatus() == null
                && reading.networkSignalDbm() == null) {
            throw TelemetryException.invalidRequest();
        }
    }

    private static boolean between(Double value, double minimum, double maximum) {
        return value == null || (Double.isFinite(value) && value >= minimum && value <= maximum);
    }

    private static boolean between(BigDecimal value, String minimum, String maximum) {
        return value == null
                || (value.compareTo(new BigDecimal(minimum)) >= 0 && value.compareTo(new BigDecimal(maximum)) <= 0);
    }

    private static BigDecimal decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros();
    }

    private static String fingerprint(Object... values) {
        StringBuilder canonical = new StringBuilder();
        for (Object value : values) {
            String text = value instanceof BigDecimal decimal ? decimal.toPlainString() : String.valueOf(value);
            canonical.append(text.length()).append(':').append(text).append('|');
        }
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256")
                            .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank()) {
            throw TelemetryException.invalidRequest();
        }
        return value.strip();
    }

    private static UUID requiredId(UUID value) {
        if (value == null) {
            throw TelemetryException.invalidRequest();
        }
        return value;
    }

    private static Instant databaseTime(Instant value) {
        return value.truncatedTo(ChronoUnit.MICROS);
    }

    public record ReadingInput(
            UUID clientEventId,
            Instant recordedAt,
            Double latitude,
            Double longitude,
            BigDecimal speedKilometresPerHour,
            BigDecimal fuelLitres,
            BigDecimal temperatureCelsius,
            Boolean sealOpen,
            BigDecimal batteryPercent,
            TelemetryNetworkStatus networkStatus,
            Integer networkSignalDbm) {}

    public record IssuedDevice(TelemetryDevice device, String rawCredential) {}

    public record IngestionResult(List<ReadingReceipt> readings) {}

    public record ReadingReceipt(UUID clientEventId, UUID readingId, ReceiptStatus status) {}

    public enum ReceiptStatus {
        ACCEPTED,
        DUPLICATE
    }

    public record CleanupResult(int deletedRedundantSamples, int markedDownsampled, int deletedExpired) {}

    private record NormalizedReading(
            UUID clientEventId,
            Instant recordedAt,
            Double latitude,
            Double longitude,
            BigDecimal speedKilometresPerHour,
            BigDecimal fuelLitres,
            BigDecimal temperatureCelsius,
            Boolean sealOpen,
            BigDecimal batteryPercent,
            TelemetryNetworkStatus networkStatus,
            Integer networkSignalDbm,
            String fingerprint) {

        TelemetryReading toReading(UUID id, TelemetryDevice device, Instant receivedAt) {
            return new TelemetryReading(
                    id,
                    device.id(),
                    device.shipmentId(),
                    clientEventId,
                    fingerprint,
                    recordedAt,
                    receivedAt,
                    latitude,
                    longitude,
                    speedKilometresPerHour,
                    fuelLitres,
                    temperatureCelsius,
                    sealOpen,
                    batteryPercent,
                    networkStatus,
                    networkSignalDbm,
                    TelemetryRetentionTier.RAW);
        }
    }
}
