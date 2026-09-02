import type { Navigate } from "./types";
import {
    Badge,
    BottomDock,
    PrimaryBtn,
    Row,
    SecondaryBtn,
    SectionCard,
    TopBar,
} from "./ui";

export function RoutesScreen({ navigate }: { navigate: Navigate }) {
    return (
        <>
            <TopBar
                title="Route Planner"
                action={
                    <button
                        className="text-xs font-semibold"
                        style={{ color: "var(--blue)" }}
                        onClick={() => navigate({ id: "logistics" })}
                    >
                        Consolidation
                    </button>
                }
            />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--surface)" }}
            >
                <SectionCard className="p-4">
                    <div className="flex items-center justify-between mb-3">
                        <p className="text-sm font-semibold">Soweto Cluster</p>
                        <Badge label="4 businesses" color="blue" />
                    </div>
                    {[
                        { name: "Mama Nkosi Spaza", weight: "710kg" },
                        { name: "Phindile's Spaza", weight: "240kg" },
                        { name: "Vusi Hardware Store", weight: "400kg" },
                        { name: "Mama D Salon Supplies", weight: "60kg" },
                    ].map((b) => (
                        <div
                            key={b.name}
                            className="flex items-center gap-2 py-1.5 border-b border-gray-50 last:border-0"
                        >
                            <div
                                className="w-2 h-2 rounded-full shrink-0"
                                style={{ background: "var(--yellow)" }}
                            />
                            <span className="flex-1 text-xs font-medium">
                                {b.name}
                            </span>
                            <span className="text-xs font-mono-data text-gray-500">
                                {b.weight}
                            </span>
                        </div>
                    ))}
                    <p className="text-xs text-gray-500 mt-3 pt-3 border-t border-gray-100">
                        Total:{" "}
                        <span className="font-mono-data font-semibold">
                            1,410 kg
                        </span>{" "}
                        · Est. savings:{" "}
                        <span
                            style={{ color: "var(--success)" }}
                            className="font-semibold"
                        >
                            R420–R680
                        </span>
                    </p>
                </SectionCard>
                <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                        Matched Truck
                    </p>
                    <SectionCard className="p-4">
                        <div className="flex items-center justify-between mb-2">
                            <span
                                className="text-sm font-mono-data font-bold"
                                style={{ color: "var(--blue)" }}
                            >
                                T-JHB-0047
                            </span>
                            <span className="text-xs text-gray-400">
                                ★ 4.9 · 312 trips
                            </span>
                        </div>
                        <p className="text-sm font-semibold">Sipho Mthembu</p>
                        <p className="text-xs text-gray-400">
                            Germiston → Cape Town · Spare: 590kg
                        </p>
                        <div className="mt-3 flex items-center gap-2">
                            <div className="flex-1 bg-gray-100 rounded-full h-2">
                                <div
                                    className="h-2 rounded-full"
                                    style={{
                                        width: "36%",
                                        background: "var(--blue)",
                                    }}
                                />
                            </div>
                            <span className="text-xs font-mono-data text-gray-500">
                                820/2000kg
                            </span>
                        </div>
                    </SectionCard>
                </div>
                <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                        Route Options
                    </p>
                    <div className="space-y-2">
                        {[
                            {
                                route: "A" as const,
                                name: "N1 + R21 Bypass",
                                time: "2h 14m",
                                score: 87,
                                tag: "Recommended",
                            },
                            {
                                route: "B" as const,
                                name: "N14 Toll Route",
                                time: "2h 41m",
                                score: 64,
                                tag: "High risk",
                            },
                            {
                                route: "C" as const,
                                name: "N3 Direct",
                                time: "1h 58m",
                                score: 72,
                                tag: "Heavy traffic",
                            },
                        ].map((r) => (
                            <button
                                key={r.route}
                                onClick={() =>
                                    navigate({
                                        id: "routes_detail",
                                        route: r.route,
                                    })
                                }
                                className="w-full flex items-center gap-3 bg-white rounded-2xl p-3.5 border border-gray-100"
                            >
                                <div
                                    className="w-9 h-9 rounded-xl flex items-center justify-center font-bold text-sm shrink-0"
                                    style={{
                                        background: "var(--navy)",
                                        color: "var(--yellow)",
                                    }}
                                >
                                    {r.route}
                                </div>
                                <div className="flex-1 text-left">
                                    <p className="text-sm font-semibold">
                                        {r.name}
                                    </p>
                                    <p className="text-xs text-gray-400">
                                        {r.time} · {r.tag}
                                    </p>
                                </div>
                                <p
                                    className="text-lg font-bold font-mono-data"
                                    style={{
                                        color:
                                            r.score >= 80
                                                ? "var(--success)"
                                                : r.score >= 65
                                                  ? "var(--warning)"
                                                  : "var(--error)",
                                    }}
                                >
                                    {r.score}
                                </p>
                                <span className="text-gray-300 text-lg">›</span>
                            </button>
                        ))}
                    </div>
                </div>
            </div>
            <BottomDock>
                <PrimaryBtn
                    label="Confirm Route A & Assign Truck"
                    onClick={() =>
                        navigate({ id: "routes_detail", route: "A" })
                    }
                />
            </BottomDock>
        </>
    );
}

