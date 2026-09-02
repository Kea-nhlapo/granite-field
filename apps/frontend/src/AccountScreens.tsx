import { Badge, SectionCard, TopBar } from "./ui";

export function RiskScreen({
    onBack,
    internal,
}: {
    onBack: () => void;
    internal: boolean;
}) {
    if (!internal) {
        return (
            <>
                <TopBar title="Your Trust Profile" onBack={onBack} />
                <div
                    className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                    style={{ background: "var(--surface)" }}
                >
                    <div
                        className="rounded-2xl p-6 text-center"
                        style={{ background: "var(--navy)" }}
                    >
                        <p className="text-white/60 text-xs uppercase tracking-wider mb-1">
                            Trust Score
                        </p>
                        <p
                            className="text-6xl font-bold font-mono-data"
                            style={{ color: "var(--yellow)" }}
                        >
                            91
                        </p>
                        <p className="text-white/60 text-xs mt-1">
                            out of 100 · +4 this month
                        </p>
                    </div>
                    <SectionCard>
                        {[
                            {
                                icon: "✓",
                                label: "Verified Business",
                                desc: "CIPC registration confirmed",
                                ok: true,
                            },
                            {
                                icon: "✓",
                                label: "Payment Record",
                                desc: "No failed supplier payments",
                                ok: true,
                            },
                            {
                                icon: "✓",
                                label: "QR Compliance",
                                desc: "All 47 deliveries scanned",
                                ok: true,
                            },
                            {
                                icon: "⚠",
                                label: "Pending Dispute",
                                desc: "1 dispute in review",
                                ok: false,
                            },
                        ].map((h, i, arr) => (
                            <div
                                key={h.label}
                                className={`flex gap-3 p-4 items-center ${i < arr.length - 1 ? "border-b border-gray-50" : ""}`}
                            >
                                <div
                                    className="w-8 h-8 rounded-full flex items-center justify-center shrink-0 text-sm font-bold text-white"
                                    style={{
                                        background: h.ok
                                            ? "var(--success)"
                                            : "var(--warning)",
                                    }}
                                >
                                    {h.icon}
                                </div>
                                <div>
                                    <p className="text-sm font-semibold">
                                        {h.label}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-0.5">
                                        {h.desc}
                                    </p>
                                </div>
                            </div>
                        ))}
                    </SectionCard>
                </div>
            </>
        );
    }

    return (
        <>
            <TopBar
                title="Risk & Fraud"
                onBack={onBack}
                action={<Badge label="Internal" color="navy" />}
            />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--surface)" }}
            >
                <div className="grid grid-cols-2 gap-3">
                    {[
                        {
                            label: "Fraud Signals",
                            value: "8",
                            sub: "4 high severity",
                            warn: true,
                        },
                        {
                            label: "Open Claims",
                            value: "2",
                            sub: "R34,000 exposure",
                            warn: true,
                        },
                        {
                            label: "Active Shipments",
                            value: "14",
                            sub: "Across 3 regions",
                            warn: false,
                        },
                        {
                            label: "Network Risk",
                            value: "34",
                            sub: "Low — stable",
                            warn: false,
                        },
                    ].map((s) => (
                        <SectionCard
                            key={s.label}
                            className={`p-3.5 ${s.warn ? "border-l-4" : ""}`}
                            style={
                                s.warn
                                    ? { borderLeftColor: "var(--error)" }
                                    : undefined
                            }
                        >
                            <p className="text-2xl font-bold font-mono-data">
                                {s.value}
                            </p>
                            <p className="text-xs font-semibold mt-0.5">
                                {s.label}
                            </p>
                            <p className="text-xs text-gray-400">{s.sub}</p>
                        </SectionCard>
                    ))}
                </div>
                <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                        Fraud Signals
                    </p>
                    <SectionCard>
                        {[
                            {
                                signal: "Driver phone changed 3× in 30 days",
                                severity: "high",
                                trips: 1,
                            },
                            {
                                signal: "QR scanned from unexpected location",
                                severity: "high",
                                trips: 1,
                            },
                            {
                                signal: "Invoice price variance > 15%",
                                severity: "medium",
                                trips: 2,
                            },
                            {
                                signal: "Route deviation unexplained",
                                severity: "low",
                                trips: 4,
                            },
                        ].map((f, i, arr) => (
                            <div
                                key={i}
                                className={`flex gap-3 p-3.5 items-center ${i < arr.length - 1 ? "border-b border-gray-50" : ""}`}
                            >
                                <div
                                    className="w-2.5 h-2.5 rounded-full shrink-0"
                                    style={{
                                        background:
                                            f.severity === "high"
                                                ? "var(--error)"
                                                : f.severity === "medium"
                                                  ? "var(--warning)"
                                                  : "var(--yellow)",
                                    }}
                                />
                                <div className="flex-1">
                                    <p className="text-xs font-medium">
                                        {f.signal}
                                    </p>
                                    <p className="text-xs text-gray-400">
                                        {f.trips} trip{f.trips > 1 ? "s" : ""}{" "}
                                        affected
                                    </p>
                                </div>
                                <Badge
                                    label={f.severity}
                                    color={
                                        f.severity === "high"
                                            ? "red"
                                            : f.severity === "medium"
                                              ? "amber"
                                              : "grey"
                                    }
                                />
                            </div>
                        ))}
                    </SectionCard>
                </div>
                <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                        Hijacking Risk by Region
                    </p>
                    <SectionCard className="p-4 space-y-3">
                        {[
                            { r: "Johannesburg South (N1)", v: 72 },
                            { r: "Krugersdorp (N14)", v: 61 },
                            { r: "Durban (N3 South)", v: 44 },
                            { r: "Midrand (N1 North)", v: 28 },
                            { r: "Cape Town (N2)", v: 19 },
                        ].map((x) => (
                            <div key={x.r} className="flex items-center gap-3">
                                <span className="text-xs text-gray-500 flex-1">
                                    {x.r}
                                </span>
                                <div className="w-24 bg-gray-100 rounded-full h-2">
                                    <div
                                        className="h-2 rounded-full"
                                        style={{
                                            width: `${x.v}%`,
                                            background:
                                                x.v > 60
                                                    ? "var(--error)"
                                                    : x.v > 40
                                                      ? "var(--warning)"
                                                      : "var(--success)",
                                        }}
                                    />
                                </div>
                                <span className="text-xs font-mono-data w-5 text-right text-gray-500">
                                    {x.v}
                                </span>
                            </div>
                        ))}
                    </SectionCard>
                </div>
            </div>
        </>
    );
}

