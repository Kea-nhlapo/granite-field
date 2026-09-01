package za.co.trademesh.modules.routing.domain;

/**
 * Vehicle constraints as a ROUTING REQUEST PARAMETER — deliberately not a fleet
 * entity. Issue #14 owns the vehicle and capacity model; this must not quietly
 * become a second one that later has to be reconciled.
 */
public record RoutingVehicleLimits(int heightMillimetres, int weightKilograms) {

    public RoutingVehicleLimits {
        if (heightMillimetres <= 0) {
            throw new IllegalArgumentException("vehicle height must be positive, was " + heightMillimetres);
        }
        if (weightKilograms <= 0) {
            throw new IllegalArgumentException("vehicle weight must be positive, was " + weightKilograms);
        }
    }
}
