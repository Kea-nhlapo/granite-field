import type { CSSProperties, ReactNode } from "react";
import type { Tab } from "./types";

export function Badge({
    label,
    color,
}: {
    label: string;
    color: "green" | "amber" | "red" | "blue" | "grey" | "navy";
}) {
    const cls: Record<string, string> = {
        green: "bg-emerald-50 text-emerald-700",
        amber: "bg-amber-50 text-amber-700",
        red: "bg-red-50 text-red-700",
        blue: "bg-blue-50 text-blue-800",
        grey: "bg-gray-100 text-gray-500",
        navy: "text-yellow-300 bg-blue-900",
    };
    return (
        <span
            className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium font-mono-data ${cls[color]}`}
        >
            {label}
        </span>
    );
}

export function PrimaryBtn({
    label,
    onClick,
    disabled,
}: {
    label: string;
    onClick?: () => void;
    disabled?: boolean;
}) {
    return (
        <button
            onClick={onClick}
            disabled={disabled}
            className="flex items-center justify-center w-full h-10 rounded-lg text-sm font-semibold transition-all"
            style={{
                background: disabled ? "var(--inactive)" : "var(--yellow)",
                color: disabled ? "var(--inactive-text)" : "var(--navy)",
                cursor: disabled ? "not-allowed" : "pointer",
            }}
        >
            {label}
        </button>
    );
}

export function SecondaryBtn({
    label,
    onClick,
}: {
    label: string;
    onClick?: () => void;
}) {
    return (
        <button
            onClick={onClick}
            className="flex items-center justify-center w-full h-10 rounded-lg text-sm font-semibold border-2 transition-all"
            style={{
                borderColor: "var(--blue)",
                color: "var(--blue)",
                background: "transparent",
            }}
        >
            {label}
        </button>
    );
}

export function BottomDock({ children }: { children: ReactNode }) {
    return (
        <div
            className="shrink-0 flex flex-col gap-3 bg-white"
            style={{
                padding:
                    "16px 20px calc(16px + env(safe-area-inset-bottom, 0px))",
                boxShadow: "0 -4px 16px rgba(0,0,0,0.06)",
            }}
        >
            {children}
        </div>
    );
}

export function TopBar({
    title,
    onBack,
    action,
}: {
    title: string;
    onBack?: () => void;
    action?: ReactNode;
}) {
    return (
        <div
            className="shrink-0 flex items-center justify-between px-3 gap-2 border-b border-gray-100"
            style={{
                background: "#ffffff",
                height: 156,
            }}
        >
            <div className="flex items-center gap-2 min-w-0 flex-1">
                {onBack ? (
                    <button
                        type="button"
                        aria-label="Back"
                        onClick={onBack}
                        className="w-8 h-8 shrink-0 flex items-center justify-center text-2xl"
                        style={{ color: "var(--navy)" }}
                    >
                        ‹
                    </button>
                ) : null}
                <img
                    src="/trademesh-logo.png"
                    alt="TradeMesh"
                    className="h-[126px] w-auto max-w-[min(360px,78vw)] object-contain object-left shrink-0 origin-left scale-110"
                />
                {title ? (
                    <span
                        className="font-semibold text-sm truncate"
                        style={{ color: "var(--navy)" }}
                    >
                        {title}
                    </span>
                ) : null}
            </div>
            {action && <div className="shrink-0">{action}</div>}
        </div>
    );
}

export function SectionCard({
    children,
    className = "",
    style,
}: {
    children: ReactNode;
    className?: string;
    style?: CSSProperties;
}) {
    return (
        <div
            className={`bg-white rounded-2xl border border-gray-100 ${className}`}
            style={style}
        >
            {children}
        </div>
    );
}

export function Row({
    label,
    value,
    mono,
}: {
    label: string;
    value: string;
    mono?: boolean;
}) {
    return (
        <div className="flex items-center justify-between py-2.5 border-b border-gray-50 last:border-0">
            <span className="text-xs text-gray-500">{label}</span>
            <span
                className={`text-xs font-semibold ${mono ? "font-mono-data" : ""}`}
            >
                {value}
            </span>
        </div>
    );
}

const TABS: { id: Tab; label: string; icon: string }[] = [
    { id: "home", label: "Home", icon: "⊞" },
    { id: "source", label: "Source", icon: "⊕" },
    { id: "orders", label: "Orders", icon: "≡" },
    { id: "routes", label: "Routes", icon: "◎" },
    { id: "track", label: "Track", icon: "◉" },
];

export function TabBar({
    active,
    onTab,
}: {
    active: Tab;
    onTab: (t: Tab) => void;
}) {
    return (
        <div
            className="shrink-0 flex items-center justify-around border-t border-gray-100"
            style={{
                background: "#ffffff",
                paddingBottom: "env(safe-area-inset-bottom, 0px)",
                height: 56,
            }}
        >
            {TABS.map((t) => (
                <button
                    key={t.id}
                    type="button"
                    aria-current={active === t.id ? "page" : undefined}
                    onClick={() => onTab(t.id)}
                    className="flex flex-col items-center gap-0.5 flex-1 h-full justify-center transition-all"
                >
                    <span
                        className="text-base leading-none"
                        style={{
                            color: active === t.id ? "var(--navy)" : "#9ca3af",
                        }}
                    >
                        {t.icon}
                    </span>
                    <span
                        className="text-xs"
                        style={{
                            color: active === t.id ? "var(--navy)" : "#9ca3af",
                            fontWeight: active === t.id ? 600 : 400,
                        }}
                    >
                        {t.label}
                    </span>
                </button>
            ))}
        </div>
    );
}
