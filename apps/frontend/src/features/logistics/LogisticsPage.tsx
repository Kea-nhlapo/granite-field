import { api } from "../../shared/api/client";
import { formatMoney } from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { SectionCard, TopBar } from "../../ui";

export function LogisticsPage({ onBack }: { onBack: () => void }) {
    const consolidation = api.getConsolidation();
    const matches = api.getCapacityMatches();
    const totalKg = consolidation.included.reduce(
        (sum, row) => sum + row.weightKg,
        0,
    );
    const totalM3 = consolidation.included.reduce(
        (sum, row) => sum + row.volumeM3,
        0,
    );

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Consolidation" onBack={onBack} />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                <SectionCard className="p-4">
                    <p className="text-sm font-semibold mb-2">
                        Included orders
                    </p>
                    {consolidation.included.map((row) => (
                        <p key={row.label} className="text-xs py-1">
                            {row.label} · {row.weightKg} kg · {row.volumeM3} m³
                        </p>
                    ))}
                    <p className="text-xs mt-2">
                        Combined {totalKg} kg · {totalM3.toFixed(1)} m³ ·
                        overlapping Tuesday window · grocery-compatible cargo.
                    </p>
                    <p className="text-xs text-gray-500 mt-3">
                        Other businesses are not named in exclusions.
                    </p>
                    {consolidation.exclusions.map((row) => (
                        <p key={row.reason} className="text-xs mt-1">
                            {row.reason}
                        </p>
                    ))}
                </SectionCard>
                <SectionCard className="p-4">
                    <p className="text-sm font-semibold mb-2">
                        Capacity matches
                    </p>
                    {matches.matches.length === 0 ? (
                        <StatusMessage>
                            No match yet. A wider delivery window or a lighter
                            cargo profile can change this.
                        </StatusMessage>
                    ) : (
                        matches.matches.map((match) => (
                            <div
                                key={match.id}
                                className="text-xs py-2 border-b border-gray-50 last:border-0"
                            >
                                <p className="font-semibold">
                                    {match.id} ·{" "}
                                    {match.hardness === "HARD_FAIL"
                                        ? "Blocked"
                                        : "Trade-off"}
                                </p>
                                <p>
                                    Spare {match.spareKg} kg · +{match.addedKm}{" "}
                                    km · {formatMoney(match.cost)} · score{" "}
                                    {match.score}
                                </p>
                                <p>{match.reason}</p>
                            </div>
                        ))
                    )}
                </SectionCard>
            </div>
        </div>
    );
}
