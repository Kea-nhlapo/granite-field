import { useEffect, useState } from "react";
import { OnboardingScreen, LoginScreen } from "./OnboardingScreens";
import { HomeScreen } from "./HomeScreen";
import { InvoiceScreen, OrdersScreen } from "./OrderScreens";
import { SourceScreen, SupplierInviteScreen } from "./SourceScreens";
import { SupplierMatchScreen } from "./SupplierMatchScreen";
import { RouteDetailScreen, RoutesScreen } from "./RouteScreens";
import {
    QRScreen,
    readDeliveryHandoverLink,
    TrackScreen,
} from "./TrackScreens";
import { ProfileScreen, RiskScreen } from "./AccountScreens";
import { WalletScreen } from "./WalletScreen";
import type { Screen, Tab } from "./types";
import { TabBar } from "./ui";
import { useSession } from "./features/access/SessionProvider";

const TAB_FOR_SCREEN: Record<string, Tab> = {
    onboarding: "home",
    login: "home",
    home: "home",
    profile: "home",
    source: "source",
    source_invite: "source",
    source_match: "source",
    orders: "orders",
    orders_invoice: "orders",
    routes: "routes",
    routes_detail: "routes",
    track: "track",
    track_qr: "track",
    risk: "home",
    wallet: "home",
};

export default function App() {
    const [screen, setScreen] = useState<Screen>({ id: "onboarding" });
    const [history, setHistory] = useState<Screen[]>([]);
    const { login, session, status } = useSession();
    const internal =
        session?.roles.has("INTERNAL_RISK_ANALYST") === true ||
        session?.roles.has("ADMINISTRATOR") === true;

    useEffect(() => {
        if (status === "authenticated" && readDeliveryHandoverLink()) {
            setHistory([]);
            setScreen({ id: "track_qr" });
        }
    }, [status]);

    function navigate(s: Screen) {
        setHistory((h) => [...h, screen]);
        setScreen(s);
    }

    function goBack() {
        const previous = history.at(-1);
        if (!previous) return;
        setScreen(previous);
        setHistory((h) => h.slice(0, -1));
    }

    function switchTab(tab: Tab) {
        setHistory([]);
        setScreen({ id: tab } as Screen);
    }

    async function signIn(email: string, password: string) {
        const result = await login(email, password);
        if (!result.session) {
            return result.error?.detail ?? "Sign-in failed. Please try again.";
        }
        setHistory([]);
        setScreen(
            readDeliveryHandoverLink() ? { id: "track_qr" } : { id: "home" },
        );
        return undefined;
    }

    const activeTab = TAB_FOR_SCREEN[screen.id] ?? "home";

    const screenEl = (() => {
        switch (screen.id) {
            case "onboarding":
                return (
                    <OnboardingScreen
                        onDone={() => navigate({ id: "login" })}
                    />
                );
            case "login":
                return (
                    <LoginScreen
                        authenticationReady={status !== "loading"}
                        onSignedIn={signIn}
                    />
                );
            case "home":
                return <HomeScreen navigate={navigate} />;
            case "source":
                return <SourceScreen navigate={navigate} />;
            case "source_invite":
                return <SupplierInviteScreen onBack={goBack} />;
            case "source_match":
                return <SupplierMatchScreen onBack={goBack} />;
            case "orders":
                return <OrdersScreen navigate={navigate} />;
            case "orders_invoice":
                return <InvoiceScreen onBack={goBack} />;
            case "routes":
                return <RoutesScreen navigate={navigate} />;
            case "routes_detail":
                return (
                    <RouteDetailScreen route={screen.route} onBack={goBack} />
                );
            case "track":
                return <TrackScreen navigate={navigate} />;
            case "track_qr":
                return <QRScreen onBack={goBack} />;
            case "risk":
                return <RiskScreen onBack={goBack} internal={internal} />;
            case "profile":
                return (
                    <ProfileScreen
                        onBack={goBack}
                        internal={internal}
                        navigate={navigate}
                    />
                );
            case "wallet":
                return <WalletScreen onBack={goBack} />;
            default:
                return null;
        }
    })();

    const showTabBar = ![
        "onboarding",
        "login",
        "source_invite",
        "source_match",
        "orders_invoice",
        "routes_detail",
        "track_qr",
        "risk",
        "profile",
        "wallet",
    ].includes(screen.id);

    return (
        <div
            className="h-full min-h-dvh flex items-center justify-center sm:py-6 sm:px-4"
            style={{ background: "var(--fluent-bg-canvas, #f3f2f1)" }}
        >
            <div className="fluent-stage flex flex-col overflow-hidden bg-white">
                <div className="flex-1 flex flex-col overflow-hidden">
                    {screenEl}
                </div>
                {showTabBar && <TabBar active={activeTab} onTab={switchTab} />}
            </div>
        </div>
    );
}
