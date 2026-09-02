import { api } from "../../shared/api/client";
import { SectionCard, TopBar } from "../../ui";

export function InsurerPage({ onBack }: { onBack: () => void }) {
    const data = api.getInsuranceCase();
    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Insurance evidence" onBack={onBack} />
            <div className="flex-1 phone-scroll overflow-y-auto p-4">
                <SectionCard className="p-4 space-y-2">
                    {data.evidence.map((item) => (
                        <p key={item.label} className="text-sm">
                            {item.label} · {item.source}
                        </p>
                    ))}
                    <p className="text-xs text-gray-500">
                        Raw identity data is omitted unless the contract
                        authorizes it.
                    </p>
                </SectionCard>
            </div>
        </div>
    );
}
