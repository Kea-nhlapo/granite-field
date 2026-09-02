import {
    shipmentGet,
    telemetryHistory,
    telemetryLive,
} from "../../shared/api/app-api";

export function loadShipment(businessId: string, shipmentId: string) {
    return shipmentGet({
        path: { businessId, shipmentId },
    });
}

export function loadTelemetryHistory(businessId: string, shipmentId: string) {
    return telemetryHistory({
        path: { businessId, shipmentId },
        query: { limit: 100 },
    });
}

export function loadLivePosition(businessId: string, shipmentId: string) {
    return telemetryLive({
        path: { businessId, shipmentId },
    });
}
