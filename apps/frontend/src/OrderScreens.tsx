import { useState } from "react";
import { AnimatePresence } from "motion/react";
import { ChevronDown } from "lucide-react";
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
  DismissIcon,
  DocumentTextIcon,
  PlusIcon,
} from "./icons";
import { EscrowPadlockCard, type EscrowStatus } from "./EscrowPadlockCard";
import { m, springs } from "./motion";

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

type LineStatus = "ok" | "qty_mismatch" | "price_mismatch" | "approved" | "rejected";

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
            <span className="app-caption-strong text-[#002B49]">Invoice review required:</span> Thabo Distributors invoice contains 3 price/quantity variances against agreed rate card.
          </div>
        </MessageBar>

        <div>
          <div className="flex items-center justify-between mb-2">
            <p className="app-overline">
              Recent Purchase Orders
            </p>
            <span className="app-micro text-[#8E8E93]">4 active</span>
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
                style={{ borderColor: "var(--fluent-stroke-divider, #E5E7EB)" }}
              >
                <div className="w-8 h-8 rounded-lg bg-[#EBF3FC] flex items-center justify-center shrink-0">
                  <DocumentTextIcon size={16} className="text-[#003E85]" />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="app-micro text-[#595959]">
                      {o.id}
                    </span>
                    <span className="app-micro text-[#8E8E93]">• {o.date}</span>
                  </div>
                  <p className="app-heading truncate mt-0.5">
                    {o.sup}
                  </p>
                </div>
                <div className="text-right shrink-0">
                  <p className="app-metric">
                    {o.total}
                  </p>
                  <div className="mt-1">
                    <Badge label={o.status} color={o.statusColor} />
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
  const [step, setStep] = useState<"upload" | "parsing" | "review" | "escrow">("upload");
  const [escrowStatus, setEscrowStatus] = useState<EscrowStatus>("LOCK_REQUESTED");

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
      product: "Tinned Pilchards 400g",
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

  const [showClean, setShowClean] = useState(false);

  const flagged = lines.filter(
    (l) => l.status === "qty_mismatch" || l.status === "price_mismatch"
  );
  const needsAttention = lines.filter((l) => l.status !== "ok");
  const cleanLines = lines.filter((l) => l.status === "ok");

  const approve = (id: string) =>
    setLines((ls) =>
      ls.map((l) => (l.id === id ? { ...l, status: "approved" } : l))
    );
  const reject = (id: string) =>
    setLines((ls) =>
      ls.map((l) => (l.id === id ? { ...l, status: "rejected" } : l))
    );

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
            <div
              onClick={() => {
                setStep("parsing");
                setTimeout(() => setStep("review"), 1600);
              }}
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
                  Supports PDF, scanned images (JPG, PNG), or TIFF
                </p>
              </div>
              <span className="app-micro font-semibold px-3 py-1 rounded-full bg-[#FFF9D6] text-[#7A6000] border border-[#FFE082]">
                Simulate Instant OCR Parse
              </span>
            </div>

            {/* Reconciliation Process Card */}
            <SectionCard className="p-4">
              <p className="app-heading mb-3">
                Automated Three-Way Matching Pipeline
              </p>
              <div className="space-y-3">
                {[
                  "Supplier uploads PDF or digital invoice",
                  "TradeMesh OCR engine extracts all line items & VAT breakdown",
                  "Cross-checks automatically against MoMo PSB purchase rates",
                  "Approve variances or trigger automated dispute settlement",
                ].map((s, i) => (
                  <div key={i} className="flex gap-2.5 items-start">
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
              <p className="app-heading">
                Parsing Invoice SB-INV-9012...
              </p>
              <p className="app-caption text-[#595959] mt-1">
                Extracting 5 SKUs, checking unit pricing against MoMo escrow contract
              </p>
            </div>
          </div>
        )}

        {step === "review" && (
          <div className="p-4 space-y-4 pb-32">
            {/* KPI Summary Tiles */}
            <div className="grid grid-cols-3 gap-2">
              <SectionCard className="p-3 text-center">
                <p className="app-micro text-[#595959]">Invoice Total</p>
                <p className="app-metric mt-0.5">
                  R14,480
                </p>
              </SectionCard>
              <SectionCard className="p-3 text-center">
                <p className="app-micro text-[#595959]">Lines Extracted</p>
                <p className="app-metric mt-0.5">
                  5 Items
                </p>
              </SectionCard>
              <div
                className="p-3 rounded-xl border text-center transition-colors"
                style={{
                  backgroundColor: flagged.length > 0 ? "#FFF3E0" : "#E3FCEF",
                  borderColor: flagged.length > 0 ? "#FFE0B2" : "#A3E7C9",
                }}
              >
                <p
                  className="app-micro font-semibold"
                  style={{ color: flagged.length > 0 ? "#D96B00" : "#00875A" }}
                >
                  Variances
                </p>
                <p
                  className="app-metric mt-0.5"
                  style={{ color: flagged.length > 0 ? "#F57C00" : "#00875A" }}
                >
                  {flagged.length} {flagged.length === 1 ? "flag" : "flags"}
                </p>
              </div>
            </div>

            {/* Line Items List — flagged/acted-on lines shown; clean matches collapsed */}
            <div>
              <p className="app-overline mb-2">
                Line Items Audit
              </p>
              <div className="space-y-2.5">
                {needsAttention.map((l) => {
                  const isFlagged =
                    l.status === "qty_mismatch" || l.status === "price_mismatch";
                  return (
                    <SectionCard
                      key={l.id}
                      className={`p-3.5 ${l.status === "rejected" ? "opacity-50" : ""}`}
                      style={
                        isFlagged
                          ? { borderLeftWidth: "4px", borderLeftColor: "#F57C00" }
                          : undefined
                      }
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex-1 min-w-0">
                          <p className="app-heading truncate">
                            {l.product}
                          </p>
                          <div className="mt-2 space-y-1 app-caption">
                            <div className="flex items-center gap-3">
                              <span className="text-[#595959]">
                                PO Qty: <span className="app-caption-strong">{l.qty}</span>
                              </span>
                              <span
                                className={`app-caption-strong ${
                                  l.status === "qty_mismatch" ? "text-[#F57C00]" : "text-[#595959]"
                                }`}
                              >
                                Inv Qty: {l.invQty}
                                {l.status === "qty_mismatch" && " (Δ -5)"}
                              </span>
                            </div>
                            <div className="flex items-center gap-3">
                              <span className="text-[#595959]">
                                PO Price: <span className="app-caption-strong">R{l.price.toFixed(2)}</span>
                              </span>
                              <span
                                className={`app-caption-strong ${
                                  l.status === "price_mismatch" ? "text-[#F57C00]" : "text-[#595959]"
                                }`}
                              >
                                Inv Price: R{l.invPrice.toFixed(2)}
                                {l.status === "price_mismatch" && " (Δ +R4.50)"}
                              </span>
                            </div>
                          </div>
                        </div>

                        <div className="shrink-0">
                          {l.status === "ok" && (
                            <Badge label="Match" color="success" />
                          )}
                          {l.status === "qty_mismatch" && (
                            <Badge label="Qty Variance" color="warning" />
                          )}
                          {l.status === "price_mismatch" && (
                            <Badge label="Price Variance" color="warning" />
                          )}
                          {l.status === "approved" && (
                            <Badge label="Approved" color="brand" />
                          )}
                          {l.status === "rejected" && (
                            <Badge label="Rejected" color="danger" />
                          )}
                        </div>
                      </div>

                      {/* Variance Decision Actions */}
                      {isFlagged && (
                        <div className="flex gap-2 mt-3 pt-2.5 border-t border-[#E5E7EB]">
                          <button
                            onClick={() => approve(l.id)}
                            className="flex-1 h-9 rounded-lg text-xs font-semibold text-white transition-colors"
                            style={{
                              backgroundColor: "var(--fluent-success, #00875A)",
                            }}
                          >
                            Accept Variance
                          </button>
                          <button
                            onClick={() => reject(l.id)}
                            className="flex-1 h-9 rounded-lg text-xs font-semibold border transition-colors bg-white hover:bg-[#FDE8E8]"
                            style={{
                              borderColor: "#F8B4B4",
                              color: "#D32F2F",
                            }}
                          >
                            Reject & Dispute
                          </button>
                        </div>
                      )}
                    </SectionCard>
                  );
                })}
              </div>

              {cleanLines.length > 0 && (
                <div className="mt-2.5">
                  <button
                    onClick={() => setShowClean((v) => !v)}
                    className="w-full flex items-center justify-between py-2 px-1 text-left"
                  >
                    <span className="app-caption-strong text-[#00875A] inline-flex items-center gap-1.5">
                      <CheckmarkIcon size={14} />
                      {cleanLines.length} more line{cleanLines.length > 1 ? "s" : ""} matched
                    </span>
                    <m.span animate={{ rotate: showClean ? 180 : 0 }} transition={springs.quick}>
                      <ChevronDown size={16} className="text-[#8E8E93]" />
                    </m.span>
                  </button>

                  <AnimatePresence initial={false}>
                    {showClean && (
                      <m.div
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: "auto", opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={springs.snappy}
                        className="overflow-hidden"
                      >
                        <div className="space-y-2.5 pt-1">
                          {cleanLines.map((l) => (
                            <SectionCard key={l.id} className="p-3.5">
                              <div className="flex items-start justify-between gap-2">
                                <div className="flex-1 min-w-0">
                                  <p className="app-heading truncate">{l.product}</p>
                                  <p className="app-caption text-[#595959] mt-1">
                                    Qty {l.qty} • R{l.price.toFixed(2)} — matches PO
                                  </p>
                                </div>
                                <Badge label="Match" color="success" />
                              </div>
                            </SectionCard>
                          ))}
                        </div>
                      </m.div>
                    )}
                  </AnimatePresence>
                </div>
              )}
            </div>
          </div>
        )}
      </div>

      {step === "review" && (
        <BottomDock>
          <PrimaryBtn
            label={
              flagged.length > 0
                ? `Resolve ${flagged.length} Variance${flagged.length > 1 ? "s" : ""} to Confirm`
                : "Confirm & Lock Payment in Escrow"
            }
            disabled={flagged.length > 0}
            onClick={runEscrowLifecycle}
          />
          {flagged.length === 0 && (
            <SecondaryBtn label="Download Signed PDF Summary" />
          )}
        </BottomDock>
      )}

      {step === "escrow" && (
        <div className="p-4 space-y-4 pb-32">
          <EscrowPadlockCard
            status={escrowStatus}
            amount="R14,480.00"
            counterparty="Thabo Distributors"
            reference="SB-INV-9012"
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
                <CheckmarkIcon size={18} className="text-[#00875A]" />
              </div>
              <p className="app-body">
                Order settled end-to-end via MoMo — payment locked on dispatch, released on confirmed delivery.
              </p>
            </SectionCard>
          )}
        </div>
      )}
    </>
  );
}
