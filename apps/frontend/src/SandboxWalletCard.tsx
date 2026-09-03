import { useCallback, useEffect, useState } from "react";
import { WalletCards } from "lucide-react";
import { sandboxWalletGet } from "./shared/api/app-api";
import type { SandboxWalletResponse } from "./shared/api/generated";
import { SectionCard } from "./ui";

export function SandboxWalletCard() {
    const [wallet, setWallet] = useState<SandboxWalletResponse>();

    const refresh = useCallback(async () => {
        const result = await sandboxWalletGet();
        if (result.data) setWallet(result.data);
    }, []);

    useEffect(() => {
        void refresh();
        const timer = window.setInterval(() => void refresh(), 3000);
        window.addEventListener("trademesh:wallet-updated", refresh);
        return () => {
            window.clearInterval(timer);
            window.removeEventListener("trademesh:wallet-updated", refresh);
        };
    }, [refresh]);

    return (
        <SectionCard className="p-4">
            <div className="flex items-center justify-between gap-3">
                <div className="flex items-center gap-3 min-w-0">
                    <div className="w-10 h-10 rounded-lg bg-[#EBF3FC] text-[#003E85] flex items-center justify-center shrink-0">
                        <WalletCards size={21} strokeWidth={1.75} />
                    </div>
                    <div className="min-w-0">
                        <p className="app-caption text-[#595959]">
                            Sandbox wallet
                        </p>
                        <p className="app-heading truncate">
                            {wallet?.displayName ?? "Loading account…"}
                        </p>
                    </div>
                </div>
                <div className="text-right shrink-0">
                    <p className="app-metric-hero" style={{ fontSize: 24 }}>
                        {wallet
                            ? new Intl.NumberFormat("en-ZA", {
                                  style: "currency",
                                  currency: wallet.currency,
                                  maximumFractionDigits: 0,
                              }).format(wallet.availableBalance)
                            : "—"}
                    </p>
                    <p className="app-micro">Available</p>
                </div>
            </div>
            {wallet?.entries[0] && (
                <p className="app-caption text-[#595959] mt-3 pt-3 border-t border-[#E5E7EB]">
                    {wallet.entries[0].description}
                </p>
            )}
        </SectionCard>
    );
}
