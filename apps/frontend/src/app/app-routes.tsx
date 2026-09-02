import { lazy, Suspense } from "react";
import { Navigate, type RouteObject } from "react-router-dom";

import { AppLoading } from "./AppLoading";
import { AppShell } from "../features/access/AppShell";
import LoginPage from "../features/access/LoginPage";
import { RequireRole } from "../features/access/RequireRole";
import { RequireSession } from "../features/access/RequireSession";
import { useSession } from "../features/access/SessionProvider";
import CustomerSettingsPage from "../features/access/CustomerSettingsPage";
import CustomerTrustPage from "../features/access/CustomerTrustPage";
import SignupPage from "../features/access/SignupPage";
import SupplierHomePage from "../features/access/SupplierHomePage";
import WorkspacePage from "../features/access/WorkspacePage";
import { homePathForRoles } from "../features/access/home-path";
import OnboardingPage from "../features/business/OnboardingPage";
import GuestInvitePage from "../features/guest/GuestInvitePage";
import DocumentReviewPage from "../features/documents/DocumentReviewPage";
import ProcurementPage from "../features/procurement/ProcurementPage";
import LogisticsPage from "../features/logistics/LogisticsPage";
import RoutingPage from "../features/routing/RoutingPage";
import TrackingPage from "../features/tracking/TrackingPage";
import HandoverPage from "../features/handover/HandoverPage";
import type { AppRole } from "../features/access/roles";

const InternalRiskPage = lazy(
    () => import("../features/internal-risk/InternalRiskPage"),
);
const InsurancePage = lazy(() => import("../features/insurance/InsurancePage"));

function RestrictedLazy({
    roles,
    page,
}: {
    roles: ReadonlyArray<AppRole>;
    page: "risk" | "insurance";
}) {
    return (
        <RequireRole roles={roles}>
            <Suspense fallback={<AppLoading />}>
                {page === "risk" ? <InternalRiskPage /> : <InsurancePage />}
            </Suspense>
        </RequireRole>
    );
}

function HomeRedirect() {
    const { session, status } = useSession();

    if (status === "loading") {
        return <AppLoading />;
    }

    return (
        <Navigate
            replace
            to={session ? homePathForRoles(session.roles) : "/login"}
        />
    );
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
        element: <SignupPage kind="customer" />,
        path: "/signup",
    },
    {
        element: <SignupPage kind="supplier" />,
        path: "/signup/supplier",
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
                    <RequireRole roles={["SUPPLIER"]}>
                        <SupplierHomePage />
                    </RequireRole>
                ),
                path: "supplier",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <CustomerSettingsPage />
                    </RequireRole>
                ),
                path: "settings",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <CustomerTrustPage />
                    </RequireRole>
                ),
                path: "trust",
            },
            {
                element: (
                    <RestrictedLazy
                        page="risk"
                        roles={["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]}
                    />
                ),
                path: "internal-risk",
            },
            {
                element: (
                    <RestrictedLazy
                        page="risk"
                        roles={["INTERNAL_RISK_ANALYST", "ADMINISTRATOR"]}
                    />
                ),
                path: "internal-risk/:shipmentId",
            },
            {
                element: (
                    <RestrictedLazy page="insurance" roles={["INSURER"]} />
                ),
                path: "insurance",
            },
            {
                element: (
                    <RestrictedLazy page="insurance" roles={["INSURER"]} />
                ),
                path: "insurance/:caseId",
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
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <TrackingPage />
                    </RequireRole>
                ),
                path: "tracking/:businessId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <TrackingPage />
                    </RequireRole>
                ),
                path: "tracking/:businessId/shipments/:shipmentId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <HandoverPage />
                    </RequireRole>
                ),
                path: "handover/:businessId",
            },
            {
                element: (
                    <RequireRole roles={["BUSINESS_OWNER", "BUSINESS_MEMBER"]}>
                        <HandoverPage />
                    </RequireRole>
                ),
                path: "handover/:businessId/shipments/:shipmentId/challenges/:challengeId",
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
