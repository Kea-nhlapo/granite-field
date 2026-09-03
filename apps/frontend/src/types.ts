export type Tab = "home" | "source" | "orders" | "routes" | "track";

export type Screen =
    | { id: "onboarding" }
    | { id: "login" }
    | { id: "signup" }
    | { id: "home" }
    | { id: "source" }
    | { id: "source_invite" }
    | { id: "source_match" }
    | { id: "orders" }
    | { id: "orders_invoice" }
    | { id: "routes" }
    | { id: "routes_detail"; route: "A" | "B" | "C" }
    | { id: "track" }
    | { id: "track_qr" }
    | { id: "risk" }
    | { id: "profile" }
    | { id: "wallet" };

export type Navigate = (s: Screen) => void;
