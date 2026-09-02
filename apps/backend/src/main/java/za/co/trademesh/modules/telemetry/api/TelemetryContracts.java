package za.co.trademesh.modules.telemetry.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.telemetry.application.TelemetryService;
import za.co.trademesh.modules.telemetry.domain.TelemetryDevice;
import za.co.trademesh.modules.telemetry.domain.TelemetryDeviceStatus;
import za.co.trademesh.modules.telemetry.domain.TelemetryLivePosition;
import za.co.trademesh.modules.telemetry.domain.TelemetryNetworkStatus;
import za.co.trademesh.modules.telemetry.domain.TelemetryReading;
import za.co.trademesh.modules.telemetry.domain.TelemetryRetentionTier;

public final class TelemetryContracts {

    private TelemetryContracts() {}

    public record ProvisionDeviceRequest(
            @NotBlank @Size(max = 120) String displayName) {}

    public record IngestReadingsRequest(@NotEmpty List<@NotNull @Valid ReadingRequest> readings) {}

    public record ReadingRequest(
            @NotNull UUID clientEventId,
            @NotNull Instant recordedAt,
            Double latitude,
            Double longitude,
            BigDecimal speedKilometresPerHour,
            BigDecimal fuelLitres,
            BigDecimal temperatureCelsius,
            Boolean sealOpen,
            BigDecimal batteryPercent,
            TelemetryNetworkStatus networkStatus,
            Integer networkSignalDbm) {

        TelemetryService.ReadingInput toInput() {
            return new TelemetryService.ReadingInput(
                    clientEventId,
                    recordedAt,
                    latitude,
                    longitude,
                    speedKilometresPerHour,
                    fuelLitres,
                    temperatureCelsius,
                    sealOpen,
                    batteryPercent,
                    networkStatus,
                    networkSignalDbm);
        }
    }

    public record IssuedDeviceResponse(
            UUID deviceId,
            UUID shipmentId,
            String displayName,
            TelemetryDeviceStatus status,
            String credential,
            Instant createdAt) {

        static IssuedDeviceResponse from(TelemetryService.IssuedDevice issued) {
            TelemetryDevice device = issued.device();
            return new IssuedDeviceResponse(
                    device.id(),
                    device.shipmentId(),
                    device.displayName(),
                    device.status(),
                    issued.rawCredential(),
                    device.createdAt());
        }
    }

    public record IngestionResponse(List<ReadingReceiptResponse> readings, int acceptedCount, int duplicateCount) {

        static IngestionResponse from(TelemetryService.IngestionResult result) {
            List<ReadingReceiptResponse> readings =
                    result.readings().stream().map(ReadingReceiptResponse::from).toList();
            int accepted = (int) result.readings().stream()
                    .filter(reading -> reading.status() == TelemetryService.ReceiptStatus.ACCEPTED)
                    .count();
            return new IngestionResponse(readings, accepted, readings.size() - accepted);
        }
    }

    public record ReadingReceiptResponse(UUID clientEventId, UUID readingId, TelemetryService.ReceiptStatus status) {
        static ReadingReceiptResponse from(TelemetryService.ReadingReceipt receipt) {
            return new ReadingReceiptResponse(receipt.clientEventId(), receipt.readingId(), receipt.status());
        }
    }

    public record LivePositionResponse(
            UUID shipmentId,
            UUID deviceId,
            UUID readingId,
            Instant recordedAt,
            Instant receivedAt,
            double latitude,
            double longitude,
            BigDecimal speedKilometresPerHour,
            BigDecimal batteryPercent,
            TelemetryNetworkStatus networkStatus,
            Integer networkSignalDbm,
            UnitsResponse units) {

        static LivePositionResponse from(TelemetryLivePosition position) {
            return new LivePositionResponse(
                    position.shipmentId(),
                    position.deviceId(),
                    position.readingId(),
                    position.recordedAt(),
                    position.receivedAt(),
                    position.latitude(),
                    position.longitude(),
                    position.speedKilometresPerHour(),
                    position.batteryPercent(),
                    position.networkStatus(),
                    position.networkSignalDbm(),
                    UnitsResponse.CANONICAL);
        }
    }

    public record ReadingHistoryResponse(List<ReadingResponse> readings, UnitsResponse units) {
        static ReadingHistoryResponse from(List<TelemetryReading> readings) {
            return new ReadingHistoryResponse(
                    readings.stream().map(ReadingResponse::from).toList(), UnitsResponse.CANONICAL);
        }
    }

    public record ReadingResponse(
            UUID readingId,
            UUID deviceId,
            UUID clientEventId,
            Instant recordedAt,
            Instant receivedAt,
            Double latitude,
            Double longitude,
            BigDecimal speedKilometresPerHour,
            BigDecimal fuelLitres,
            BigDecimal temperatureCelsius,
            Boolean sealOpen,
            BigDecimal batteryPercent,
            TelemetryNetworkStatus networkStatus,
            Integer networkSignalDbm,
            TelemetryRetentionTier retentionTier) {

        static ReadingResponse from(TelemetryReading reading) {
            return new ReadingResponse(
                    reading.id(),
                    reading.deviceId(),
                    reading.clientEventId(),
                    reading.recordedAt(),
                    reading.receivedAt(),
                    reading.latitude(),
                    reading.longitude(),
                    reading.speedKilometresPerHour(),
                    reading.fuelLitres(),
                    reading.temperatureCelsius(),
                    reading.sealOpen(),
                    reading.batteryPercent(),
                    reading.networkStatus(),
                    reading.networkSignalDbm(),
                    reading.retentionTier());
        }
    }

    public record UnitsResponse(
            String coordinates, String speed, String fuel, String temperature, String battery, String networkSignal) {
        private static final UnitsResponse CANONICAL = new UnitsResponse(
                "WGS84 decimal degrees", "kilometres per hour", "litres", "degrees Celsius", "percent", "dBm");
    }
}
