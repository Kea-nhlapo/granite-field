import type { PointResponse } from "../../shared/api/generated";

export function polylinePoints(geometry: PointResponse[] | undefined) {
    const points = geometry ?? [];
    if (points.length === 0) {
        return "";
    }
    const lats = points.map((point) => point.latitude ?? 0);
    const lngs = points.map((point) => point.longitude ?? 0);
    const minLat = Math.min(...lats);
    const maxLat = Math.max(...lats);
    const minLng = Math.min(...lngs);
    const maxLng = Math.max(...lngs);
    const latSpan = maxLat - minLat || 1;
    const lngSpan = maxLng - minLng || 1;
    return points
        .map((point) => {
            const x = (((point.longitude ?? 0) - minLng) / lngSpan) * 400;
            const y = ((maxLat - (point.latitude ?? 0)) / latSpan) * 220;
            return `${x},${y}`;
        })
        .join(" ");
}