export function ProfileScreen({
    onBack,
    internal,
    showInternalToggle = false,
    onSignOut,
}: {
    onBack: () => void;
    internal: boolean;
    showInternalToggle?: boolean;
    onSignOut: () => void;
}) {
    return (
        <>
            <TopBar title="Profile" onBack={onBack} />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--surface)" }}
            >
                <div className="flex flex-col items-center py-6">
                    <div
                        className="w-16 h-16 rounded-full flex items-center justify-center text-xl font-bold text-white mb-3"
                        style={{ background: "var(--blue)" }}
                    >
                        MN
                    </div>
                    <p className="font-semibold">Mama Nkosi Spaza Supply</p>
                    <p className="text-xs text-gray-400 mt-0.5">
                        Soweto, Johannesburg · Member since Jan 2025
                    </p>
                    <div className="mt-2">
                        <Badge label="Trust Score: 91/100" color="green" />
                    </div>
                </div>
                <SectionCard>
                    {showInternalToggle ? (
                        <div className="flex items-center justify-between p-4 border-b border-gray-50">
                            <div>
                                <p className="text-sm font-semibold">
                                    Internal View
                                </p>
                                <p className="text-xs text-gray-400">
                                    Restricted partner dashboards for this role
                                </p>
                            </div>
                            <span
                                className="text-xs font-semibold"
                                style={{ color: "var(--blue)" }}
                            >
                                {internal ? "On" : "Off"}
                            </span>
                        </div>
                    ) : null}
                    {[
                        {
                            label: "Business Registration",
                            value: "CIPC: 2024/003821/07",
                        },
                        { label: "MoMo Wallet", value: "Linked · Active" },
                        { label: "Insurance Class", value: "Standard Cover" },
                    ].map((r, i, arr) => (
                        <div
                            key={r.label}
                            className={`flex items-center justify-between p-4 ${i < arr.length - 1 ? "border-b border-gray-50" : ""}`}
                        >
                            <span className="text-xs text-gray-500">
                                {r.label}
                            </span>
                            <span className="text-xs font-semibold font-mono-data">
                                {r.value}
                            </span>
                        </div>
                    ))}
                </SectionCard>
                <button
                    className="w-full h-10 rounded-lg text-sm font-semibold border"
                    onClick={onSignOut}
                >
                    Sign out
                </button>
                <p className="text-xs text-gray-400 text-center py-2">
                    Powered by{" "}
                    <span
                        className="font-semibold"
                        style={{ color: "var(--blue)" }}
                    >
                        MoMo PSB
                    </span>{" "}
                    · TradeMesh v1.0
                </p>
            </div>
        </>
    );
}
