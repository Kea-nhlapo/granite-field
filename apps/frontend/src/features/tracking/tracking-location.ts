import type { AppRole } from "../access/roles";
import { hasAnyRole } from "../access/roles";

const preciseRoles: AppRole[] = [
    "BUSINESS_OWNER",
    "BUSINESS_MEMBER",
    "ADMINISTRATOR",
];

export function canRequestPreciseTelemetry(
    roles: ReadonlySet<AppRole> | undefined,
) {
    return roles !== undefined && hasAnyRole(roles, preciseRoles);
}

export function displayCoordinate(value: number | undefined, precise: boolean) {
    if (value === undefined || Number.isNaN(value)) {
        return "unavailable";
    }
    return precise ? value.toFixed(4) : value.toFixed(1);
}
