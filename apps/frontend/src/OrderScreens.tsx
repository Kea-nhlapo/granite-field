import { useState } from "react";
import type { Navigate } from "./types";
import {
    Badge,
    BottomDock,
    PrimaryBtn,
    SecondaryBtn,
    SectionCard,
    TopBar,
} from "./ui";

type LineStatus =
    "ok" | "qty_mismatch" | "price_mismatch" | "approved" | "rejected";
interface Line {
    id: string;
    product: string;
    qty: number;
    price: number;
    invQty: number;
    invPrice: number;
    status: LineStatus;
}

export function OrdersScreen({ navigate }: { navigate: Navigate }) {
    return (
        <>
            <TopBar
                title="Orders"
                action={
                    <button
                        onClick={() => navigate({ id: "orders_invoice" })}
                        className="text-xs font-semibold px-3 h-8 rounded-lg"
                        style={{
                            background: "var(--yellow)",
                            color: "var(--navy)",
                        }}
                    >
                        + Upload
                    </button>
                }
            />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--surface)" }}
            >
                <div
                    className="p-3 rounded-xl border-l-4 flex gap-3"
                    style={{
                        background: "white",
                        borderLeftColor: "var(--warning)",
                    }}
                >
                    <span className="text-xl">⚠️</span>
                    <div className="flex-1">
                        <p className="text-xs font-semibold text-amber-700">
                            Invoice needs review
                        </p>
                        <p className="text-xs text-gray-600 mt-0.5">
                            Thabo Distributors · 3 mismatches flagged
                        </p>
                    </div>
                    <button
                        onClick={() => navigate({ id: "orders_invoice" })}
                        className="text-xs font-semibold self-center"
                        style={{ color: "var(--blue)" }}
                    >
                        Review
                    </button>
                </div>
                <div>
                    <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                        All Orders
                    </p>
                    <SectionCard>
                        {[
                            {
                                id: "ORD-2026-9012",
                                sup: "Thabo Distributors",
                                total: "R14,480",
                                date: "2 Sep",
                                status: "Reviewing",
                                statusColor: "amber" as const,
                            },
                            {
                                id: "ORD-2026-8901",
                                sup: "Bulk SA Wholesale",
                                total: "R8,340",
                                date: "28 Aug",
                                status: "Delivered",
                                statusColor: "green" as const,
                            },
                            {
                                id: "ORD-2026-8771",
                                sup: "Thabo Distributors",
                                total: "R12,700",
                                date: "21 Aug",
                                status: "Confirmed",
                                statusColor: "blue" as const,
                            },
                            {
                                id: "ORD-2026-8611",
                                sup: "Nkosi Foods SA",
                                total: "R6,900",
                                date: "14 Aug",
                                status: "Delivered",
                                statusColor: "green" as const,
                            },
                        ].map((o, i, arr) => (
                            <div
                                key={o.id}
                                className={`flex items-center gap-3 px-4 py-3.5 ${i < arr.length - 1 ? "border-b border-gray-50" : ""}`}
                            >
                                <div className="flex-1 min-w-0">
                                    <p className="text-xs font-mono-data text-gray-400">
                                        {o.id}
                                    </p>
                                    <p className="text-sm font-semibold mt-0.5">
                                        {o.sup}
                                    </p>
                                    <p className="text-xs text-gray-400">
                                        {o.date}
                                    </p>
                                </div>
                                <div className="text-right">
                                    <p className="text-sm font-mono-data font-bold">
                                        {o.total}
                                    </p>
                                    <div className="mt-1">
                                        <Badge
                                            label={o.status}
                                            color={o.statusColor}
                                        />
                                    </div>
                                </div>
                            </div>
                        ))}
                    </SectionCard>
                </div>
            </div>
        </>
    );
}

