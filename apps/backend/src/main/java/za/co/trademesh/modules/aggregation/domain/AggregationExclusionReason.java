package za.co.trademesh.modules.aggregation.domain;

public enum AggregationExclusionReason {
    SUPPLIER_OR_PICKUP_MISMATCH,
    DISTANCE_EXCEEDS_LIMIT,
    DELIVERY_WINDOWS_DO_NOT_OVERLAP,
    CARGO_PROFILE_UNAVAILABLE,
    CARGO_NOT_COMPATIBLE
}
