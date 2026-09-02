import type { ShipmentResponse } from "../../shared/api/generated";

export type TrackingRoutePoint = {
    latitude?: number;
    longitude?: number;
};

export type TrackingAssignment = {
    assignmentId?: string;
    driverDisplayName?: string;
    routeGeometry?: TrackingRoutePoint[];
    startedAt?: string;
    endedAt?: string;
    reason?: string;
};

export type TrackingTransition = {
    fromStatus?: string;
    toStatus?: string;
    occurredAt?: string;
    reason?: string;
};

export type TrackingShipment = Omit<
    ShipmentResponse,
    "currentAssignment" | "assignmentHistory" | "transitionHistory"
> & {
    currentAssignment?: TrackingAssignment;
    assignmentHistory?: TrackingAssignment[];
    transitionHistory?: TrackingTransition[];
};
