package za.co.trademesh.modules.telemetry.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.telemetry.domain.TelemetryReading;
import za.co.trademesh.modules.telemetry.domain.TelemetryRepository;

@Service
class TelemetryRiskQueryService implements TelemetryRiskCatalog {

    private final TelemetryRepository telemetry;

    TelemetryRiskQueryService(TelemetryRepository telemetry) {
        this.telemetry = telemetry;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RiskReading> findReading(UUID readingId) {
        return telemetry.findReading(readingId).map(TelemetryRiskQueryService::riskReading);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RiskReading> findReadingsThrough(UUID shipmentId, Instant from, Instant through, int limit) {
        return telemetry.findReadingsThrough(shipmentId, from, through, limit).stream()
                .map(TelemetryRiskQueryService::riskReading)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfflineDevice> findOfflineDevices(Instant lastSeenBefore, int limit) {
        return telemetry.findOfflineDevices(lastSeenBefore, limit).stream()
                .map(device -> new OfflineDevice(
                        device.id(),
                        device.shipmentId(),
                        device.lastSeenAt() == null ? device.createdAt() : device.lastSeenAt()))
                .toList();
    }

    private static RiskReading riskReading(TelemetryReading reading) {
        return new RiskReading(
                reading.id(),
                reading.deviceId(),
                reading.shipmentId(),
                reading.recordedAt(),
                reading.receivedAt(),
                reading.latitude(),
                reading.longitude(),
                reading.speedKilometresPerHour(),
                reading.fuelLitres(),
                reading.sealOpen());
    }
}
