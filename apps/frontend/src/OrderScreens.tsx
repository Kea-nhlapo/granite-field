import { useRef, useState } from "react";
import { FileWarning } from "lucide-react";
import type { Navigate } from "./types";
import {
    Badge,
    BottomDock,
    MessageBar,
    PrimaryBtn,
    SecondaryBtn,
    SectionCard,
    SubtleBtn,
    TopBar,
} from "./ui";
import {
    ArrowUploadIcon,
    CheckmarkIcon,
    DocumentTextIcon,
    PlusIcon,
} from "./icons";
import { EscrowPadlockCard, type EscrowStatus } from "./EscrowPadlockCard";
import {
    parseInvoicePdf,
    type ParsedInvoiceLine,
} from "./shared/pdfInvoiceParser";

/** Simulated timeline standing in for the real RequestToPay → GetTransactionStatus poll. */
const LOCK_TIMELINE: { status: EscrowStatus; after: number }[] = [
    { status: "LOCK_REQUESTED", after: 0 },
    { status: "LOCK_PENDING", after: 900 },
    { status: "LOCKED", after: 2400 },
];

/** Simulated timeline standing in for the real Transfer → GetTransactionStatus poll. */
const RELEASE_TIMELINE: { status: EscrowStatus; after: number }[] = [
    { status: "RELEASE_REQUESTED", after: 0 },
    { status: "RELEASE_PENDING", after: 800 },
    { status: "RELEASED", after: 2000 },
];

