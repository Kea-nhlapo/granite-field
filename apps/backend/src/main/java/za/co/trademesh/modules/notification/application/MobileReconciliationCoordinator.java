package za.co.trademesh.modules.notification.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Component;
import za.co.trademesh.modules.notification.domain.MobileNotificationRepository;
import za.co.trademesh.modules.notification.domain.MobileNotificationStatus;

@Component
class MobileReconciliationCoordinator {

    private final MobileNotificationRepository repository;
    private final MobileDeliveryProvider provider;
    private final InfobipStatusService statuses;

    MobileReconciliationCoordinator(
            MobileNotificationRepository repository, MobileDeliveryProvider provider, InfobipStatusService statuses) {
        this.repository = repository;
        this.provider = provider;
        this.statuses = statuses;
    }

    void reconcile(UUID notificationId) throws ReconciliationRetryException {
        var notification = repository
                .findNotification(notificationId)
                .orElseThrow(() -> new IllegalStateException("Mobile notification does not exist"));
        if (notification.status() != MobileNotificationStatus.SUBMISSION_UNKNOWN) {
            return;
        }
        try {
            var result = provider.reconcile(notificationId);
            if (result.isEmpty()) {
                throw new ReconciliationRetryException("INFOBIP_REPORT_PENDING", null);
            }
            var report = result.get();
            statuses.record(new InfobipStatusService.StatusUpdate(
                    notificationId,
                    report.providerMessageId(),
                    report.providerStatus(),
                    report.status(),
                    fingerprint(notificationId, report.providerMessageId(), report.providerStatus()),
                    report.observedAt()));
        } catch (MobileProviderException failure) {
            if (failure.kind() == MobileProviderException.FailureKind.PERMANENT) {
                throw new IllegalStateException("Infobip reconciliation failed permanently", failure);
            }
            throw new ReconciliationRetryException(failure.code(), failure);
        }
    }

    private static String fingerprint(UUID notificationId, String providerMessageId, String providerStatus) {
        String value = "reconcile|" + notificationId + "|" + providerMessageId + "|" + providerStatus;
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 must be available", impossible);
        }
    }

    static final class ReconciliationRetryException extends Exception {
        ReconciliationRetryException(String code, Throwable cause) {
            super("Mobile reconciliation failed with code " + code, cause);
        }
    }
}
