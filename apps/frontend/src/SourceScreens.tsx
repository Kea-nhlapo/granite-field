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

export function SourceScreen({ navigate }: { navigate: Navigate }) {
    const [search, setSearch] = useState("");
    const products = [
        {
            name: "Sunflower Oil 5L",
            sku: "OIL-SFW-5L",
            supplier: "Thabo Distributors",
            price: "R89.00",
            stock: "High",
        },
        {
            name: "Maize Meal 10kg (Iwisa)",
            sku: "GRN-MZM-10",
            supplier: "Bulk SA Wholesale",
            price: "R112.50",
            stock: "Medium",
        },
        {
            name: "Washing Powder 3kg",
            sku: "HLD-WSH-3K",
            supplier: "Nkosi Foods SA",
            price: "R67.00",
            stock: "High",
        },
        {
            name: "Tinned Pilchards 400g",
            sku: "TIN-PLH-400",
            supplier: "Thabo Distributors",
            price: "R24.90",
            stock: "Low",
        },
        {
            name: "Long-life Milk 1L (Clover)",
            sku: "DRY-MLK-1L",
            supplier: "Bulk SA Wholesale",
            price: "R18.50",
            stock: "High",
        },
    ];

    return (
        <>
            <TopBar title="Source Stock" />
            <div
                className="flex-1 phone-scroll overflow-y-auto"
                style={{ background: "var(--surface)" }}
            >
                <div className="p-4 space-y-4">
                    <input
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        placeholder="Search products or suppliers…"
                        aria-label="Search products or suppliers"
                        className="w-full text-sm border border-gray-200 rounded-xl px-3 h-10 outline-none bg-white focus:border-blue-400"
                    />
                    <div>
                        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                            Nearby Trusted Suppliers
                        </p>
                        <div className="flex gap-2 overflow-x-auto pb-1 -mx-1 px-1">
                            {[
                                {
                                    name: "Thabo Distributors",
                                    dist: "3.2km",
                                    rating: 4.8,
                                },
                                {
                                    name: "Bulk SA Wholesale",
                                    dist: "7.1km",
                                    rating: 4.5,
                                },
                                {
                                    name: "Joburg Fresh Market",
                                    dist: "12km",
                                    rating: 4.2,
                                },
                            ].map((s) => (
                                <div
                                    key={s.name}
                                    className="shrink-0 bg-white rounded-xl p-3 border border-gray-100 w-36"
                                >
                                    <div
                                        className="w-8 h-8 rounded-full flex items-center justify-center text-sm mb-2 font-bold"
                                        style={{
                                            background: "var(--blue)",
                                            color: "white",
                                        }}
                                    >
                                        {s.name[0]}
                                    </div>
                                    <p className="text-xs font-semibold leading-snug">
                                        {s.name}
                                    </p>
                                    <p className="text-xs text-gray-400 mt-0.5">
                                        ★ {s.rating} · {s.dist}
                                    </p>
                                    <button
                                        className="mt-2 text-xs font-medium px-2 py-1 rounded-lg"
                                        style={{
                                            background: "var(--yellow)",
                                            color: "var(--navy)",
                                        }}
                                    >
                                        Invite
                                    </button>
                                </div>
                            ))}
                        </div>
                    </div>
                    <div>
                        <p className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2.5">
                            Products
                        </p>
                        <SectionCard>
                            {products
                                .filter(
                                    (p) =>
                                        !search ||
                                        p.name
                                            .toLowerCase()
                                            .includes(search.toLowerCase()),
                                )
                                .map((p, i, arr) => (
                                    <div
                                        key={p.sku}
                                        className={`flex items-center gap-3 px-4 py-3 ${i < arr.length - 1 ? "border-b border-gray-50" : ""}`}
                                    >
                                        <div className="flex-1 min-w-0">
                                            <p className="text-sm font-semibold text-gray-800 truncate">
                                                {p.name}
                                            </p>
                                            <p className="text-xs text-gray-400 mt-0.5">
                                                {p.supplier}
                                            </p>
                                        </div>
                                        <div className="text-right shrink-0">
                                            <p className="text-sm font-mono-data font-bold">
                                                {p.price}
                                            </p>
                                            <Badge
                                                label={p.stock}
                                                color={
                                                    p.stock === "High"
                                                        ? "green"
                                                        : p.stock === "Medium"
                                                          ? "amber"
                                                          : "red"
                                                }
                                            />
                                        </div>
                                        <button
                                            className="shrink-0 w-8 h-8 rounded-full flex items-center justify-center text-lg font-bold"
                                            style={{
                                                background: "var(--yellow)",
                                                color: "var(--navy)",
                                            }}
                                        >
                                            +
                                        </button>
                                    </div>
                                ))}
                        </SectionCard>
                    </div>
                </div>
            </div>
            <BottomDock>
                <PrimaryBtn
                    label="Create stock request"
                    onClick={() => navigate({ id: "procurement" })}
                />
                <SecondaryBtn
                    label="Generate Supplier Invite"
                    onClick={() => navigate({ id: "source_invite" })}
                />
            </BottomDock>
        </>
    );
}

export function SupplierInviteScreen({ onBack }: { onBack: () => void }) {
    const [generated, setGenerated] = useState(false);
    const [copied, setCopied] = useState(false);
    const link = "stockbridge.co.za/supplier/SB-INV-7XK9M2";

    return (
        <>
            <TopBar title="Invite Supplier" onBack={onBack} />
            <div
                className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--surface)" }}
            >
                <SectionCard className="p-4">
                    <p className="text-sm font-semibold mb-1">
                        No signup needed
                    </p>
                    <p className="text-xs text-gray-500 leading-relaxed">
                        Share this link with your supplier. They can upload an
                        invoice directly — no account creation required. The
                        system reads their document and fills your order
                        automatically.
                    </p>
                </SectionCard>
                <SectionCard className="p-4 space-y-3">
                    <input
                        className="w-full text-sm border border-gray-200 rounded-xl px-3 h-10 outline-none bg-white"
                        placeholder="Supplier business name"
                    />
                    <input
                        className="w-full text-sm border border-gray-200 rounded-xl px-3 h-10 outline-none bg-white"
                        placeholder="WhatsApp number or email"
                    />
                </SectionCard>
                {generated && (
                    <SectionCard className="p-4">
                        <p className="text-xs font-semibold text-green-700 mb-2">
                            ✓ Link ready
                        </p>
                        <div className="flex gap-2 items-center">
                            <code className="flex-1 text-xs font-mono-data bg-gray-50 border border-gray-200 rounded-lg px-2.5 py-2 truncate">
                                {link}
                            </code>
                            <button
                                onClick={() => {
                                    setCopied(true);
                                    setTimeout(() => setCopied(false), 2000);
                                }}
                                className="shrink-0 px-3 h-9 rounded-lg text-xs font-semibold"
                                style={{
                                    background: copied
                                        ? "var(--success)"
                                        : "var(--blue)",
                                    color: "white",
                                }}
                            >
                                {copied ? "Copied!" : "Copy"}
                            </button>
                        </div>
                        <p className="text-xs text-gray-400 mt-2">
                            Expires in 72 hours · Single-use link
                        </p>
                    </SectionCard>
                )}
            </div>
            <BottomDock>
                {!generated ? (
                    <PrimaryBtn
                        label="Generate Link"
                        onClick={() => setGenerated(true)}
                    />
                ) : (
                    <>
                        <PrimaryBtn label="Share via WhatsApp" />
                        <SecondaryBtn
                            label="Copy Link"
                            onClick={() => setCopied(true)}
                        />
                    </>
                )}
            </BottomDock>
        </>
    );
}
