package za.co.trademesh.modules.handover.domain;

public enum HandoverState {
    PENDING,
    COMPLETED,
    DISPUTED,
    EXPIRED;

    public boolean terminal() {
        return this != PENDING;
    }
}
