export const APP_ROLES = [
    "BUSINESS_OWNER",
    "BUSINESS_MEMBER",
    "SUPPLIER",
    "TRANSPORTER",
    "DRIVER",
    "INTERNAL_RISK_ANALYST",
    "INSURER",
    "ADMINISTRATOR",
] as const;

export type AppRole = (typeof APP_ROLES)[number];

const appRoleSet = new Set<string>(APP_ROLES);

export function isAppRole(value: string): value is AppRole {
    return appRoleSet.has(value);
}

export function rolesFromList(
    values: ReadonlyArray<string> | undefined,
): ReadonlySet<AppRole> {
    const roles = new Set<AppRole>();
    for (const value of values ?? []) {
        if (isAppRole(value)) {
            roles.add(value);
        }
    }
    return roles;
}

export function hasAnyRole(
    actual: ReadonlySet<AppRole>,
    required: ReadonlyArray<AppRole>,
): boolean {
    return required.some((role) => actual.has(role));
}
