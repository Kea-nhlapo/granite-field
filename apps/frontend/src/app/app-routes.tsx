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
import DocumentReviewPage from "../features/documents/DocumentReviewPage";
import ProcurementPage from "../features/procurement/ProcurementPage";
import LogisticsPage from "../features/logistics/LogisticsPage";
import RoutingPage from "../features/routing/RoutingPage";

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
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <DocumentReviewPage />
                    </RequireRole>
                ),
                path: "documents/:businessId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <DocumentReviewPage />
                    </RequireRole>
                ),
                path: "documents/:businessId/:documentId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <ProcurementPage />
                    </RequireRole>
                ),
                path: "procurement/:businessId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <ProcurementPage />
                    </RequireRole>
                ),
                path: "procurement/:businessId/quotes/:quoteId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <ProcurementPage />
                    </RequireRole>
                ),
                path: "procurement/:businessId/orders/:orderId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <LogisticsPage />
                    </RequireRole>
                ),
                path: "logistics/:businessId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <LogisticsPage />
                    </RequireRole>
                ),
                path: "logistics/:businessId/suggestions/:suggestionId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <LogisticsPage />
                    </RequireRole>
                ),
                path: "logistics/:businessId/capacity-matches/:searchId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <RoutingPage />
                    </RequireRole>
                ),
                path: "routing/:businessId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <RoutingPage />
                    </RequireRole>
                ),
                path: "routing/:businessId/calculations/:calculationId/assessments/:assessmentId",
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
