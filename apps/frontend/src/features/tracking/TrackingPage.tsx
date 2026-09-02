import {
    Body1,
    Button,
    Card,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    Spinner,
    Title1,
} from "@fluentui/react-components";
import { useEffect, useState, type FormEvent } from "react";
import { useNavigate, useParams } from "react-router-dom";

import { mockShipmentId } from "../../shared/api/mocks/tracking-handlers";
import { useAccessStyles } from "../access/access.styles";
import { useSession } from "../access/SessionProvider";
import { buildTimeline } from "./tracking-alerts";
import {
    loadLivePosition,
    loadShipment,
    loadTelemetryHistory,
} from "./tracking-api";
import {
    isForbiddenTracking,
    isRetryableTrackingProblem,
    problemMessage,
} from "./tracking-errors";
import {
    canRequestPreciseTelemetry,
    displayCoordinate,
} from "./tracking-location";
import { polylinePoints } from "./tracking-map";
import { pollLiveTelemetry } from "./tracking-stream";
import type { TrackingShipment } from "./tracking-types";
import type {
    ApiProblem,
    LivePositionResponse,
    ReadingResponse,
} from "../../shared/api/generated";

type Screen =
    | { kind: "form" }
    | { kind: "loading" }
    | {
          kind: "ready";
          shipment: TrackingShipment;
          readings: ReadingResponse[];
          live?: LivePositionResponse;
          stream: "live" | "reconnecting" | "failed" | "idle";
      }
    | { kind: "error"; title: string; retryable: boolean };

