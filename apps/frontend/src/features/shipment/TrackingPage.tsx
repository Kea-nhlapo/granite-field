import { useEffect, useState } from "react";

import { api } from "../../shared/api/client";
import type { Shipment } from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { SectionCard, TopBar } from "../../ui";

export function TrackingPage({
    onOpenHandover,
}: {
    onOpenHandover: () => void;
}) {
    const [shipment, setShipment] = useState<Shipment>(() => api.getShipment());

    useEffect(() => {
        const timer = window.setInterval(() => {
            setShipment(api.getShipment());
        }, 1500);
        return () => window.clearInterval(timer);
    }, []);

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Live track" />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                <SectionCard className="p-4 space-y-2">
                    <p className="text-xs font-mono-data">{shipment.id}</p>
                    <p className="text-sm font-semibold">{shipment.state}</p>
                    <p className="text-xs">
                        Approved path: {shipment.approvedPath}
                    </p>
                    <p className="text-xs">
                        Actual path: {shipment.actualPath}
                    </p>
                    {shipment.approximateArea ? (
                        <p className="text-xs text-gray-500">
                            Approximate area: {shipment.approximateArea}
                        </p>
                    ) : null}
                </SectionCard>
                <SectionCard className="p-4 space-y-2">
                    <p className="text-sm font-semibold">Event timeline</p>
                    {shipment.events.map((event) => (
                        <p
                            key={`${event.at}-${event.kind}`}
                            className="text-xs"
                        >
                            {event.at} · {event.kind.replaceAll("_", " ")} ·{" "}
                            {event.summary}
                        </p>
                    ))}
                </SectionCard>
                <StatusMessage>
                    Risk language stays cautious: deviations are possible and
                    require review.
                </StatusMessage>
                <button
                    className="w-full h-10 rounded-lg font-semibold"
                    style={{
                        background: "var(--yellow)",
                        color: "var(--navy)",
                    }}
                    onClick={onOpenHandover}
                >
                    Open handover QR
                </button>
            </div>
        </div>
    );
}
