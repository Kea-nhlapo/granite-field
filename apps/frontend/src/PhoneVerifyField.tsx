import { useEffect, useRef, useState } from "react";
import { AnimatePresence } from "motion/react";
import { CheckCircle2, Loader2, XCircle } from "lucide-react";
import { m, springs } from "./motion";

type CheckState = "idle" | "checking" | "valid" | "invalid";

/** Accepts local (0…) or international (+27…) South African MSISDN shapes — good enough for a live demo check. */
const SA_MSISDN = /^(?:\+27|0)[6-8][0-9]{8}$/;

/**
 * Debounced, animated stand-in for `MomoClient.validateAccountHolder`. The real
 * backend call is a single GET once the payment module exposes it publicly —
 * swap `simulateValidate` for that fetch without touching the animation.
 */
function simulateValidate(phone: string): Promise<boolean> {
    return new Promise((resolve) => {
        setTimeout(
            () => resolve(SA_MSISDN.test(phone.replace(/\s/g, ""))),
            750,
        );
    });
}

export function PhoneVerifyField({
    label,
    placeholder,
    onVerified,
}: {
    label: string;
    placeholder: string;
    onVerified?: (phone: string, valid: boolean) => void;
}) {
    const [phone, setPhone] = useState("");
    const [state, setState] = useState<CheckState>("idle");
    const requestId = useRef(0);

    useEffect(() => {
        const digits = phone.replace(/\s/g, "");
        if (digits.length < 9) {
            setState("idle");
            return;
        }

        setState("checking");
        const id = ++requestId.current;
        const timer = setTimeout(async () => {
            const valid = await simulateValidate(digits);
            if (requestId.current !== id) return; // superseded by newer input
            setState(valid ? "valid" : "invalid");
            onVerified?.(digits, valid);
        }, 350);

        return () => clearTimeout(timer);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [phone]);

    const borderColor =
        state === "valid"
            ? "var(--fluent-success, #00875A)"
            : state === "invalid"
              ? "var(--fluent-danger, #D32F2F)"
              : "var(--fluent-stroke-default, #D1D5DB)";

    return (
        <div>
            <label className="block app-caption-strong text-[#002B49] mb-1">
                {label}
            </label>
            <div className="relative">
                <input
                    type="tel"
                    inputMode="tel"
                    value={phone}
                    onChange={(e) => setPhone(e.target.value)}
                    placeholder={placeholder}
                    className="w-full app-caption bg-white border rounded-lg h-10 pl-3 pr-9 text-[#1A1A1A] outline-none transition-colors focus:ring-1"
                    style={{ borderColor }}
                />
                <div className="absolute right-2.5 top-1/2 -translate-y-1/2">
                    <AnimatePresence mode="wait">
                        {state === "checking" && (
                            <m.div
                                key="checking"
                                initial={{ opacity: 0, scale: 0.6 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.6 }}
                                transition={springs.quick}
                            >
                                <Loader2
                                    size={16}
                                    className="animate-spin text-[#8E8E93]"
                                />
                            </m.div>
                        )}
                        {state === "valid" && (
                            <m.div
                                key="valid"
                                initial={{
                                    opacity: 0,
                                    scale: 0.4,
                                    rotate: -45,
                                }}
                                animate={{ opacity: 1, scale: 1, rotate: 0 }}
                                exit={{ opacity: 0, scale: 0.6 }}
                                transition={springs.quick}
                            >
                                <CheckCircle2
                                    size={16}
                                    className="text-[#00875A]"
                                />
                            </m.div>
                        )}
                        {state === "invalid" && (
                            <m.div
                                key="invalid"
                                initial={{ opacity: 0, scale: 0.4 }}
                                animate={{ opacity: 1, scale: 1 }}
                                exit={{ opacity: 0, scale: 0.6 }}
                                transition={springs.quick}
                            >
                                <XCircle size={16} className="text-[#D32F2F]" />
                            </m.div>
                        )}
                    </AnimatePresence>
                </div>
            </div>
            <AnimatePresence mode="wait">
                {state === "valid" && (
                    <m.p
                        key="valid-msg"
                        initial={{ opacity: 0, y: -4, height: 0 }}
                        animate={{ opacity: 1, y: 0, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        className="app-micro text-[#00875A] mt-1"
                    >
                        Active MoMo account — verified live
                    </m.p>
                )}
                {state === "invalid" && (
                    <m.p
                        key="invalid-msg"
                        initial={{ opacity: 0, y: -4, height: 0 }}
                        animate={{ opacity: 1, y: 0, height: "auto" }}
                        exit={{ opacity: 0, height: 0 }}
                        className="app-micro text-[#D32F2F] mt-1"
                    >
                        No active MoMo account found for this number
                    </m.p>
                )}
            </AnimatePresence>
        </div>
    );
}
