package za.co.trademesh.modules.payment.domain;

public enum EscrowTransactionStatus {
    REQUESTED,
    PENDING,
    SUCCESSFUL,
    FAILED,
    TIMED_OUT;

    public boolean finalState() {
        return this == SUCCESSFUL || this == FAILED || this == TIMED_OUT;
    }
}
