import type { CSSProperties, ReactNode } from "react";
import type { Tab } from "./types";
import {
    HomeIcon,
    BoxIcon,
    DocumentTextIcon,
    RouteIcon,
    RadarIcon,
    ChevronLeftIcon,
    AlertTriangleIcon,
    CheckmarkIcon,
    DismissIcon,
    ShieldCheckmarkIcon,
} from "./icons";

export type BadgeColor =
    | "green"
    | "amber"
    | "red"
    | "blue"
    | "grey"
    | "navy"
    | "yellow"
    | "success"
    | "warning"
    | "danger"
    | "brand"
    | "neutral";

export function Badge({
    label,
    color,
    icon,
}: {
    label: string;
    color: BadgeColor;
    icon?: ReactNode;
}) {
    const styles: Record<
        BadgeColor,
        { bg: string; text: string; border: string; dot: string }
    > = {
        green: {
            bg: "#E3FCEF",
            text: "#00875A",
            border: "#A3E7C9",
            dot: "#00875A",
        },
        success: {
            bg: "#E3FCEF",
            text: "#00875A",
            border: "#A3E7C9",
            dot: "#00875A",
        },
        amber: {
            bg: "#FFF3E0",
            text: "#D96B00",
            border: "#FFE0B2",
            dot: "#F57C00",
        },
        warning: {
            bg: "#FFF3E0",
            text: "#D96B00",
            border: "#FFE0B2",
            dot: "#F57C00",
        },
        red: {
            bg: "#FDE8E8",
            text: "#D32F2F",
            border: "#F8B4B4",
            dot: "#D32F2F",
        },
        danger: {
            bg: "#FDE8E8",
            text: "#D32F2F",
            border: "#F8B4B4",
            dot: "#D32F2F",
        },
        blue: {
            bg: "#EBF3FC",
            text: "#003E85",
            border: "#C7E0F4",
            dot: "#003E85",
        },
        brand: {
            bg: "#EBF3FC",
            text: "#003E85",
            border: "#C7E0F4",
            dot: "#003E85",
        },
        grey: {
            bg: "#F3F4F6",
            text: "#595959",
            border: "#E5E7EB",
            dot: "#8E8E93",
        },
        neutral: {
            bg: "#F3F4F6",
            text: "#595959",
            border: "#E5E7EB",
            dot: "#8E8E93",
        },
        navy: {
            bg: "#002B49",
            text: "#FFCC00",
            border: "#001F35",
            dot: "#FFCC00",
        },
        yellow: {
            bg: "#FFF9D6",
            text: "#7A6000",
            border: "#FFE082",
            dot: "#FFCC00",
        },
    };

    const c = styles[color] || styles.neutral;

    return (
        <span
            className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-tight transition-colors"
            style={{
                backgroundColor: c.bg,
                color: c.text,
                border: `1px solid ${c.border}`,
            }}
        >
            {icon ? (
                <span className="shrink-0">{icon}</span>
            ) : (
                <span
                    className="w-1.5 h-1.5 rounded-full shrink-0"
                    style={{ backgroundColor: c.dot }}
                />
            )}
            <span className="truncate">{label}</span>
        </span>
    );
}

/* Primary Button per MoMo Guidelines:
   - Height: 40px
   - Background: MoMo Yellow (#FFCC00)
   - Color: Dark Navy (#002B49) / #000000
   - Radius: 8px
   - Weight: 500 (Medium)
   - Inactive: #CCCCCC / #8E8E93 */