export function OrdersScreen({ navigate }: { navigate: Navigate }) {
    return (
        <>
            <TopBar
                title="Purchase Orders"
                action={
                    <button
                        onClick={() => navigate({ id: "orders_invoice" })}
                        className="flex items-center gap-1.5 h-8 px-3 rounded-lg text-xs font-semibold transition-all hover:bg-[#00326B] active:scale-95 shadow-xs"
                        style={{
                            backgroundColor: "var(--momo-navy, #002B49)",
                            color: "#FFFFFF",
                        }}
                    >
                        <PlusIcon size={14} />
                        <span>Upload</span>
                    </button>
                }
            />

            <div
                className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
            >
                {/* Warning Notice */}
                <MessageBar
                    intent="warning"
                    action={
                        <SubtleBtn
                            label="Review"
                            onClick={() => navigate({ id: "orders_invoice" })}
                        />
                    }
                >
                    <div className="app-caption leading-relaxed">
                        <span className="app-caption-strong text-[#002B49]">
                            Invoice review required:
                        </span>{" "}
                        Thabo Distributors invoice contains 3 price/quantity
                        variances against agreed rate card.
                    </div>
                </MessageBar>

                <div>
                    <div className="flex items-center justify-between mb-2">
                        <p className="app-overline">Recent Purchase Orders</p>
                        <span className="app-micro text-[#8E8E93]">
                            4 active
                        </span>
                    </div>

                    <SectionCard>
                        {[
                            {
                                id: "ORD-2026-9012",
                                sup: "Thabo Distributors",
                                total: "R14,480.00",
                                date: "2 Sep 2026",
                                status: "Pending Review",
                                statusColor: "warning" as const,
                            },
                            {
                                id: "ORD-2026-8901",
                                sup: "Bulk SA Wholesale",
                                total: "R8,340.00",
                                date: "28 Aug 2026",
                                status: "Delivered",
                                statusColor: "success" as const,
                            },
                            {
                                id: "ORD-2026-8771",
                                sup: "Thabo Distributors",
                                total: "R12,700.00",
                                date: "21 Aug 2026",
                                status: "Confirmed",
                                statusColor: "brand" as const,
                            },
                            {
                                id: "ORD-2026-8611",
                                sup: "Nkosi Foods SA",
                                total: "R6,900.00",
                                date: "14 Aug 2026",
                                status: "Delivered",
                                statusColor: "success" as const,
                            },
                        ].map((o, i, arr) => (
                            <div
                                key={o.id}
                                className={`flex items-center gap-3 px-4 py-3.5 ${i < arr.length - 1 ? "border-b" : ""}`}
                                style={{
                                    borderColor:
                                        "var(--fluent-stroke-divider, #E5E7EB)",
                                }}
                            >
                                <div className="w-8 h-8 rounded-lg bg-[#EBF3FC] flex items-center justify-center shrink-0">
                                    <DocumentTextIcon
                                        size={16}
                                        className="text-[#003E85]"
                                    />
                                </div>
                                <div className="flex-1 min-w-0">
                                    <div className="flex items-center gap-2">
                                        <span className="app-micro text-[#595959]">
                                            {o.id}
                                        </span>
                                        <span className="app-micro text-[#8E8E93]">
                                            • {o.date}
                                        </span>
                                    </div>
                                    <p className="app-heading truncate mt-0.5">
                                        {o.sup}
                                    </p>
                                </div>
                                <div className="text-right shrink-0">
                                    <p className="app-metric">{o.total}</p>
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
    const [step, setStep] = useState<
        "upload" | "parsing" | "review" | "unparsed" | "error" | "escrow"
    >("upload");
    const [escrowStatus, setEscrowStatus] =
        useState<EscrowStatus>("LOCK_REQUESTED");
    const [fileName, setFileName] = useState("");
    const [lines, setLines] = useState<ParsedInvoiceLine[]>([]);
    const [rawLines, setRawLines] = useState<string[]>([]);
    const [invoiceNumber, setInvoiceNumber] = useState<string>("");
    const [extractedTotal, setExtractedTotal] = useState<number | undefined>(
        undefined,
    );
    const fileInputRef = useRef<HTMLInputElement>(null);

    const computedTotal = lines.reduce((sum, l) => sum + l.lineTotal, 0);
    const invoiceTotal = extractedTotal ?? computedTotal;

    function runEscrowLifecycle() {
        setStep("escrow");
        setEscrowStatus("LOCK_REQUESTED");
        for (const entry of LOCK_TIMELINE) {
            setTimeout(() => setEscrowStatus(entry.status), entry.after);
        }
    }

    function releaseEscrow() {
        for (const entry of RELEASE_TIMELINE) {
            setTimeout(() => setEscrowStatus(entry.status), entry.after);
        }
    }

    async function handleFile(file: File) {
        if (file.type !== "application/pdf") {
            setStep("error");
            return;
        }
        setFileName(file.name);
        setStep("parsing");
        try {
            const parsed = await parseInvoicePdf(file);
            setLines(parsed.lines);
            setRawLines(parsed.rawLines);
            setInvoiceNumber(parsed.invoiceNumber ?? "");
            setExtractedTotal(parsed.total);
            setStep(parsed.lines.length > 0 ? "review" : "unparsed");
        } catch (err) {
            console.warn("PDF parsing failed", err);
            setStep("error");
        }
    }

    return (
        <>
            <TopBar title="Invoice Reconciliation" onBack={onBack} />
            <div
                className="flex-1 fluent-scroll overflow-y-auto"
                style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
            >
                {step === "upload" && (
                    <div className="p-4 space-y-4">
                        {/* Upload Dropzone */}
                        <input
                            ref={fileInputRef}
                            type="file"
                            accept="application/pdf"
                            className="sr-only"
                            onChange={(e) => {
                                const file = e.target.files?.[0];
                                e.target.value = "";
                                if (file) void handleFile(file);
                            }}
                        />
                        <button
                            onClick={() => fileInputRef.current?.click()}
                            className="w-full bg-white border-2 border-dashed border-[#D1D5DB] hover:border-[#003E85] hover:bg-[#F8F9FA] cursor-pointer rounded-xl p-8 flex flex-col items-center justify-center gap-3 transition-all"
                        >
                            <div className="w-12 h-12 rounded-full bg-[#EBF3FC] flex items-center justify-center text-[#003E85]">
                                <ArrowUploadIcon size={24} />
                            </div>
                            <div className="text-center">
                                <p className="app-heading">
                                    Click to upload invoice document
                                </p>
                                <p className="app-caption text-[#595959] mt-1">
                                    PDF only — text is read and formatted
                                    directly in your browser
                                </p>
                            </div>
                        </button>

                        {/* Reconciliation Process Card */}
                        <SectionCard className="p-4">
                            <p className="app-heading mb-3">
                                How Invoice Parsing Works
                            </p>
                            <div className="space-y-3">
                                {[
                                    "Supplier uploads a digital PDF invoice",
                                    "TradeMesh reads the PDF's text and reconstructs each line",
                                    "Line items, quantities and unit prices are detected automatically",
                                    "You review the extracted lines before locking payment in escrow",
                                ].map((s, i) => (
                                    <div
                                        key={i}
                                        className="flex gap-2.5 items-start"
                                    >
                                        <div className="w-5 h-5 rounded-full bg-[#EBF3FC] text-[#003E85] font-bold flex items-center justify-center text-[11px] shrink-0">
                                            {i + 1}
                                        </div>
                                        <p className="app-body pt-0.5 leading-snug">
                                            {s}
                                        </p>
                                    </div>
                                ))}
                            </div>
                        </SectionCard>
                    </div>
                )}

                {step === "parsing" && (
                    <div className="p-8 flex flex-col items-center justify-center min-h-[300px] gap-4 text-center">
                        <div className="w-10 h-10 border-3 border-[#003E85] border-t-[#FFCC00] rounded-full animate-spin" />
                        <div>
                            <p className="app-heading">Reading {fileName}...</p>
                            <p className="app-caption text-[#595959] mt-1">
                                Extracting text and formatting line items
                            </p>
                        </div>
                    </div>
                )}

                {step === "error" && (
                    <div className="p-8 flex flex-col items-center justify-center min-h-[300px] gap-4 text-center">
                        <div className="w-12 h-12 rounded-full bg-[#FDE8E8] flex items-center justify-center text-[#D32F2F]">
                            <FileWarning size={22} strokeWidth={1.75} />
                        </div>
                        <div>
                            <p className="app-heading">
                                Couldn't read that file
                            </p>
                            <p className="app-caption text-[#595959] mt-1">
                                Only PDF invoices are supported, and the file
                                must not be corrupted or password-protected.
                            </p>
                        </div>
                        <SecondaryBtn
                            label="Try Another File"
                            onClick={() => setStep("upload")}
                        />
                    </div>
                )}

                {step === "unparsed" && (
                    <div className="p-4 space-y-4 pb-8">
                        <div className="p-6 flex flex-col items-center justify-center gap-3 text-center">
                            <div className="w-12 h-12 rounded-full bg-[#FFF3E0] flex items-center justify-center text-[#F57C00]">
                                <FileWarning size={22} strokeWidth={1.75} />
                            </div>
                            <div>
                                <p className="app-heading">
                                    No line items detected
                                </p>
                                <p className="app-caption text-[#595959] mt-1">
                                    The PDF's text was read, but its layout
                                    didn't match a recognizable line-item
                                    pattern. Here's the raw text extracted from{" "}
                                    {fileName}:
                                </p>
                            </div>
                        </div>
                        <SectionCard className="p-3.5 max-h-64 overflow-y-auto fluent-scroll">
                            {rawLines.length > 0 ? (
                                rawLines.map((line, i) => (
                                    <p
                                        key={i}
                                        className="app-caption text-[#595959] leading-relaxed"
                                    >
                                        {line}
                                    </p>
                                ))
                            ) : (
                                <p className="app-caption text-[#8E8E93]">
                                    No extractable text found in this PDF.
                                </p>
                            )}
                        </SectionCard>
                        <SecondaryBtn
                            label="Try Another File"
                            onClick={() => setStep("upload")}
                        />
                    </div>
                )}

                {step === "review" && (
                    <div className="p-4 space-y-4 pb-32">
                        {/* KPI Summary Tiles */}
                        <div className="grid grid-cols-2 gap-2">
                            <SectionCard className="p-3 text-center">
                                <p className="app-micro text-[#595959]">
                                    Invoice Total
                                </p>
                                <p className="app-metric mt-0.5">
                                    R
                                    {invoiceTotal.toLocaleString("en-ZA", {
                                        minimumFractionDigits: 2,
                                        maximumFractionDigits: 2,
                                    })}
                                </p>
                            </SectionCard>
                            <SectionCard className="p-3 text-center">
                                <p className="app-micro text-[#595959]">
                                    Lines Extracted
                                </p>
                                <p className="app-metric mt-0.5">
                                    {lines.length}{" "}
                                    {lines.length === 1 ? "Item" : "Items"}
                                </p>
                            </SectionCard>
                        </div>

                        {/* Extracted Line Items — read straight from the uploaded PDF's text */}
                        <div>
                            <p className="app-overline mb-2">
                                Line Items Extracted from{" "}
                                {fileName || "Uploaded PDF"}
                                {invoiceNumber && ` • ${invoiceNumber}`}
                            </p>
                            <div className="space-y-2.5">
                                {lines.map((l, i) => (
                                    <SectionCard key={i} className="p-3.5">
                                        <div className="flex items-start justify-between gap-2">
                                            <div className="flex-1 min-w-0">
                                                <p className="app-heading truncate">
                                                    {l.description}
                                                </p>
                                                <p className="app-caption text-[#595959] mt-1">
                                                    Qty {l.qty} × R
                                                    {l.unitPrice.toFixed(2)}
                                                </p>
                                            </div>
                                            <p className="app-metric shrink-0">
                                                R{l.lineTotal.toFixed(2)}
                                            </p>
                                        </div>
                                    </SectionCard>
                                ))}
                            </div>
                            <p className="app-micro text-[#8E8E93] mt-2.5 leading-relaxed">
                                Extracted directly from the PDF's text —
                                double-check quantities and prices before
                                locking payment, since layout parsing is
                                best-effort.
                            </p>
                        </div>
                    </div>
                )}
            </div>

            {step === "review" && (
                <BottomDock>
                    <PrimaryBtn
                        label="Confirm & Lock Payment in Escrow"
                        onClick={runEscrowLifecycle}
                    />
                    <SecondaryBtn
                        label="Try Another File"
                        onClick={() => setStep("upload")}
                    />
                </BottomDock>
            )}

            {step === "escrow" && (
                <div className="p-4 space-y-4 pb-32">
                    <EscrowPadlockCard
                        status={escrowStatus}
                        amount={`R${invoiceTotal.toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`}
                        counterparty="Uploaded Supplier Invoice"
                        reference={invoiceNumber || fileName || "—"}
                    />

                    {escrowStatus === "LOCKED" && (
                        <BottomDock>
                            <PrimaryBtn
                                label="Confirm Delivery Received & Release Funds"
                                onClick={releaseEscrow}
                            />
                        </BottomDock>
                    )}

                    {escrowStatus === "RELEASED" && (
                        <SectionCard className="p-4 flex items-center gap-3">
                            <div className="w-9 h-9 rounded-full bg-[#E3FCEF] flex items-center justify-center shrink-0">
                                <CheckmarkIcon
                                    size={18}
                                    className="text-[#00875A]"
                                />
                            </div>
                            <p className="app-body">
                                Order settled end-to-end via MoMo — payment
                                locked on dispatch, released on confirmed
                                delivery.
                            </p>
                        </SectionCard>
                    )}
                </div>
            )}
        </>
    );
}
