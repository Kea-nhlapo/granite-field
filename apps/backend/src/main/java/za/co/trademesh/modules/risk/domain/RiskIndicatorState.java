package za.co.trademesh.modules.risk.domain;

public enum RiskIndicatorState {
    OPEN,
    ACKNOWLEDGED,
    INVESTIGATING,
    RESOLVED,
    FALSE_POSITIVE;

    public boolean isActive() {
        return this == OPEN || this == ACKNOWLEDGED || this == INVESTIGATING;
    }

    public boolean canTransitionTo(RiskIndicatorState target) {
        if (!isActive() || target == null || target == OPEN || target == this) {
            return false;
        }
        return switch (this) {
            case OPEN -> true;
            case ACKNOWLEDGED -> target == INVESTIGATING || target == RESOLVED || target == FALSE_POSITIVE;
            case INVESTIGATING -> target == RESOLVED || target == FALSE_POSITIVE;
            case RESOLVED, FALSE_POSITIVE -> false;
        };
    }
}
