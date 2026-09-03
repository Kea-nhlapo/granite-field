import { AnimatePresence } from "motion/react";
import { Lock, LockOpen, AlertTriangle, Loader2 } from "lucide-react";
import { SectionCard } from "./ui";
import { m, springs } from "./motion";

/**
 * Mirrors za.co.trademesh.modules.payment.domain.EscrowStatus exactly, so this
 * component can be pointed at the real `/api/delivery/{id}/escrow/events` SSE
 * stream later without changing the state names it renders.
 */
export type EscrowStatus =
  | "LOCK_REQUESTED"
  | "LOCK_PENDING"
  | "LOCKED"
  | "LOCK_FAILED"
  | "RELEASE_REQUESTED"
  | "RELEASE_PENDING"
  | "RELEASED"
  | "RELEASE_FAILED";

const STATUS_COPY: Record<EscrowStatus, { label: string; caption: string }> = {
  LOCK_REQUESTED: { label: "Requesting payment", caption: "Sending Request to Pay via MoMo Collections…" },
  LOCK_PENDING: { label: "Awaiting approval", caption: "Waiting for the buyer to approve on their phone…" },
  LOCKED: { label: "Funds locked in escrow", caption: "Held securely until delivery is confirmed." },
  LOCK_FAILED: { label: "Payment request failed", caption: "The buyer declined or the request timed out." },
  RELEASE_REQUESTED: { label: "Releasing funds", caption: "Sending Transfer via MoMo Disbursements…" },
  RELEASE_PENDING: { label: "Transfer in progress", caption: "Funds are moving to the supplier's MoMo wallet…" },
  RELEASED: { label: "Funds released", caption: "Supplier has been paid." },
  RELEASE_FAILED: { label: "Release failed", caption: "The transfer could not be completed." },
};

function iconFor(status: EscrowStatus) {
  if (status === "LOCKED") return <Lock size={28} strokeWidth={1.75} className="text-white" />;
  if (status === "RELEASED") return <LockOpen size={28} strokeWidth={1.75} className="text-white" />;
  if (status === "LOCK_FAILED" || status === "RELEASE_FAILED")
    return <AlertTriangle size={28} strokeWidth={1.75} className="text-white" />;
  return <Loader2 size={28} strokeWidth={1.75} className="text-white animate-spin" />;
}

function badgeColor(status: EscrowStatus) {
  if (status === "LOCKED" || status === "RELEASED") return "var(--fluent-success, #00875A)";
  if (status === "LOCK_FAILED" || status === "RELEASE_FAILED") return "var(--fluent-danger, #D32F2F)";
  return "var(--momo-blue, #003E85)";
}

export function EscrowPadlockCard({
  status,
  amount,
  counterparty,
  reference,
}: {
  status: EscrowStatus;
  amount: string;
  counterparty: string;
  reference?: string;
}) {
  const copy = STATUS_COPY[status];
  const settled = status === "LOCKED" || status === "RELEASED";

  return (
    <SectionCard className="p-4 overflow-hidden">
      <div className="flex items-center gap-3.5">
        <AnimatePresence mode="wait">
          <m.div
            key={status}
            initial={{ scale: 0.6, opacity: 0, rotate: settled ? -90 : 0 }}
            animate={{ scale: 1, opacity: 1, rotate: 0 }}
            exit={{ scale: 0.6, opacity: 0 }}
            transition={springs.quick}
            className="w-14 h-14 rounded-xl flex items-center justify-center shrink-0"
            style={{
              background: `linear-gradient(135deg, ${badgeColor(status)}, var(--momo-navy, #002B49))`,
              boxShadow: `0 8px 18px ${badgeColor(status)}40`,
            }}
          >
            {iconFor(status)}
          </m.div>
        </AnimatePresence>

        <div className="flex-1 min-w-0">
          <div className="flex items-center justify-between gap-2">
            <p className="app-heading truncate">{counterparty}</p>
            <p className="app-metric shrink-0">{amount}</p>
          </div>
          <AnimatePresence mode="wait">
            <m.p
              key={copy.label}
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 4 }}
              transition={{ duration: 0.2 }}
              className="app-caption mt-0.5"
              style={{ color: badgeColor(status) }}
            >
              {copy.label}
            </m.p>
          </AnimatePresence>
          <p className="app-micro text-[#8E8E93] mt-0.5">{copy.caption}</p>
          {reference && (
            <p className="app-micro text-[#8E8E93] mt-1 font-fluent-mono">Ref: {reference}</p>
          )}
        </div>
      </div>
    </SectionCard>
  );
}