function FactorRow({
    label,
    score,
    invert,
}: {
    label: string;
    score: number;
    invert?: boolean;
}) {
    const goodScore = invert ? 100 - score : score;
    const color =
        goodScore >= 70
            ? "var(--success)"
            : goodScore >= 45
              ? "var(--warning)"
              : "var(--error)";
    return (
        <div className="py-2.5 border-b border-gray-50 last:border-0">
            <div className="flex items-center justify-between mb-1.5">
                <span className="text-xs text-gray-600">{label}</span>
                <span className="text-xs font-mono-data font-bold">
                    {score}/100
                </span>
            </div>
            <div className="bg-gray-100 rounded-full h-2">
                <div
                    className="h-2 rounded-full"
                    style={{ width: `${score}%`, background: color }}
                />
            </div>
        </div>
    );
}

export function RouteDetailScreen({
    route,
    onBack,
}: {
    route: "A" | "B" | "C";
    onBack: () => void;
}) {
    const data = {
        A: {
            name: "N1 + R21 Bypass",
            time: "2h 14m",
            fuel: "68L",
            score: 87,
            hijack: 22,
            traffic: 65,
            coverage: 94,
            road: 88,
            desc: "Avoids the N1 hijacking hotspot near Johannesburg South via the R21 bypass. MTN signal strong throughout.",
        },
        B: {
            name: "N14 Toll Route",
            time: "2h 41m",
            fuel: "74L",
            score: 64,
            hijack: 58,
            traffic: 28,
            coverage: 81,
            road: 72,
            desc: "Elevated hijacking risk through Krugersdorp stretch. Lower traffic but not recommended for high-value cargo.",
        },
        C: {
            name: "N3 Direct Highway",
            time: "1h 58m",
            fuel: "71L",
            score: 72,
            hijack: 41,
            traffic: 82,
            coverage: 88,
            road: 80,
            desc: "Fastest route but traffic load spikes near Heidelberg interchange midday.",
        },
    }[route];

    return (
        <>
            <TopBar title={`Route ${route}`} onBack={onBack} />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4 pb-28"
                style={{ background: "var(--surface)" }}
            >
                <SectionCard className="p-4">
                    <div className="flex items-center justify-between mb-1">
                        <p className="text-sm font-semibold">{data.name}</p>
                        <div
                            className="text-2xl font-bold font-mono-data"
                            style={{
                                color:
                                    data.score >= 80
                                        ? "var(--success)"
                                        : data.score >= 65
                                          ? "var(--warning)"
                                          : "var(--error)",
                            }}
                        >
                            {data.score}
                        </div>
                    </div>
                    <p className="text-xs text-gray-400">
                        {data.time} · {data.fuel} fuel estimate
                    </p>
                    <p className="text-xs text-gray-600 mt-3 leading-relaxed">
                        {data.desc}
                    </p>
                </SectionCard>
                <SectionCard className="px-4 py-2">
                    <p className="text-xs font-semibold text-gray-700 pt-2 pb-1">
                        Routing Factor Breakdown
                    </p>
                    <FactorRow
                        label="Hijacking Risk (lower is better)"
                        score={data.hijack}
                        invert
                    />
                    <FactorRow
                        label="Traffic Load (lower is better)"
                        score={data.traffic}
                        invert
                    />
                    <FactorRow label="MTN Coverage" score={data.coverage} />
                    <FactorRow label="Road Condition" score={data.road} />
                    <FactorRow
                        label="Fuel Efficiency"
                        score={Math.round(
                            100 - (parseInt(data.fuel, 10) - 60) * 3,
                        )}
                    />
                </SectionCard>
                <SectionCard className="p-4">
                    <p className="text-xs font-semibold text-gray-700 mb-3">
                        Cargo Notes
                    </p>
                    <Row label="Cargo type" value="General Grocery" />
                    <Row label="Refrigeration" value="Not required" />
                    <Row label="Insurance class" value="Standard" />
                    <Row label="Estimated departure" value="Today 16:00" />
                </SectionCard>
            </div>
            <BottomDock>
                <PrimaryBtn label={`Confirm Route ${route}`} />
                <SecondaryBtn label="Choose Different Route" onClick={onBack} />
            </BottomDock>
        </>
    );
}
