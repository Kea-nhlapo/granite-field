import { Navigate, Outlet, useNavigate } from "react-router";
import type { RouteObject } from "react-router";

import { ForbiddenPage, UnauthorizedPage } from "../features/access/AuthPages";
import { GuestOnly, RequireAuth, RequireRole } from "../features/access/guards";
import { LoginPage } from "../features/auth/LoginPage";
import { OnboardingPage } from "../features/business/OnboardingPage";
import { InsurerPage } from "../features/insurance/InsurerPage";
import { InternalRiskPage } from "../features/internal-risk/InternalRiskPage";
import { NotFoundPage } from "../features/shell/NotFoundPage";
import { Workspace } from "../features/shell/Workspace";
import { GuestInvitePage } from "../features/supplier/GuestInvitePage";

function MobileShell() {
    return (
        <div
            className="h-full min-h-dvh flex items-center justify-center"
            style={{ background: "var(--surface)" }}
        >
            <div className="mobile-stage flex flex-col overflow-hidden">
                <Outlet />
            </div>
        </div>
    );
}

function RiskRoute() {
    const navigate = useNavigate();
    return (
        <RequireRole role="INTERNAL_RISK">
            <InternalRiskPage onBack={() => navigate("/app")} />
        </RequireRole>
    );
}

function InsuranceRoute() {
    const navigate = useNavigate();
    return (
        <RequireRole role="INSURER">
            <InsurerPage onBack={() => navigate("/app")} />
        </RequireRole>
    );
}

export const appRoutes: RouteObject[] = [
    {
        path: "/login",
        element: (
            <GuestOnly>
                <LoginPage />
            </GuestOnly>
        ),
    },
    { path: "/invite/:token", element: <GuestInvitePage /> },
    { path: "/unauthorized", element: <UnauthorizedPage /> },
    { path: "/forbidden", element: <ForbiddenPage /> },
    {
        path: "/app",
        element: <RequireAuth />,
        children: [
            {
                element: <MobileShell />,
                children: [
                    { index: true, element: <Workspace /> },
                    { path: "onboarding", element: <OnboardingPage /> },
                    { path: "risk", element: <RiskRoute /> },
                    { path: "insurance", element: <InsuranceRoute /> },
                ],
            },
        ],
    },
    { path: "/", element: <Navigate to="/login" replace /> },
    { path: "*", element: <NotFoundPage /> },
];
