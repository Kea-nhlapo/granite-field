package za.co.trademesh.modules.transport.domain;

public record CapacityConstraintResult(
        CapacityMatchConstraint constraint, CapacityConstraintOutcome outcome, String explanation) {

    public boolean passed() {
        return outcome == CapacityConstraintOutcome.PASS;
    }
}
