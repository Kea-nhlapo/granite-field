import { riskList } from "../../shared/api/internal-api";

export function loadRiskIndicators(shipmentId: string) {
    return riskList({
        path: { shipmentId },
    });
}
