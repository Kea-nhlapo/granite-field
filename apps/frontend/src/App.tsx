import { useState } from "react";
import { HomeScreen } from "./HomeScreen";
import { InvoiceScreen, OrdersScreen } from "./OrderScreens";
import { SourceScreen, SupplierInviteScreen } from "./SourceScreens";
import { RouteDetailScreen, RoutesScreen } from "./RouteScreens";
import { QRScreen, TrackScreen } from "./TrackScreens";
import { ProfileScreen, RiskScreen } from "./AccountScreens";
import type { Screen, Tab } from "./types";
import { TabBar } from "./ui";

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
};

export default function App() {
  const [screen, setScreen] = useState<Screen>({ id: "home" });
  const [internal, setInternal] = useState(false);
  const [history, setHistory] = useState<Screen[]>([]);

  function navigate(s: Screen) {
    setHistory((h) => [...h, screen]);
    setScreen(s);
  }

  function goBack() {
    if (history.length === 0) return;
    setScreen(history[history.length - 1]);
    setHistory((h) => h.slice(0, -1));
  }

  function switchTab(tab: Tab) {
    setHistory([]);
    setScreen({ id: tab } as Screen);
  }

  const activeTab = TAB_FOR_SCREEN[screen.id] ?? "home";

  const screenEl = (() => {
    switch (screen.id) {
      case "home":
        return <HomeScreen navigate={navigate} />;
      case "source":
        return <SourceScreen navigate={navigate} />;
      case "source_invite":
        return <SupplierInviteScreen onBack={goBack} />;
      case "orders":
        return <OrdersScreen navigate={navigate} />;
      case "orders_invoice":
        return <InvoiceScreen onBack={goBack} />;
      case "routes":
        return <RoutesScreen navigate={navigate} />;
      case "routes_detail":
        return <RouteDetailScreen route={screen.route} onBack={goBack} />;
      case "track":
        return <TrackScreen navigate={navigate} />;
      case "track_qr":
        return <QRScreen onBack={goBack} />;
      case "risk":
        return <RiskScreen onBack={goBack} internal={internal} />;
      case "profile":
        return (
          <ProfileScreen onBack={goBack} internal={internal} onToggleInternal={() => setInternal((v) => !v)} />
        );
      default:
        return null;
    }
  })();

  const showTabBar = !["source_invite", "orders_invoice", "routes_detail", "track_qr", "risk", "profile"].includes(screen.id);

  return (
    <div
      className="h-full min-h-dvh flex items-center justify-center sm:py-6 sm:px-4"
      style={{ background: "var(--fluent-bg-canvas, #f3f2f1)" }}
    >
      <div className="fluent-stage flex flex-col overflow-hidden bg-white">
        <div className="flex-1 flex flex-col overflow-hidden">{screenEl}</div>
        {showTabBar && <TabBar active={activeTab} onTab={switchTab} />}
      </div>
    </div>
  );
}
