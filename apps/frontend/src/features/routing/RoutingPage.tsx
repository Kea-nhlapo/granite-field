import { useState } from "react";

import { api } from "../../shared/api/client";
import { ApiError } from "../../shared/api/errors";
import type { RouteOption } from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { PrimaryBtn, SectionCard, TopBar } from "../../ui";

export function RoutingPage({
    onBack,
    onOpenLogistics,
}: {
    onBack?: () => void;
    onOpenLogistics: () => void;
}) {
    const [cargo, setCargo] = useState("grocery-ambient");
    const [safety, setSafety] = useState(70);
    const [options, setOptions] = useState(() => api.getRouteOptions().options);
    const [selected, setSelected] = useState<RouteOption | null>(null);
    const [error, setError] = useState<string | null>(null);

    function refresh() {
        setError(null);
        setOptions(api.getRouteOptions().options);
    }

    function choose(option: RouteOption) {
        setError(null);
        try {
            setSelected(
                api.selectRoute(option.id, `${cargo};safety=${safety}`),
            );
        } catch (caught) {
            setError(
                caught instanceof ApiError
                    ? caught.message
                    : "Selection failed.",
            );
        }
    }

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar
                title="Route planner"
                onBack={onBack}
                action={
                    <button
                        className="text-xs font-semibold"
                        style={{ color: "var(--blue)" }}
                        onClick={onOpenLogistics}
                    >
                        Consolidation
                    </button>
                }
            />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                <label className="block text-sm">
                    Cargo profile
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3"
                        aria-label="Cargo profile"
                        value={cargo}
                        onChange={(event) => setCargo(event.target.value)}
                    />
                </label>
                <label className="block text-sm">
                    Safety weight {safety}
                    <input
                        className="mt-1 w-full"
                        type="range"
                        min={0}
                        max={100}
                        aria-label="Safety weight"
                        value={safety}
                        onChange={(event) =>
                            setSafety(Number(event.target.value))
                        }
                    />
                </label>
                <button className="text-sm font-semibold" onClick={refresh}>
                    Recalculate routes
                </button>
                <table className="w-full text-xs">
                    <caption className="text-left text-sm font-semibold mb-2">
                        Route summary
                    </caption>
                    <thead>
                        <tr className="text-left text-gray-500">
                            <th>Route</th>
                            <th>Kind</th>
                            <th>Time</th>
                            <th>Km</th>
                        </tr>
                    </thead>
                    <tbody>
                        {options.map((option) => (
                            <tr key={option.id}>
                                <td>{option.name}</td>
                                <td>{option.kind.replaceAll("_", " ")}</td>
                                <td>{option.time}</td>
                                <td>{option.distanceKm}</td>
                            </tr>
                        ))}
                    </tbody>
                </table>
                {options.map((option) => (
                    <SectionCard key={option.id} className="p-4 space-y-2">
                        <p className="text-sm font-semibold">{option.name}</p>
                        <p className="text-xs">
                            {option.kind.replaceAll("_", " ")} · {option.time} ·{" "}
                            {option.distanceKm} km · geometry {option.geometry}
                        </p>
                        <p className="text-xs">{option.reason}</p>
                        {option.missingData ? (
                            <StatusMessage>
                                Missing data — this option is not presented as
                                safe.
                            </StatusMessage>
                        ) : null}
                        <PrimaryBtn
                            label={
                                selected?.id === option.id
                                    ? "Selected"
                                    : `Select ${option.id}`
                            }
                            onClick={() => choose(option)}
                        />
                    </SectionCard>
                ))}
                {selected ? (
                    <StatusMessage>
                        {`Selected ${selected.name}. ${selected.reason}`}
                    </StatusMessage>
                ) : null}
                {error ? (
                    <StatusMessage tone="error">{error}</StatusMessage>
                ) : null}
            </div>
        </div>
    );
}
