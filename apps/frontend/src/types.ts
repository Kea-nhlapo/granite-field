export type Tab = "home" | "source" | "orders" | "routes" | "track";

export type Screen =
    | { id: "home" }
    | { id: "source" }
    | { id: "source_invite" }
    | { id: "orders" }
    | { id: "orders_invoice" }
    | { id: "routes" }
    | { id: "routes_detail"; route: "A" | "B" | "C" }
    | { id: "track" }
    | { id: "track_qr" }
    | { id: "risk" }
    | { id: "profile" }
    | { id: "logistics" }
    | { id: "insurance" }
    | { id: "procurement" };

export type Navigate = (s: Screen) => void;
