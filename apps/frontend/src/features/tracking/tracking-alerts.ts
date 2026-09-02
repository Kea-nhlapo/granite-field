import type {
    LivePositionResponse,
    ReadingResponse,
} from "../../shared/api/generated";
import type { TrackingShipment } from "./tracking-types";

export type TimelineItem = {
    id: string;
    at: string;
    kind: string;
    summary: string;
};

function distanceFromRoute(
    latitude: number,
    longitude: number,
    route: Array<{ latitude?: number; longitude?: number }>,
) {
    if (route.length === 0) {
        return 0;
    }
    return Math.min(
        ...route.map((point) => {
            const dLat = (point.latitude ?? 0) - latitude;
            const dLng = (point.longitude ?? 0) - longitude;
            return Math.hypot(dLat, dLng);
        }),
    );
}

export function buildTimeline(
    shipment: TrackingShipment,
    readings: ReadingResponse[],
    live?: LivePositionResponse,
): TimelineItem[] {
    const items: TimelineItem[] = [];
    for (const transition of shipment.transitionHistory ?? []) {
        items.push({
            id: `transition-${transition.occurredAt}-${transition.toStatus}`,
            at: transition.occurredAt ?? "",
            kind: "shipment",
            summary:
                `Shipment ${transition.toStatus?.replace(/_/g, " ").toLowerCase()}${
                    transition.toStatus === "DELAYED"
                        ? " — possible delay, requires review"
                        : ""
                }. ${transition.reason ?? ""}`.trim(),
        });
    }
    const assignment = shipment.currentAssignment;
    if (assignment?.startedAt) {
        items.push({
            id: `handover-${assignment.startedAt}`,
            at: assignment.startedAt,
            kind: "handover",
            summary:
                "Collection handover recorded. Safe evidence stays on the authorized challenge; this view shows only the confirmed time.",
        });
    }
    const route = assignment?.routeGeometry ?? [];
    let previousFuel: number | undefined;
    let previousDevice: string | undefined;
    const ordered = [...readings].sort((left, right) =>
        (left.recordedAt ?? "").localeCompare(right.recordedAt ?? ""),
    );
    for (const reading of ordered) {
        const at = reading.recordedAt ?? "";
        if (reading.networkStatus === "OFFLINE") {
            items.push({
                id: `offline-${reading.readingId}`,
                at,
                kind: "offline",
                summary: `Tracker offline at ${at}. Possible loss of coverage — requires review.`,
            });
        }
        if (
            reading.latitude !== undefined &&
            reading.longitude !== undefined &&
            distanceFromRoute(reading.latitude, reading.longitude, route) > 0.02
        ) {
            items.push({
                id: `deviation-${reading.readingId}`,
                at,
                kind: "deviation",
                summary:
                    "Possible route deviation — requires review. Location is an authorized telemetry summary, not a finding.",
            });
        }
        if (
            previousFuel !== undefined &&
            reading.fuelLitres !== undefined &&
            previousFuel - reading.fuelLitres >= 8 &&
            (reading.speedKilometresPerHour ?? 0) < 5
        ) {
            items.push({
                id: `fuel-${reading.readingId}`,
                at,
                kind: "fuel-loss",
                summary:
                    "Possible fuel loss while nearly stationary — requires review.",
            });
        }
        if (reading.sealOpen) {
            items.push({
                id: `seal-${reading.readingId}`,
                at,
                kind: "seal",
                summary: "Possible seal open — requires review.",
            });
        }
        if (
            previousDevice &&
            reading.deviceId &&
            previousDevice !== reading.deviceId
        ) {
            items.push({
                id: `device-${reading.readingId}`,
                at,
                kind: "device-change",
                summary: `Possible device change at ${at} — requires review.`,
            });
        }
        previousFuel = reading.fuelLitres ?? previousFuel;
        previousDevice = reading.deviceId ?? previousDevice;
    }
    if (live?.networkStatus === "OFFLINE") {
        items.push({
            id: `offline-live-${live.readingId}`,
            at: live.recordedAt ?? "",
            kind: "offline",
            summary: `Tracker offline at ${live.recordedAt}. Possible loss of coverage — requires review.`,
        });
    }
    return items.sort((left, right) => left.at.localeCompare(right.at));
}
