package za.co.trademesh.modules.payment.domain;

public enum EscrowStatus {
    LOCK_REQUESTED,
    LOCK_PENDING,
    LOCKED,
    LOCK_FAILED,
    RELEASE_REQUESTED,
    RELEASE_PENDING,
    RELEASED,
    RELEASE_FAILED
}
