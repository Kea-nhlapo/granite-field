import { Lock, LockOpen, ArrowDownCircle, ArrowUpCircle } from "lucide-react";
import { Badge, SectionCard, TopBar } from "./ui";

/**
 * Mirrors za.co.trademesh.modules.payment.domain.EscrowTransactionType and
 * EscrowTransactionStatus exactly, so this screen can be pointed at the real
 * GET /api/businesses/{businessId}/wallet endpoint (za.co.trademesh.modules
 * .payment.api.WalletController) without changing the state names it renders.
 *
 * Demo data only for now: this frontend has no business-context resolution
 * anywhere yet (no session-to-businessId mapping exists), so there is no real
 * businessId to call the live endpoint with.
 */
type TransactionType = "LOCK" | "RELEASE";
type TransactionStatus =
    | "REQUESTED"
    | "PENDING"
    | "SUCCESSFUL"
    | "FAILED"
    | "TIMED_OUT";

interface WalletTransaction {
    id: string;
    type: TransactionType;
    counterparty: string;
    amount: string;
    status: TransactionStatus;
    updatedAt: string;
}

const RECENT_TRANSACTIONS: WalletTransaction[] = [
    {
        id: "1",
        type: "LOCK",
        counterparty: "Thabo Distributors",
        amount: "R14,480.00",
        status: "SUCCESSFUL",
        updatedAt: "Today, 09:14",
    },
    {
        id: "2",
        type: "RELEASE",
        counterparty: "Sipho Mthembu (Driver)",
        amount: "R120.00",
        status: "SUCCESSFUL",
        updatedAt: "Today, 09:12",
    },
    {
        id: "3",
        type: "LOCK",
        counterparty: "Bulk SA Wholesale",
        amount: "R8,340.00",
        status: "PENDING",
        updatedAt: "Yesterday, 16:40",
    },
    {
        id: "4",
        type: "RELEASE",
        counterparty: "Nkosi Foods SA",
        amount: "R2,100.00",
        status: "FAILED",
        updatedAt: "Yesterday, 11:05",
    },
];

function statusColor(status: TransactionStatus): "success" | "warning" | "danger" | "brand" {
    if (status === "SUCCESSFUL") return "success";
    if (status === "FAILED" || status === "TIMED_OUT") return "danger";
    if (status === "PENDING") return "warning";
    return "brand";
}

function statusLabel(status: TransactionStatus): string {
    switch (status) {
        case "SUCCESSFUL":
            return "Successful";
        case "FAILED":
            return "Failed";
        case "TIMED_OUT":
            return "Timed out";
        case "PENDING":
            return "Pending";
        case "REQUESTED":
            return "Requested";
    }
}

export function WalletScreen({ onBack }: { onBack: () => void }) {
    return (
        <>
            <TopBar title="Wallet" onBack={onBack} />
            <div
                className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
            >
                <Badge label="Demo simulation" color="neutral" />

                {/* Balances */}
                <div className="grid grid-cols-2 gap-3">
                    <SectionCard className="p-4">
                        <div className="flex items-center gap-2 mb-2">
                            <ArrowDownCircle
                                size={18}
                                className="text-[#00875A]"
                            />
                            <p className="app-caption text-[#595959]">
                                Collections
                            </p>
                        </div>
                        <p className="app-metric" style={{ fontSize: 20 }}>
                            R15,420.75
                        </p>
                        <p className="app-micro text-[#8E8E93] mt-0.5">
                            Available balance
                        </p>
                    </SectionCard>

                    <SectionCard className="p-4">
                        <div className="flex items-center gap-2 mb-2">
                            <ArrowUpCircle
                                size={18}
                                className="text-[#003E85]"
                            />
                            <p className="app-caption text-[#595959]">
                                Disbursements
                            </p>
                        </div>
                        <p className="app-metric" style={{ fontSize: 20 }}>
                            R8,930.00
                        </p>
                        <p className="app-micro text-[#8E8E93] mt-0.5">
                            Available balance
                        </p>
                    </SectionCard>
                </div>

                {/* Recent transactions */}
                <div>
                    <p className="app-overline mb-2">Recent Transactions</p>
                    <SectionCard>
                        {RECENT_TRANSACTIONS.map((tx, i, arr) => (
                            <div
                                key={tx.id}
                                className={`flex items-center gap-3 p-3.5 ${i < arr.length - 1 ? "border-b border-[#E5E7EB]" : ""}`}
                            >
                                <div
                                    className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0"
                                    style={{
                                        backgroundColor:
                                            tx.type === "LOCK"
                                                ? "#EBF3FC"
                                                : "#FFF9D6",
                                        color:
                                            tx.type === "LOCK"
                                                ? "#003E85"
                                                : "#7A6000",
                                    }}
                                >
                                    {tx.type === "LOCK" ? (
                                        <Lock size={16} strokeWidth={1.75} />
                                    ) : (
                                        <LockOpen
                                            size={16}
                                            strokeWidth={1.75}
                                        />
                                    )}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p className="app-heading truncate">
                                        {tx.counterparty}
                                    </p>
                                    <p className="app-micro text-[#8E8E93] mt-0.5">
                                        {tx.type === "LOCK"
                                            ? "Payment locked"
                                            : "Funds released"}{" "}
                                        • {tx.updatedAt}
                                    </p>
                                </div>
                                <div className="text-right shrink-0">
                                    <p className="app-metric">{tx.amount}</p>
                                    <div className="mt-1">
                                        <Badge
                                            label={statusLabel(tx.status)}
                                            color={statusColor(tx.status)}
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
