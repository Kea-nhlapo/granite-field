package za.co.trademesh.modules.handover.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("trademesh.handover")
public record HandoverProperties(
        Duration challengeTtl, Duration allowedClockSkew, int locationToleranceMetres, int maxQuantityNoteLength) {

    public HandoverProperties {
        requirePositive(challengeTtl, "challenge-ttl");
        requireNonNegative(allowedClockSkew, "allowed-clock-skew");
        if (challengeTtl.compareTo(Duration.ofHours(1)) > 0) {
            throw new IllegalArgumentException("Handover challenge-ttl cannot exceed one hour");
        }
        if (allowedClockSkew.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("Handover allowed-clock-skew cannot exceed ten minutes");
        }
        if (locationToleranceMetres < 1 || locationToleranceMetres > 10_000) {
            throw new IllegalArgumentException("Handover location-tolerance-metres must be between 1 and 10000");
        }
        if (maxQuantityNoteLength < 1 || maxQuantityNoteLength > 500) {
            throw new IllegalArgumentException("Handover max-quantity-note-length must be between 1 and 500");
        }
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("Handover " + name + " must be positive");
        }
    }

    private static void requireNonNegative(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException("Handover " + name + " cannot be negative");
        }
    }
}