export function InvoiceScreen({ onBack }: { onBack: () => void }) {
    const [step, setStep] = useState<"upload" | "parsing" | "review">("upload");
    const [lines, setLines] = useState<Line[]>([
        {
            id: "L1",
            product: "Sunflower Oil 5L",
            qty: 50,
            price: 89.0,
            invQty: 50,
            invPrice: 89.0,
            status: "ok",
        },
        {
            id: "L2",
            product: "Maize Meal 10kg",
            qty: 30,
            price: 112.5,
            invQty: 25,
            invPrice: 112.5,
            status: "qty_mismatch",
        },
        {
            id: "L3",
            product: "Washing Powder 3kg",
            qty: 20,
            price: 67.0,
            invQty: 20,
            invPrice: 71.5,
            status: "price_mismatch",
        },
        {
            id: "L4",
            product: "Tinned Pilchards",
            qty: 100,
            price: 24.9,
            invQty: 100,
            invPrice: 24.9,
            status: "ok",
        },
        {
            id: "L5",
            product: "Long-life Milk 1L",
            qty: 60,
            price: 18.5,
            invQty: 60,
            invPrice: 18.5,
            status: "ok",
        },
    ]);
    const flagged = lines.filter(
        (l) => l.status === "qty_mismatch" || l.status === "price_mismatch",
    );
    const approve = (id: string) =>
        setLines((ls) =>
            ls.map((l) => (l.id === id ? { ...l, status: "approved" } : l)),
        );
    const reject = (id: string) =>
        setLines((ls) =>
            ls.map((l) => (l.id === id ? { ...l, status: "rejected" } : l)),
        );

    return (
        <>
            <TopBar title="Invoice Auto-Fill" onBack={onBack} />
            <div
                className="flex-1 phone-scroll overflow-y-auto"
                style={{ background: "var(--surface)" }}
            >
                {step === "upload" && (
                    <div className="p-4 space-y-4">
                        <button
                            onClick={() => {
                                setStep("parsing");
                                setTimeout(() => setStep("review"), 1800);
                            }}
                            className="w-full bg-white border-2 border-dashed border-gray-200 rounded-2xl p-8 flex flex-col items-center gap-3"
                        >
                            <span className="text-4xl">📄</span>
                            <p className="text-sm font-semibold text-gray-700">
                                Tap to upload invoice
                            </p>
                            <p className="text-xs text-gray-400">
                                PDF, JPG, or PNG
                            </p>
                        </button>
                        <SectionCard className="p-4">
                            <p className="text-xs font-semibold text-gray-600 mb-1">
                                How it works
                            </p>
                            <div className="space-y-2 mt-2">
                                {[
                                    "Supplier uploads or emails invoice",
                                    "System reads and extracts all line items",
                                    "Mismatches vs your order are flagged",
                                    "You approve or reject each line",
                                ].map((s, i) => (
                                    <div
                                        key={i}
                                        className="flex gap-2.5 items-start"
                                    >
                                        <div
                                            className="w-5 h-5 rounded-full shrink-0 flex items-center justify-center text-white font-bold"
                                            style={{
                                                background: "var(--blue)",
                                                fontSize: 10,
                                            }}
                                        >
                                            {i + 1}
                                        </div>
                                        <p className="text-xs text-gray-600 pt-0.5">
                                            {s}
                                        </p>
                                    </div>
                                ))}
                            </div>
                        </SectionCard>
                    </div>
                )}
                {step === "parsing" && (
                    <div className="p-4 flex flex-col items-center justify-center min-h-48 gap-4">
                        <div className="text-4xl animate-pulse">⏳</div>
                        <p className="text-sm font-semibold text-gray-700">
                            Reading invoice…
                        </p>
                        <p className="text-xs text-gray-400">
                            Extracting line items and prices
                        </p>
                    </div>
                )}
                {step === "review" && (
                    <div className="p-4 space-y-4 pb-32">
                        <div className="flex gap-2">
                            <SectionCard className="flex-1 p-3 text-center">
                                <p className="text-xs text-gray-400">
                                    Invoice total
                                </p>
                                <p className="text-sm font-mono-data font-bold mt-0.5">
                                    R14,480
                                </p>
                            </SectionCard>
                            <SectionCard className="flex-1 p-3 text-center">
                                <p className="text-xs text-gray-400">
                                    Lines parsed
                                </p>
                                <p className="text-sm font-mono-data font-bold mt-0.5">
                                    5
                                </p>
                            </SectionCard>
                            <div
                                className="flex-1 p-3 rounded-2xl text-center"
                                style={{
                                    background:
                                        flagged.length > 0
                                            ? "#fff7ed"
                                            : "#f0fdf4",
                                    border: `1px solid ${flagged.length > 0 ? "#fed7aa" : "#bbf7d0"}`,
                                }}
                            >
                                <p
                                    className="text-xs"
                                    style={{
                                        color:
                                            flagged.length > 0
                                                ? "#9a3412"
                                                : "#166534",
                                    }}
                                >
                                    Mismatches
                                </p>
                                <p
                                    className="text-sm font-mono-data font-bold mt-0.5"
                                    style={{
                                        color:
                                            flagged.length > 0
                                                ? "#c2410c"
                                                : "#15803d",
                                    }}
                                >
                                    {flagged.length}
                                </p>
                            </div>
                        </div>
                        <div>
                            <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                                Order Lines
                            </p>
                            <div className="space-y-2">
                                {lines.map((l) => {
                                    const isFlagged =
                                        l.status === "qty_mismatch" ||
                                        l.status === "price_mismatch";
                                    return (
                                        <SectionCard
                                            key={l.id}
                                            className={`p-3.5 ${isFlagged ? "border-l-4" : ""} ${l.status === "rejected" ? "opacity-40" : ""}`}
                                            style={
                                                isFlagged
                                                    ? {
                                                          borderLeftColor:
                                                              "var(--warning)",
                                                      }
                                                    : undefined
                                            }
                                        >
                                            <div className="flex items-start justify-between gap-2">
                                                <div className="flex-1 min-w-0">
                                                    <p className="text-sm font-semibold truncate">
                                                        {l.product}
                                                    </p>
                                                    <div className="mt-1.5 space-y-0.5 text-xs text-gray-500">
                                                        <div className="flex gap-2">
                                                            <span>
                                                                Your qty:{" "}
                                                                <span className="font-mono-data font-medium">
                                                                    {l.qty}
                                                                </span>
                                                            </span>
                                                            <span
                                                                className={`font-mono-data font-medium ${l.status === "qty_mismatch" ? "text-amber-600" : ""}`}
                                                            >
                                                                Invoice:{" "}
                                                                {l.invQty}
                                                                {l.status ===
                                                                "qty_mismatch"
                                                                    ? " ⚠"
                                                                    : ""}
                                                            </span>
                                                        </div>
                                                        <div className="flex gap-2">
                                                            <span>
                                                                Your price:{" "}
                                                                <span className="font-mono-data">
                                                                    R
                                                                    {l.price.toFixed(
                                                                        2,
                                                                    )}
                                                                </span>
                                                            </span>
                                                            <span
                                                                className={`font-mono-data ${l.status === "price_mismatch" ? "text-amber-600 font-medium" : ""}`}
                                                            >
                                                                Invoice: R
                                                                {l.invPrice.toFixed(
                                                                    2,
                                                                )}
                                                                {l.status ===
                                                                "price_mismatch"
                                                                    ? " ⚠"
                                                                    : ""}
                                                            </span>
                                                        </div>
                                                    </div>
                                                </div>
                                                <div className="shrink-0">
                                                    {l.status === "ok" && (
                                                        <Badge
                                                            label="Match"
                                                            color="green"
                                                        />
                                                    )}
                                                    {l.status ===
                                                        "qty_mismatch" && (
                                                        <Badge
                                                            label="Qty ≠"
                                                            color="amber"
                                                        />
                                                    )}
                                                    {l.status ===
                                                        "price_mismatch" && (
                                                        <Badge
                                                            label="Price ≠"
                                                            color="amber"
                                                        />
                                                    )}
                                                    {l.status ===
                                                        "approved" && (
                                                        <Badge
                                                            label="Accepted"
                                                            color="blue"
                                                        />
                                                    )}
                                                    {l.status ===
                                                        "rejected" && (
                                                        <Badge
                                                            label="Rejected"
                                                            color="red"
                                                        />
                                                    )}
                                                </div>
                                            </div>
                                            {isFlagged && (
                                                <div className="flex gap-2 mt-3">
                                                    <button
                                                        onClick={() =>
                                                            approve(l.id)
                                                        }
                                                        className="flex-1 h-9 rounded-xl text-xs font-semibold"
                                                        style={{
                                                            background:
                                                                "var(--success)",
                                                            color: "white",
                                                        }}
                                                    >
                                                        Accept
                                                    </button>
                                                    <button
                                                        onClick={() =>
                                                            reject(l.id)
                                                        }
                                                        className="flex-1 h-9 rounded-xl text-xs font-semibold border-2"
                                                        style={{
                                                            borderColor:
                                                                "var(--error)",
                                                            color: "var(--error)",
                                                        }}
                                                    >
                                                        Reject
                                                    </button>
                                                </div>
                                            )}
                                        </SectionCard>
                                    );
                                })}
                            </div>
                        </div>
                    </div>
                )}
            </div>
            {step === "review" && (
                <BottomDock>
                    <PrimaryBtn
                        label={
                            flagged.length > 0
                                ? `Resolve ${flagged.length} mismatch${flagged.length > 1 ? "es" : ""} first`
                                : "Confirm Order"
                        }
                        disabled={flagged.length > 0}
                    />
                    {flagged.length === 0 && (
                        <SecondaryBtn label="Dispute Invoice" />
                    )}
                </BottomDock>
            )}
        </>
    );
}
