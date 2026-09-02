import { hasAnyRole, type AppRole } from "./roles";

export function homePathForRoles(
    roles: ReadonlySet<AppRole> | undefined,
): string {
    if (roles && hasAnyRole(roles, ["SUPPLIER"])) {
        return "/app/supplier";
    }
    return "/app";
}
