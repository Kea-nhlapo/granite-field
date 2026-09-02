import { Navigate, useLocation } from "react-router-dom";
import type { ReactNode } from "react";

import { AppLoading } from "../../app/AppLoading";
import { useSession } from "./SessionProvider";

type RequireSessionProps = {
    children: ReactNode;
};

export function RequireSession({ children }: RequireSessionProps) {
    const { session, status } = useSession();
    const location = useLocation();

    if (status === "loading") {
        return <AppLoading />;
    }

    if (!session) {
        const from = `${location.pathname}${location.search}`;
        return (
            <Navigate replace to={`/login?from=${encodeURIComponent(from)}`} />
        );
    }

    return children;
}
