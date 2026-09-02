import { Navigate, type RouteObject } from "react-router-dom";

import { AppLoading } from "./AppLoading";
import { AppShell } from "../features/access/AppShell";
import InternalRiskPlaceholderPage from "../features/access/InternalRiskPlaceholderPage";
import LoginPage from "../features/access/LoginPage";
import { RequireRole } from "../features/access/RequireRole";
import { RequireSession } from "../features/access/RequireSession";
import { useSession } from "../features/access/SessionProvider";
import WorkspacePage from "../features/access/WorkspacePage";
import OnboardingPage from "../features/business/OnboardingPage";
import GuestInvitePage from "../features/guest/GuestInvitePage";

function HomeRedirect() {
    const { session, status } = useSession();

    if (status === "loading") {
        return <AppLoading />;
    }

    return <Navigate replace to={session ? "/app" : "/login"} />;
}

export const appRoutes: RouteObject[] = [
    {
        element: <HomeRedirect />,
        path: "/",
    },
    {
        element: <LoginPage />,
        path: "/login",
    },
    {
        element: <GuestInvitePage />,
        path: "/supplier-invitations/guest/:token",
    },
    {
        children: [
            {
                element: <WorkspacePage />,
                index: true,
            },
            {
                element: (
                    <RequireRole
                        roles={["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]}
                    >
                        <InternalRiskPlaceholderPage />
                    </RequireRole>
                ),
                path: "internal-risk",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER"]}>
                        <OnboardingPage />
                    </RequireRole>
                ),
                path: "onboarding",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER"]}>
                        <OnboardingPage />
                    </RequireRole>
                ),
                path: "onboarding/:onboardingId",
            },
        ],
        element: (
            <RequireSession>
                <AppShell />
            </RequireSession>
        ),
        path: "/app",
    },
];
