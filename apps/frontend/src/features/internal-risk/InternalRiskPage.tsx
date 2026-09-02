import { api } from "../../shared/api/client";
import { SectionCard, TopBar } from "../../ui";

export function InternalRiskPage({ onBack }: { onBack: () => void }) {
    const data = api.getRiskCase();
    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Risk case" onBack={onBack} />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-3">
                {data.indicators.map((item) => (
                    <SectionCard key={item.label} className="p-4">
                        <p className="text-sm font-semibold">{item.label}</p>
                        <p className="text-xs text-gray-500">
                            {item.source} · {item.at} · {item.state}
                        </p>
                    </SectionCard>
                ))}
                <p className="text-xs text-gray-600">{data.notes}</p>
            </div>
        </div>
    );
}
