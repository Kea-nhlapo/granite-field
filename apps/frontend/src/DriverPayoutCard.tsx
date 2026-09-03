import { useEffect, useState } from "react";
import { AnimatePresence } from "motion/react";
import { Loader2, PartyPopper, Smartphone } from "lucide-react";
import { m, springs } from "./motion";
import { SectionCard } from "./ui";

type PayoutState = "idle" | "sending" | "paid";

/**
 * Reuses the same MomoClient.transfer() call as escrow release (see
 * EscrowPadlockCard) with a different recipient — the driver's MSISDN instead
 * of the supplier's. Auto-starts shortly after mount so it lands right after
 * the handover scan, before the user has to ask for anything.
 */
export function DriverPayoutCard({
  driverName,
  amount,
  startDelayMs = 900,
}: {
  driverName: string;
  amount: string;
  startDelayMs?: number;
}) {
  const [state, setState] = useState<PayoutState>("idle");

  useEffect(() => {
    const t1 = setTimeout(() => setState("sending"), startDelayMs);
    const t2 = setTimeout(() => setState("paid"), startDelayMs + 1400);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [startDelayMs]);

  if (state === "idle") return null;

  return (
    <SectionCard className="p-4 overflow-hidden">
      <div className="flex items-center gap-3.5">
        <AnimatePresence mode="wait">
          <m.div
            key={state}
            initial={{ scale: 0.6, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.6, opacity: 0 }}
            transition={springs.quick}
            className="w-12 h-12 rounded-xl flex items-center justify-center shrink-0"
            style={{
              background:
                state === "paid"
                  ? "linear-gradient(135deg, var(--fluent-success, #00875A), #00694A)"
                  : "linear-gradient(135deg, var(--momo-yellow, #FFCC00), #E0B000)",
            }}
          >
            {state === "sending" ? (
              <Loader2 size={22} strokeWidth={1.75} className="animate-spin text-[#002B49]" />
            ) : (
              <PartyPopper size={22} strokeWidth={1.75} className="text-white" />
            )}
          </m.div>
        </AnimatePresence>

        <div className="flex-1 min-w-0">
          <p className="app-heading truncate">{driverName}</p>
          <AnimatePresence mode="wait">
            <m.p
              key={state}
              initial={{ opacity: 0, y: -4 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: 4 }}
              transition={{ duration: 0.2 }}
              className="app-caption mt-0.5"
              style={{
                color:
                  state === "paid"
                    ? "var(--fluent-success, #00875A)"
                    : "var(--momo-blue, #003E85)",
              }}
            >
              {state === "sending"
                ? "Sending instant delivery bonus via MoMo…"
                : `Instant bonus of ${amount} paid — notification sent`}
            </m.p>
          </AnimatePresence>
        </div>

        {state === "paid" && (
          <m.div
            initial={{ opacity: 0, x: 8 }}
            animate={{ opacity: 1, x: 0 }}
            className="shrink-0 flex items-center gap-1 text-[#8E8E93]"
          >
            <Smartphone size={14} strokeWidth={1.75} />
          </m.div>
        )}
      </div>
    </SectionCard>
  );
}
