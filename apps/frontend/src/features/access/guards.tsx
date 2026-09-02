import { Navigate, Outlet, useLocation } from "react-router";
import type { ReactNode } from "react";

import type { PublicRole } from "../../shared/api/generated";
import { useSession } from "./session";

export function RequireAuth() {
    const { session, loading } = useSession();
    const location = useLocation();

    if (loading) {
        return (
            <main className="p-8 text-sm" aria-busy="true">
                Loading application...
            </main>
        );
    }

    if (!session) {
        return (
            <Navigate to="/login" replace state={{ from: location.pathname }} />
        );
    }

    return <Outlet />;
}

export function RequireRole({
    role,
    children,
}: {
    role: PublicRole;
    children: ReactNode;
}) {
    const { session } = useSession();
    if (!session) {
        return <Navigate to="/login" replace />;
    }
    if (session.role !== role) {
        return <Navigate to="/forbidden" replace />;
    }
    return children;
}

export function GuestOnly({ children }: { children: ReactNode }) {
    const { session, loading } = useSession();
    if (loading) {
        return (
            <main className="p-8 text-sm" aria-busy="true">
                Loading application...
            </main>
        );
    }
    if (session) {
        return <Navigate to="/app" replace />;
    }
    return children;
}
