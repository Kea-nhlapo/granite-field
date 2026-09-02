package za.co.trademesh.modules.notification.domain;

public enum MobileNotificationStatus {
    PENDING,
    SUBMITTING,
    SUBMISSION_UNKNOWN,
    ACCEPTED,
    QUEUED,
    SENT,
    DELIVERED,
    READ,
    FAILED,
    REJECTED,
    EXPIRED,
    SUPPRESSED;

    public boolean deliverable() {
        return this == PENDING;
    }

    public boolean finalFailure() {
        return this == FAILED || this == REJECTED || this == EXPIRED;
    }
}
