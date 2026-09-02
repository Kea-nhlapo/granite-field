import type { Navigate, Screen } from "./types";
import { Badge, SectionCard, TopBar } from "./ui";

export function HomeScreen({ navigate }: { navigate: Navigate }) {
    return (
        <>
            <TopBar
                title=""
                action={
                    <button
                        onClick={() => navigate({ id: "profile" })}
                        className="w-8 h-8 rounded-full flex items-center justify-center text-xs font-bold"
                        style={{ background: "var(--blue)", color: "white" }}
                    >
                        MN
                    </button>
                }
            />
            <div
                className="flex-1 phone-scroll overflow-y-auto"
                style={{ background: "var(--surface)" }}
            >
                <div className="px-4 pt-4 pb-5 bg-white">
                    <p
                        className="text-xs mb-0.5"
                        style={{ color: "var(--yellow-hover)" }}
                    >
                        Good morning
                    </p>
                    <h1
                        className="font-semibold text-lg font-display leading-tight"
                        style={{ color: "var(--navy)" }}
                    >
                        Mama Nkosi
                        <br />
                        Spaza Supply
                    </h1>
                    <p className="text-xs mt-0.5 text-gray-500">
                        Soweto, Johannesburg
                    </p>
                    <div
                        className="mt-4 inline-flex items-center gap-2 px-3 py-1.5 rounded-full"
                        style={{
                            background: "rgba(0,135,90,0.2)",
                            border: "1px solid rgba(0,135,90,0.4)",
                        }}
                    >
                        <span
                            className="w-4 h-4 rounded-full flex items-center justify-center text-xs"
                            style={{
                                background: "var(--success)",
                                color: "white",
                            }}
                        >
                            ✓
                        </span>
                        <span
                            className="text-xs font-semibold"
                            style={{ color: "#4ade80" }}
                        >
                            Trust Score: 91/100
                        </span>
                    </div>
                </div>

                <div
                    className="flex gap-0 border-b border-gray-100"
                    style={{ background: "white" }}
                >
                    {[
                        { label: "Active Orders", value: "7" },
                        { label: "Deliveries", value: "3" },
                        { label: "Savings", value: "R2,340" },
                    ].map((s, i) => (
                        <div
                            key={s.label}
                            className={`flex-1 text-center py-3 ${i < 2 ? "border-r border-gray-100" : ""}`}
                        >
                            <p
                                className="text-base font-bold font-mono-data"
                                style={{ color: "var(--navy)" }}
                            >
                                {s.value}
                            </p>
                            <p className="text-xs text-gray-400 mt-0.5">
                                {s.label}
                            </p>
                        </div>
                    ))}
                </div>

                <div className="p-4 space-y-4">
                    <SectionCard>
                        <div className="px-4 pt-3 pb-1 flex items-center justify-between">
                            <span className="text-xs font-semibold text-gray-700">
                                Active Shipment
                            </span>
                            <button
                                onClick={() => navigate({ id: "track" })}
                                className="text-xs font-medium"
                                style={{ color: "var(--blue)" }}
                            >
                                Track →
                            </button>
                        </div>
                        <div className="px-4 pb-4">
                            <p className="text-xs font-mono-data text-gray-400 mt-1">
                                SB-2026-9901
                            </p>
                            <p className="text-sm font-semibold mt-0.5">
                                Germiston → Soweto CBD
                            </p>
                            <p className="text-xs text-gray-400">
                                4 businesses · Sipho M. · ETA 14:30
                            </p>
                            <div className="flex items-center gap-2 mt-3">
                                <div className="flex-1 bg-gray-100 rounded-full h-1.5">
                                    <div
                                        className="h-1.5 rounded-full"
                                        style={{
                                            width: "68%",
                                            background: "var(--yellow)",
                                        }}
                                    />
                                </div>
                                <span className="text-xs font-mono-data text-gray-500">
                                    68%
                                </span>
                            </div>
                            <div className="flex gap-2 mt-2">
                                <Badge label="In transit" color="blue" />
                                <Badge label="67 km left" color="grey" />
                            </div>
                        </div>
                    </SectionCard>

                    <div>
                        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                            Quick Actions
                        </p>
                        <div className="grid grid-cols-2 gap-3">
                            {(
                                [
                                    {
                                        label: "Request Stock",
                                        icon: "📦",
                                        desc: "Browse & request products",
                                        screen: { id: "procurement" } as Screen,
                                    },
                                    {
                                        label: "Upload Invoice",
                                        icon: "📄",
                                        desc: "Auto-fill from supplier doc",
                                        screen: {
                                            id: "orders_invoice",
                                        } as Screen,
                                    },
                                    {
                                        label: "Plan Route",
                                        icon: "🗺",
                                        desc: "Group orders, match trucks",
                                        screen: { id: "routes" } as Screen,
                                    },
                                    {
                                        label: "Risk Dashboard",
                                        icon: "🛡",
                                        desc: "Trust & fraud signals",
                                        screen: { id: "risk" } as Screen,
                                    },
                                ] as const
                            ).map((q) => (
                                <button
                                    key={q.label}
                                    type="button"
                                    aria-label={q.label}
                                    onClick={() => navigate(q.screen)}
                                    className="bg-white rounded-2xl p-3.5 text-left border border-gray-100 active:scale-95 transition-transform"
                                >
                                    <span className="text-xl">{q.icon}</span>
                                    <p className="text-sm font-semibold mt-1.5">
                                        {q.label}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-0.5 leading-snug">
                                        {q.desc}
                                    </p>
                                </button>
                            ))}
                        </div>
                    </div>

                    <SectionCard>
                        <div className="px-4 pt-3 pb-1">
                            <span className="text-xs font-semibold text-gray-700">
                                Recent Activity
                            </span>
                        </div>
                        <div className="px-4 pb-3 space-y-3">
                            {[
                                {
                                    t: "09:14",
                                    e: "Invoice from Nkosi Foods — 3 mismatches flagged",
                                    w: true,
                                },
                                {
                                    t: "08:52",
                                    e: "Truck T-JHB-0047 matched for Soweto cluster",
                                    w: false,
                                },
                                {
                                    t: "08:30",
                                    e: "Supplier Thabo Distributors accepted invite",
                                    w: false,
                                },
                            ].map((a, i) => (
                                <div
                                    key={i}
                                    className="flex gap-3 pt-2 border-t border-gray-50"
                                >
                                    <div
                                        className="w-1.5 h-1.5 rounded-full mt-1.5 shrink-0"
                                        style={{
                                            background: a.w
                                                ? "var(--warning)"
                                                : "var(--success)",
                                        }}
                                    />
                                    <div>
                                        <p className="text-xs text-gray-700 leading-snug">
                                            {a.e}
                                        </p>
                                        <p className="text-xs text-gray-400 mt-0.5 font-mono-data">
                                            {a.t}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </SectionCard>
                </div>
            </div>
        </>
    );
}