export default function TrackingPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { session } = useSession();
    const { businessId = "", shipmentId } = useParams();
    const precise = canRequestPreciseTelemetry(session?.roles);
    const [screen, setScreen] = useState<Screen>(
        shipmentId ? { kind: "loading" } : { kind: "form" },
    );
    const [shipmentInput, setShipmentInput] = useState(
        shipmentId ?? mockShipmentId,
    );

    useEffect(() => {
        if (!businessId || !shipmentId) {
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "loading" });
        void Promise.all([
            loadShipment(businessId, shipmentId),
            loadTelemetryHistory(businessId, shipmentId),
        ]).then(([shipmentResult, historyResult]) => {
            if (abort.signal.aborted) {
                return;
            }
            if (shipmentResult.error || !shipmentResult.data) {
                setScreen(
                    errorScreen(
                        shipmentResult.error as ApiProblem | undefined,
                        "The shipment could not be loaded",
                    ),
                );
                return;
            }
            if (historyResult.error) {
                setScreen(
                    errorScreen(
                        historyResult.error as ApiProblem | undefined,
                        "Telemetry history could not be loaded",
                    ),
                );
                return;
            }
            const shipment = shipmentResult.data as TrackingShipment;
            const readings = historyResult.data?.readings ?? [];
            setScreen({
                kind: "ready",
                shipment,
                readings,
                stream: precise ? "idle" : "idle",
            });
            if (!precise) {
                return;
            }
            void pollLiveTelemetry({
                intervalMs: 80,
                load: () => loadLivePosition(businessId, shipmentId),
                signal: abort.signal,
                onStatus: (stream) => {
                    if (abort.signal.aborted) {
                        return;
                    }
                    setScreen((current) =>
                        current.kind === "ready"
                            ? { ...current, stream }
                            : current,
                    );
                },
                onUpdate: (live) => {
                    if (abort.signal.aborted) {
                        return;
                    }
                    setScreen((current) => {
                        if (current.kind !== "ready") {
                            return current;
                        }
                        if (
                            current.live?.recordedAt &&
                            live.recordedAt &&
                            live.recordedAt < current.live.recordedAt
                        ) {
                            return current;
                        }
                        return {
                            ...current,
                            live,
                            readings: mergeReading(current.readings, live),
                            stream: "live",
                        };
                    });
                },
            });
        });
        return () => abort.abort();
    }, [businessId, precise, shipmentId]);

    async function onOpen(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        navigate(`/app/tracking/${businessId}/shipments/${shipmentInput}`);
    }

    if (screen.kind === "loading") {
        return (
            <Card>
                <Spinner label="Loading shipment tracking..." />
            </Card>
        );
    }

    if (screen.kind === "error") {
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    {screen.title}
                </Title1>
                {screen.retryable ? (
                    <Button
                        className={styles.touchTarget}
                        onClick={() => {
                            setScreen({ kind: "form" });
                            navigate(`/app/tracking/${businessId}`);
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "ready") {
        const approved = screen.shipment.currentAssignment?.routeGeometry ?? [];
        const actual = screen.readings
            .filter(
                (reading) =>
                    reading.latitude !== undefined &&
                    reading.longitude !== undefined,
            )
            .map((reading) => ({
                latitude: reading.latitude,
                longitude: reading.longitude,
            }));
        if (screen.live?.latitude !== undefined) {
            actual.push({
                latitude: screen.live.latitude,
                longitude: screen.live.longitude,
            });
        }
        const timeline = buildTimeline(
            screen.shipment,
            screen.readings,
            screen.live,
        );
        const liveLabel = !precise
            ? "Approximate authorized location only"
            : screen.stream === "reconnecting"
              ? "Reconnecting to live telemetry"
              : screen.stream === "failed"
                ? "Live telemetry stopped"
                : screen.live
                  ? "Live authorized position"
                  : "Waiting for live telemetry";
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Shipment tracking
                </Title1>
                <Body1>
                    Status {screen.shipment.status} · {liveLabel}
                </Body1>
                {screen.readings.length === 0 && !screen.live ? (
                    <MessageBar>
                        <MessageBarBody>
                            No telemetry readings are available yet.
                        </MessageBarBody>
                    </MessageBar>
                ) : null}
                {screen.stream === "failed" ? (
                    <Button
                        className={styles.touchTarget}
                        onClick={() =>
                            navigate(
                                `/app/tracking/${businessId}/shipments/${shipmentId}`,
                            )
                        }
                    >
                        Retry live updates
                    </Button>
                ) : null}
                <svg
                    aria-label="Approved route versus actual path"
                    height="220"
                    role="img"
                    viewBox="0 0 400 220"
                    width="100%"
                >
                    <polyline
                        fill="none"
                        points={polylinePoints(approved)}
                        stroke="#888"
                        strokeDasharray="8 6"
                        strokeWidth="4"
                    />
                    <polyline
                        fill="none"
                        points={polylinePoints(actual)}
                        stroke="currentColor"
                        strokeWidth="5"
                    />
                </svg>
                <section aria-label="Path summary">
                    <Body1>
                        Approved corridor is the dashed path. Actual movement is
                        the solid path. Latest authorized position{" "}
                        {displayCoordinate(screen.live?.latitude, precise)},{" "}
                        {displayCoordinate(screen.live?.longitude, precise)}.
                    </Body1>
                </section>
                <ol aria-label="Shipment timeline">
                    {timeline.map((item) => (
                        <li key={item.id}>
                            {item.at} · {item.summary}
                        </li>
                    ))}
                </ol>
                <Button
                    className={styles.touchTarget}
                    onClick={() => navigate(`/app/tracking/${businessId}`)}
                >
                    Track another shipment
                </Button>
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Track a shipment
            </Title1>
            <form noValidate onSubmit={(event) => void onOpen(event)}>
                <Field label="Shipment ID" required>
                    <Input
                        className={styles.touchTarget}
                        onChange={(_, data) => setShipmentInput(data.value)}
                        value={shipmentInput}
                    />
                </Field>
                <Button
                    appearance="primary"
                    className={styles.touchTarget}
                    type="submit"
                >
                    Open tracking
                </Button>
            </form>
        </Card>
    );
}

function mergeReading(
    readings: ReadingResponse[],
    live: LivePositionResponse,
): ReadingResponse[] {
    if (
        live.readingId &&
        readings.some((reading) => reading.readingId === live.readingId)
    ) {
        return readings;
    }
    return [
        ...readings,
        {
            readingId: live.readingId,
            deviceId: live.deviceId,
            recordedAt: live.recordedAt,
            latitude: live.latitude,
            longitude: live.longitude,
            speedKilometresPerHour: live.speedKilometresPerHour,
            networkStatus: live.networkStatus,
        },
    ];
}

function errorScreen(
    error: ApiProblem | undefined,
    fallback: string,
): Extract<Screen, { kind: "error" }> {
    if (isForbiddenTracking(error)) {
        return {
            kind: "error",
            retryable: false,
            title: problemMessage(error, "Access denied"),
        };
    }
    return {
        kind: "error",
        retryable: isRetryableTrackingProblem(error) || !error,
        title: problemMessage(error, fallback),
    };
}
