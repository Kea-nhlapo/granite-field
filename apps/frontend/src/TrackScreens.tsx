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

export function TrackScreen({ navigate }: { navigate: Navigate }) {
    return (
        <>
            <TopBar title="Live Track" />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--surface)" }}
            >
                <SectionCard
                    className="p-4"
                    style={{ borderLeft: "4px solid var(--yellow)" }}
                >
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="text-xs font-mono-data text-gray-400">
                                SB-2026-9901
                            </p>
                            <p className="text-sm font-semibold mt-0.5">
                                Germiston → Soweto CBD
                            </p>
                        </div>
                        <Badge label="In Transit" color="blue" />
                    </div>
                    <div className="flex items-center gap-2 mt-3">
                        <div className="flex-1 bg-gray-100 rounded-full h-2">
                            <div
                                className="h-2 rounded-full"
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
                    <p className="text-xs text-gray-400 mt-2">
                        ETA 14:30 · Sipho Mthembu · T-JHB-0047
                    </p>
                </SectionCard>
                <SectionCard className="p-4">
                    <p className="text-xs font-semibold text-gray-700 mb-4">
                        Journey Timeline
                    </p>
                    <div className="relative">
                        <div className="absolute left-3 top-3 bottom-3 w-px bg-gray-200" />
                        <div className="space-y-5">
                            {[
                                {
                                    time: "07:30",
                                    label: "Collected at Germiston Market",
                                    done: true,
                                },
                                {
                                    time: "09:14",
                                    label: "Departed — all cargo secured",
                                    done: true,
                                },
                                {
                                    time: "10:00",
                                    label: "Checkpoint: N12/R21 Junction",
                                    done: true,
                                },
                                {
                                    time: "14:30",
                                    label: "Estimated arrival: Soweto CBD",
                                    done: false,
                                },
                                {
                                    time: "~15:00",
                                    label: "Delivery QR scan required",
                                    done: false,
                                },
                            ].map((e, i) => (
                                <div key={i} className="flex gap-3 items-start">
                                    <div
                                        className={`w-6 h-6 rounded-full border-2 shrink-0 flex items-center justify-center z-10 ${e.done ? "bg-green-500 border-green-500" : "bg-white border-gray-300"}`}
                                    >
                                        {e.done && (
                                            <span
                                                className="text-white"
                                                style={{ fontSize: 10 }}
                                            >
                                                ✓
                                            </span>
                                        )}
                                    </div>
                                    <div className="flex-1">
                                        <p
                                            className={`text-xs font-medium ${e.done ? "text-gray-800" : "text-gray-400"}`}
                                        >
                                            {e.label}
                                        </p>
                                        <p className="text-xs text-gray-400 mt-0.5 font-mono-data">
                                            {e.time}
                                        </p>
                                    </div>
                                </div>
                            ))}
                        </div>
                    </div>
                </SectionCard>
                <button
                    onClick={() => navigate({ id: "track_qr" })}
                    className="w-full bg-white rounded-2xl p-4 border border-gray-100 flex items-center gap-4"
                >
                    <div
                        className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
                        style={{ background: "var(--navy)" }}
                    >
                        <span className="text-2xl">⬛</span>
                    </div>
                    <div className="flex-1 text-left">
                        <p className="text-sm font-semibold">
                            View Delivery QR Code
                        </p>
                        <p className="text-xs text-gray-400 mt-0.5">
                            Tap to open · Driver scans on arrival
                        </p>
                    </div>
                    <span className="text-gray-300 text-xl">›</span>
                </button>
                <SectionCard className="p-4">
                    <p className="text-xs font-semibold text-gray-700 mb-3">
                        Trip Trust Record
                    </p>
                    {[
                        { e: "Collection QR verified", t: "07:31", ok: true },
                        {
                            e: "Route deviation detected",
                            t: "08:02",
                            ok: false,
                        },
                        {
                            e: "Deviation resolved — fuel stop N12",
                            t: "08:08",
                            ok: true,
                        },
                    ].map((r, i) => (
                        <div key={i} className="flex gap-2.5 text-xs">
                            <span
                                style={{
                                    color: r.ok
                                        ? "var(--success)"
                                        : "var(--warning)",
                                }}
                            >
                                {r.ok ? "✓" : "⚠"}
                            </span>
                            <span className="flex-1 text-gray-600">{r.e}</span>
                            <span className="font-mono-data text-gray-400">
                                {r.t}
                            </span>
                        </div>
                    ))}
                </SectionCard>
            </div>
        </>
    );
}

export function QRScreen({ onBack }: { onBack: () => void }) {
    const [verified, setVerified] = useState(false);
    const pattern = [
        0, 1, 3, 5, 6, 7, 8, 10, 12, 14, 15, 16, 18, 20, 21, 22, 24, 26, 28, 29,
        30, 32, 34, 35, 36, 37, 40, 42, 43, 45, 47, 48,
    ];

    return (
        <>
            <TopBar title="Delivery QR" onBack={onBack} />
            <div
                className="flex-1 phone-scroll overflow-y-auto flex flex-col items-center justify-center p-6 gap-6"
                style={{ background: "var(--surface)" }}
            >
                <div
                    className="p-6 rounded-3xl"
                    style={{ background: "var(--navy)" }}
                >
                    <div
                        className="grid gap-1"
                        style={{ gridTemplateColumns: "repeat(7, 1fr)" }}
                    >
                        {Array.from({ length: 49 }).map((_, i) => {
                            const isEdge =
                                i < 7 || i > 41 || i % 7 === 0 || i % 7 === 6;
                            return (
                                <div
                                    key={i}
                                    className="w-7 h-7 rounded-sm"
                                    style={{
                                        background:
                                            isEdge || pattern.includes(i)
                                                ? "var(--yellow)"
                                                : "transparent",
                                    }}
                                />
                            );
                        })}
                    </div>
                    <p
                        className="text-center font-mono-data text-xs mt-3"
                        style={{ color: "var(--yellow)" }}
                    >
                        SB-2026-9901
                    </p>
                </div>
                <div className="w-full space-y-2 text-center">
                    <p className="text-sm font-semibold">
                        Show this to the driver on arrival
                    </p>
                    <p className="text-xs text-gray-500">
                        14 Mofolo Crescent, Soweto
                    </p>
                    <p className="text-xs font-mono-data text-gray-400">
                        Valid until today 18:00 · Single-use
                    </p>
                </div>
                {verified && (
                    <div
                        className="w-full p-4 rounded-2xl border border-green-200 text-center"
                        style={{ background: "#f0fdf4" }}
                    >
                        <p className="text-sm font-semibold text-green-700">
                            ✓ Delivery confirmed
                        </p>
                        <p className="text-xs text-green-600 mt-1 font-mono-data">
                            15:02:44 SAST · REF-F9K2
                        </p>
                    </div>
                )}
            </div>
            <BottomDock>
                {!verified ? (
                    <PrimaryBtn
                        label="Simulate Driver Scan"
                        onClick={() => setVerified(true)}
                    />
                ) : (
                    <SecondaryBtn label="Back to Tracking" onClick={onBack} />
                )}
            </BottomDock>
        </>
    );
}