export function PrimaryBtn({
    label,
    onClick,
    disabled,
    icon,
    type = "button",
    className = "",
}: {
    label: string;
    onClick?: () => void;
    disabled?: boolean;
    icon?: ReactNode;
    type?: "button" | "submit";
    className?: string;
}) {
    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled}
            className={`flex items-center justify-center gap-2 w-full h-10 px-4 rounded-lg text-sm font-semibold transition-all active:scale-[0.99] ${className}`}
            style={{
                backgroundColor: disabled
                    ? "var(--momo-grey-inactive, #CCCCCC)"
                    : "var(--momo-yellow, #FFCC00)",
                color: disabled
                    ? "var(--momo-grey-inactive-text, #8E8E93)"
                    : "var(--momo-navy, #002B49)",
                border: "1px solid transparent",
                cursor: disabled ? "not-allowed" : "pointer",
                borderRadius: "var(--fluent-radius-md, 8px)",
                boxShadow: disabled ? "none" : "0 1px 3px rgba(0,0,0,0.08)",
            }}
        >
            {icon && <span className="shrink-0">{icon}</span>}
            <span>{label}</span>
        </button>
    );
}

/* Primary Blue Button alternative per MoMo Guidelines:
   - Background: MoMo Blue (#003E85)
   - Color: White */
export function PrimaryBlueBtn({
    label,
    onClick,
    disabled,
    icon,
    className = "",
}: {
    label: string;
    onClick?: () => void;
    disabled?: boolean;
    icon?: ReactNode;
    className?: string;
}) {
    return (
        <button
            onClick={onClick}
            disabled={disabled}
            className={`flex items-center justify-center gap-2 w-full h-10 px-4 rounded-lg text-sm font-semibold transition-all active:scale-[0.99] text-white ${className}`}
            style={{
                backgroundColor: disabled
                    ? "var(--momo-grey-inactive, #CCCCCC)"
                    : "var(--momo-blue, #003E85)",
                color: disabled
                    ? "var(--momo-grey-inactive-text, #8E8E93)"
                    : "#FFFFFF",
                border: "1px solid transparent",
                cursor: disabled ? "not-allowed" : "pointer",
                borderRadius: "var(--fluent-radius-md, 8px)",
            }}
        >
            {icon && <span className="shrink-0">{icon}</span>}
            <span>{label}</span>
        </button>
    );
}

/* Secondary Button per MoMo Guidelines:
   - Outlined MoMo Blue (#003E85)
   - Height: 40px, Radius: 8px */
export function SecondaryBtn({
    label,
    onClick,
    disabled,
    icon,
    className = "",
}: {
    label: string;
    onClick?: () => void;
    disabled?: boolean;
    icon?: ReactNode;
    className?: string;
}) {
    return (
        <button
            onClick={onClick}
            disabled={disabled}
            className={`flex items-center justify-center gap-2 w-full h-10 px-4 rounded-lg text-sm font-semibold transition-all bg-transparent hover:bg-[#EBF3FC] active:bg-[#D9EAFB] ${className}`}
            style={{
                borderColor: disabled
                    ? "var(--momo-grey-inactive, #CCCCCC)"
                    : "var(--momo-blue, #003E85)",
                borderWidth: "1.5px",
                color: disabled
                    ? "var(--momo-grey-inactive-text, #8E8E93)"
                    : "var(--momo-blue, #003E85)",
                cursor: disabled ? "not-allowed" : "pointer",
                borderRadius: "var(--fluent-radius-md, 8px)",
            }}
        >
            {icon && <span className="shrink-0">{icon}</span>}
            <span>{label}</span>
        </button>
    );
}

export function SubtleBtn({
    label,
    onClick,
    icon,
    className = "",
}: {
    label: string;
    onClick?: () => void;
    icon?: ReactNode;
    className?: string;
}) {
    return (
        <button
            onClick={onClick}
            className={`flex items-center justify-center gap-1.5 px-2.5 py-1.5 rounded-lg text-xs font-semibold text-[#003E85] hover:bg-[#EBF3FC] active:bg-[#D9EAFB] transition-colors ${className}`}
            style={{ borderRadius: "var(--fluent-radius-md, 8px)" }}
        >
            {icon && <span className="shrink-0">{icon}</span>}
            <span>{label}</span>
        </button>
    );
}

