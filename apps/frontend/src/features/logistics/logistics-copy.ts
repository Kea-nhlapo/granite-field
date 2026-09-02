export const exclusionCopy = {
    SUPPLIER_OR_PICKUP_MISMATCH:
        "Pickup or supplier is not compatible with this group.",
    DISTANCE_EXCEEDS_LIMIT:
        "The delivery location is outside the allowed distance.",
    DELIVERY_WINDOWS_DO_NOT_OVERLAP:
        "The delivery windows do not overlap enough.",
    CARGO_PROFILE_UNAVAILABLE:
        "Cargo details are not available for this order.",
    CARGO_NOT_COMPATIBLE: "The cargo profile is not compatible.",
} as const;

export type ExclusionReason = keyof typeof exclusionCopy;

export function exclusionReasonText(reason: string | undefined) {
    if (reason && reason in exclusionCopy) {
        return exclusionCopy[reason as ExclusionReason];
    }
    return "This order cannot join the group.";
}
