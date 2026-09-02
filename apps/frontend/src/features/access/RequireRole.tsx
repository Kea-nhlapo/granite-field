import type { ReactNode } from "react";

import { hasAnyRole, type AppRole } from "./roles";
import { ForbiddenPage } from "./ForbiddenPage";
import { useSession } from "./SessionProvider";

type RequireRoleProps = {
    children: ReactNode;
    roles: ReadonlyArray<AppRole>;
};

export function RequireRole({ children, roles }: RequireRoleProps) {
    const { session } = useSession();

    if (!session || !hasAnyRole(session.roles, roles)) {
        return <ForbiddenPage />;
    }

    return children;
}