export function BottomDock({ children }: { children: ReactNode }) {
    return (
        <div
            className="shrink-0 flex flex-col gap-2.5 bg-white border-t"
            style={{
                borderColor: "var(--fluent-stroke-divider, #E5E7EB)",
                padding:
                    "16px 20px calc(16px + env(safe-area-inset-bottom, 0px)) 20px",
                boxShadow: "0 -4px 16px rgba(0, 0, 0, 0.05)",
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
    title?: string;
    onBack?: () => void;
    action?: ReactNode;
}) {
    return (
        <div
            className="shrink-0 flex items-center justify-between gap-2 px-4 z-20 bg-white border-b"
            style={{
                borderColor: "var(--fluent-stroke-divider, #E5E7EB)",
                minHeight: 56,
                height: 56,
            }}
        >
            <div className="flex items-center gap-2 min-w-0 flex-1">
                {onBack ? (
                    <button
                        onClick={onBack}
                        className="w-9 h-9 -ml-1 rounded-lg flex items-center justify-center text-[#FFCC00] hover:bg-[#FFF9D6] active:bg-[#FFE082] transition-colors shrink-0"
                        aria-label="Back"
                    >
                        <ChevronLeftIcon size={22} />
                    </button>
                ) : null}

                {title ? (
                    <h1 className="text-[17px] font-bold text-[#1A1A1A] truncate">
                        {title}
                    </h1>
                ) : (
                    <img
                        src="/trademesh-logo.png"
                        alt="TradeMesh"
                        className="h-11 w-auto object-contain shrink-0"
                        style={{ height: 44 }}
                    />
                )}
            </div>

            {action && (
                <div className="shrink-0 flex items-center gap-2">{action}</div>
            )}
        </div>
    );
}

export function SectionCard({
    children,
    className = "",
    style,
    onClick,
}: {
    children: ReactNode;
    className?: string;
    style?: CSSProperties;
    onClick?: () => void;
}) {
    return (
        <div
            onClick={onClick}
            className={`bg-white border rounded-xl transition-all ${
                onClick
                    ? "cursor-pointer hover:border-[#003E85] active:bg-[#F8F9FA]"
                    : ""
            } ${className}`}
            style={{
                borderColor: "var(--fluent-stroke-card, #E5E7EB)",
                borderRadius: "var(--fluent-radius-lg, 12px)",
                boxShadow:
                    "var(--fluent-depth-card, 0 2px 8px rgba(0, 0, 0, 0.05))",
                ...style,
            }}
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
    value: string | ReactNode;
    mono?: boolean;
}) {
    return (
        <div
            className="flex items-center justify-between py-2 border-b last:border-0"
            style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
        >
            <span className="text-xs text-[#595959]">{label}</span>
            <span
                className={`text-xs font-semibold text-[#1A1A1A] ${
                    mono ? "font-fluent-mono" : ""
                }`}
            >
                {value}
            </span>
        </div>
    );
}

export function PersonaCoin({
    initials,
    size = 32,
    status = "available",
    onClick,
}: {
    initials: string;
    size?: number;
    status?: "available" | "busy" | "away" | "offline";
    onClick?: () => void;
}) {
    const statusColors = {
        available: "#00875A",
        busy: "#D32F2F",
        away: "#F57C00",
        offline: "#8E8E93",
    };

    const className =
        "relative flex items-center justify-center font-bold rounded-full select-none";
    const style = {
        width: size,
        height: size,
        backgroundColor: "var(--momo-blue, #003E85)",
        color: "#FFFFFF",
        fontSize: Math.round(size * 0.38),
        border: "2px solid #FFFFFF",
        boxShadow: "0 1px 3px rgba(0,0,0,0.12)",
    };
    const content = (
        <>
            {initials}
            <span
                aria-hidden="true"
                className="absolute bottom-0 right-0 rounded-full border-2 border-white"
                style={{
                    width: Math.max(8, Math.round(size * 0.28)),
                    height: Math.max(8, Math.round(size * 0.28)),
                    backgroundColor: statusColors[status],
                }}
            />
        </>
    );

    if (onClick) {
        return (
            <button
                type="button"
                aria-label="Open account and preferences"
                onClick={onClick}
                className={`${className} cursor-pointer hover:opacity-90 active:scale-95`}
                style={style}
            >
                {content}
            </button>
        );
    }

    return (
        <div className={className} style={style}>
            {content}
        </div>
    );
}

export function MessageBar({
    intent = "info",
    children,
    action,
}: {
    intent?: "info" | "warning" | "error" | "success";
    children: ReactNode;
    action?: ReactNode;
}) {
    const cfg = {
        info: {
            bg: "#EBF3FC",
            border: "#C7E0F4",
            stripe: "#003E85",
            icon: <ShieldCheckmarkIcon size={16} className="text-[#003E85]" />,
        },
        warning: {
            bg: "#FFF3E0",
            border: "#FFE0B2",
            stripe: "#F57C00",
            icon: <AlertTriangleIcon size={16} className="text-[#F57C00]" />,
        },
        error: {
            bg: "#FDE8E8",
            border: "#F8B4B4",
            stripe: "#D32F2F",
            icon: <DismissIcon size={16} className="text-[#D32F2F]" />,
        },
        success: {
            bg: "#E3FCEF",
            border: "#A3E7C9",
            stripe: "#00875A",
            icon: <CheckmarkIcon size={16} className="text-[#00875A]" />,
        },
    }[intent];

    return (
        <div
            className="p-3 rounded-lg border flex items-start gap-2.5 transition-all text-xs"
            style={{
                backgroundColor: cfg.bg,
                borderColor: cfg.border,
                borderLeftWidth: "4px",
                borderLeftColor: cfg.stripe,
            }}
        >
            <div className="shrink-0 mt-0.5">{cfg.icon}</div>
            <div className="flex-1 text-[#1A1A1A] leading-relaxed">
                {children}
            </div>
            {action && <div className="shrink-0">{action}</div>}
        </div>
    );
}

const TABS: {
    id: Tab;
    label: string;
    icon: (props: { size?: number }) => ReactNode;
}[] = [
    { id: "home", label: "Home", icon: (p) => <HomeIcon {...p} /> },
    { id: "source", label: "Source", icon: (p) => <BoxIcon {...p} /> },
    { id: "orders", label: "Orders", icon: (p) => <DocumentTextIcon {...p} /> },
    { id: "routes", label: "Routes", icon: (p) => <RouteIcon {...p} /> },
    { id: "track", label: "Track", icon: (p) => <RadarIcon {...p} /> },
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
            className="shrink-0 flex items-center justify-around select-none z-20 bg-white border-t"
            style={{
                borderColor: "var(--fluent-stroke-divider, #E5E7EB)",
                paddingBottom: "env(safe-area-inset-bottom, 0px)",
                paddingTop: 6,
                height: 60,
            }}
        >
            {TABS.map((t) => {
                const isActive = active === t.id;
                return (
                    <button
                        key={t.id}
                        onClick={() => onTab(t.id)}
                        className="relative flex flex-col items-center gap-1 flex-1 h-full justify-start transition-all group"
                        style={{ color: isActive ? "#1A1A1A" : "#595959" }}
                    >
                        <span
                            className="flex items-center justify-center rounded-full transition-all group-active:scale-95"
                            style={{
                                width: 34,
                                height: 34,
                                backgroundColor: isActive
                                    ? "var(--momo-yellow, #FFCC00)"
                                    : "transparent",
                                boxShadow: isActive
                                    ? "0 2px 6px rgba(0,0,0,0.15)"
                                    : "none",
                                color: isActive ? "#1A1A1A" : "#595959",
                            }}
                        >
                            {t.icon({ size: 18 })}
                        </span>
                        <span
                            className="text-[11px] leading-none"
                            style={{ fontWeight: isActive ? 700 : 500 }}
                        >
                            {t.label}
                        </span>
                    </button>
                );
            })}
        </div>
    );
}
