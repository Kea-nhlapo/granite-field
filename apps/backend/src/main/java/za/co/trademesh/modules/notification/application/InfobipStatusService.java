package za.co.trademesh.modules.notification.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.co.trademesh.modules.notification.domain.MobileNotification;
import za.co.trademesh.modules.notification.domain.MobileNotificationRepository;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;
import za.co.trademesh.modules.notification.domain.MobileStatusObservation;

@Service
public class InfobipStatusService {

    private static final String PROVIDER = "infobip";
    private static final Duration MAXIMUM_FUTURE_SKEW = Duration.ofMinutes(5);

    private final MobileNotificationRepository repository;
    private final Clock clock;

    public InfobipStatusService(MobileNotificationRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public boolean record(StatusUpdate update) {
        if (update == null
                || update.status() == null
                || update.callbackFingerprint() == null
                || !update.callbackFingerprint().matches("[0-9a-f]{64}")
                || update.providerStatus() == null
                || update.providerStatus().isBlank()) {
            throw new IllegalArgumentException("Invalid Infobip status update");
        }
        Optional<MobileNotification> resolved = resolve(update);
        if (resolved.isEmpty()) {
            return false;
        }
        MobileNotification notification = repository
                .findNotificationForUpdate(resolved.get().id())
                .orElseThrow(() -> new IllegalStateException("Mobile notification disappeared"));
        if (notification.providerKey() != null && !PROVIDER.equals(notification.providerKey())) {
            return false;
        }
        if (notification.providerMessageId() != null
                && update.providerMessageId() != null
                && !notification
                        .providerMessageId()
                        .equals(update.providerMessageId().strip())) {
            return false;
        }
        Instant receivedAt = clock.instant();
        Instant observedAt = safeObservedAt(update.observedAt(), receivedAt, notification.createdAt());
        boolean inserted = repository.saveObservation(new MobileStatusObservation(
                UUID.randomUUID(),
                notification.id(),
                update.callbackFingerprint(),
                PROVIDER,
                update.providerMessageId(),
                update.providerStatus().strip(),
                observedAt,
                receivedAt));
        if (!inserted) {
            return true;
        }
        if (shouldAdvance(notification.status(), update.status())) {
            repository.updateStatus(
                    notification.id(), PROVIDER, update.providerMessageId(), update.status(), observedAt, receivedAt);
        }
        return true;
    }

    private Optional<MobileNotification> resolve(StatusUpdate update) {
        if (update.notificationId() != null) {
            Optional<MobileNotification> notification = repository.findNotification(update.notificationId());
            if (notification.isPresent()) {
                return notification;
            }
        }
        if (update.providerMessageId() == null || update.providerMessageId().isBlank()) {
            return Optional.empty();
        }
        return repository.findByProviderMessageId(
                PROVIDER, update.providerMessageId().strip());
    }

    static boolean shouldAdvance(MobileNotificationStatus current, MobileNotificationStatus target) {
        if (current == target || current == MobileNotificationStatus.SUPPRESSED) {
            return false;
        }
        if (current == MobileNotificationStatus.READ || current.finalFailure()) {
            return false;
        }
        if (current == MobileNotificationStatus.DELIVERED) {
            return target == MobileNotificationStatus.READ;
        }
        if (target.finalFailure()) {
            return current != MobileNotificationStatus.DELIVERED && current != MobileNotificationStatus.READ;
        }
        return progress(target) > progress(current);
    }

    private static int progress(MobileNotificationStatus status) {
        return switch (status) {
            case PENDING -> 0;
            case SUBMITTING, SUBMISSION_UNKNOWN -> 1;
            case ACCEPTED -> 2;
            case QUEUED -> 3;
            case SENT -> 4;
            case DELIVERED -> 5;
            case READ -> 6;
            case FAILED, REJECTED, EXPIRED, SUPPRESSED -> -1;
        };
    }

    private static Instant safeObservedAt(Instant observedAt, Instant receivedAt, Instant createdAt) {
        if (observedAt == null
                || observedAt.isBefore(createdAt)
                || observedAt.isAfter(receivedAt.plus(MAXIMUM_FUTURE_SKEW))) {
            return receivedAt;
        }
        return observedAt;
    }

    public record StatusUpdate(
            UUID notificationId,
            String providerMessageId,
            String providerStatus,
            MobileNotificationStatus status,
            String callbackFingerprint,
            Instant observedAt) {}
}
