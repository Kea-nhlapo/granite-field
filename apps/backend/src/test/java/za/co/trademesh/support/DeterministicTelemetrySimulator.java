package za.co.trademesh.support;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import za.co.trademesh.modules.telemetry.application.TelemetryService;
import za.co.trademesh.modules.telemetry.domain.TelemetryNetworkStatus;

/**
 * Repeatable demo telemetry. It lives in test support so no synthetic device
 * behavior can leak into production packages.
 */
public final class DeterministicTelemetrySimulator {

    private static final UUID MOVEMENT_ONE = UUID.fromString("25000000-0000-0000-0000-000000000001");
    private static final UUID MOVEMENT_TWO = UUID.fromString("25000000-0000-0000-0000-000000000002");
    private static final UUID FUEL_BASELINE = UUID.fromString("25000000-0000-0000-0000-000000000003");
    private static final UUID FUEL_DROP = UUID.fromString("25000000-0000-0000-0000-000000000004");

    private final TelemetryService telemetry;
    private final MutableTestClock clock;

    public DeterministicTelemetrySimulator(TelemetryService telemetry, MutableTestClock clock) {
        this.telemetry = telemetry;
        this.clock = clock;
    }

    public Result run(String deviceCredential, Instant startedAt) {
        var movementOne = reading(MOVEMENT_ONE, startedAt.plus(Duration.ofMinutes(1)), -25.0000, 30.0000, "58", "320");
        var movementTwo = reading(MOVEMENT_TWO, startedAt.plus(Duration.ofMinutes(2)), -25.0005, 30.0005, "52", "318");
        var fuelBaseline = reading(FUEL_BASELINE, startedAt.plus(Duration.ofMinutes(3)), -25.0010, 30.0010, "0", "310");
        var fuelDrop = reading(FUEL_DROP, startedAt.plus(Duration.ofMinutes(7)), -25.0010, 30.0010, "0", "265");

        List<TelemetryService.ReadingReceipt> receipts = java.util.stream.Stream.of(
                        movementOne, movementTwo, fuelBaseline, fuelDrop)
                .map(reading -> ingest(deviceCredential, reading))
                .toList();
        return new Result(receipts, fuelDrop.recordedAt());
    }

    private TelemetryService.ReadingReceipt ingest(String credential, TelemetryService.ReadingInput reading) {
        clock.set(reading.recordedAt());
        TelemetryService.IngestionResult result = telemetry.ingest(credential, List.of(reading));
        TelemetryService.ReadingReceipt receipt = result.readings().getFirst();
        if (receipt.status() != TelemetryService.ReceiptStatus.ACCEPTED) {
            throw new IllegalStateException("The deterministic telemetry event was not accepted");
        }
        return receipt;
    }

    private static TelemetryService.ReadingInput reading(
            UUID eventId, Instant at, double latitude, double longitude, String speed, String fuel) {
        return new TelemetryService.ReadingInput(
                eventId,
                at,
                latitude,
                longitude,
                new BigDecimal(speed),
                new BigDecimal(fuel),
                new BigDecimal("22.5"),
                false,
                new BigDecimal("87"),
                TelemetryNetworkStatus.CONNECTED,
                -72);
    }

    public record Result(List<TelemetryService.ReadingReceipt> receipts, Instant completedAt) {
        public Result {
            receipts = List.copyOf(receipts);
        }
    }
}
