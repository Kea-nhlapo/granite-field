import { useEffect, useState } from "react";
import { useNavigate } from "react-router";

import { HomeScreen } from "../../HomeScreen";
import { OrdersScreen } from "../../OrderScreens";
import { SourceScreen, SupplierInviteScreen } from "../../SourceScreens";
import { RouteDetailScreen } from "../../RouteScreens";
import { ProfileScreen, RiskScreen } from "../../AccountScreens";
import { DocumentReviewPage } from "../documents/DocumentReviewPage";
import { HandoverPage } from "../handover/HandoverPage";
import { InsurerPage } from "../insurance/InsurerPage";
import { LogisticsPage } from "../logistics/LogisticsPage";
import { ProcurementPage } from "../procurement/ProcurementPage";
import { RoutingPage } from "../routing/RoutingPage";
import { TrackingPage } from "../shipment/TrackingPage";
import type { Screen, Tab } from "../../types";
import { TabBar } from "../../ui";
import { useSession } from "../access/session";

const TAB_FOR_SCREEN: Record<string, Tab> = {
    home: "home",
    profile: "home",
    source: "source",
    source_invite: "source",
    orders: "orders",
    orders_invoice: "orders",
    routes: "routes",
    routes_detail: "routes",
    track: "track",
    track_qr: "track",
    risk: "home",
    logistics: "routes",
    insurance: "home",
    procurement: "source",
};

export function Workspace() {
    const navigate = useNavigate();
    const { session, logout } = useSession();
    const [screen, setScreen] = useState<Screen>({ id: "home" });
    const [history, setHistory] = useState<Screen[]>([]);

    useEffect(() => {
        if (session && !session.onboardingComplete) {
            navigate("/app/onboarding", { replace: true });
        }
    }, [navigate, session]);

    function go(next: Screen) {
        setHistory((current) => [...current, screen]);
        setScreen(next);
    }

    function goBack() {
        const previous = history[history.length - 1];
        if (!previous) {
            return;
        }
        setScreen(previous);
        setHistory((current) => current.slice(0, -1));
    }

    function switchTab(tab: Tab) {
        setHistory([]);
        setScreen({ id: tab } as Screen);
    }

    const activeTab = TAB_FOR_SCREEN[screen.id] ?? "home";

    function openRisk() {
        if (session?.role === "INTERNAL_RISK") {
            navigate("/app/risk");
            return;
        }
        if (session?.role === "INSURER") {
            navigate("/app/insurance");
            return;
        }
        go({ id: "risk" });
    }

    const screenEl = (() => {
        switch (screen.id) {
            case "home":
                return (
                    <HomeScreen
                        navigate={(next) => {
                            if (next.id === "risk") {
                                openRisk();
                                return;
                            }
                            go(next);
                        }}
                    />
                );
            case "source":
                return (
                    <SourceScreen
                        navigate={(next) => {
                            if (next.id === "procurement") {
                                go(next);
                                return;
                            }
                            go(next);
                        }}
                    />
                );
            case "source_invite":
                return <SupplierInviteScreen onBack={goBack} />;
            case "procurement":
                return <ProcurementPage onBack={goBack} />;
            case "orders":
                return <OrdersScreen navigate={go} />;
            case "orders_invoice":
                return <DocumentReviewPage onBack={goBack} />;
            case "routes":
                return (
                    <RoutingPage
                        onOpenLogistics={() => go({ id: "logistics" })}
                    />
                );
            case "logistics":
                return <LogisticsPage onBack={goBack} />;
            case "routes_detail":
                return (
                    <RouteDetailScreen route={screen.route} onBack={goBack} />
                );
            case "track":
                return (
                    <TrackingPage
                        onOpenHandover={() => go({ id: "track_qr" })}
                    />
                );
            case "track_qr":
                return <HandoverPage onBack={goBack} />;
            case "risk":
                return <RiskScreen onBack={goBack} internal={false} />;
            case "insurance":
                return <InsurerPage onBack={goBack} />;
            case "profile":
                return (
                    <ProfileScreen
                        onBack={goBack}
                        internal={session?.role === "INTERNAL_RISK"}
                        showInternalToggle={session?.role !== "BUSINESS"}
                        onSignOut={() => {
                            void logout().then(() => navigate("/login"));
                        }}
                    />
                );
            default:
                return null;
        }
    })();

    const showTabBar = ![
        "source_invite",
        "orders_invoice",
        "routes_detail",
        "track_qr",
        "risk",
        "profile",
        "logistics",
        "insurance",
        "procurement",
    ].includes(screen.id);

    return (
        <>
            <div className="flex-1 flex flex-col overflow-hidden">
                {screenEl}
            </div>
            {showTabBar ? (
                <TabBar active={activeTab} onTab={switchTab} />
            ) : null}
        </>
    );
}
