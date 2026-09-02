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
import { useEffect, useRef, useState, type FormEvent } from "react";
import { useLocation, useNavigate, useParams } from "react-router-dom";

import { useAccessStyles } from "../access/access.styles";
import { confirmHandover, issueChallenge, loadChallenge } from "./handover-api";
import {
    handoverFailureTitle,
    isRetryableHandoverProblem,
} from "./handover-errors";
import { qrModules } from "./qr-modules";
import { mockShipmentId } from "../../shared/api/mocks/tracking-handlers";
import { mockCounterpartyUserId } from "../../shared/api/mocks/handover-handlers";
import type {
    ApiProblem,
    ChallengeResponse,
    IssueChallengeRequest,
} from "../../shared/api/generated";

type Screen =
    | { kind: "form" }
    | { kind: "loading" }
    | {
          kind: "issued";
          challenge: ChallengeResponse;
          qrPayload?: string;
      }
    | { kind: "receipt"; challenge: ChallengeResponse }
    | { kind: "error"; title: string; retryable: boolean };

type LocationState = { qrPayload?: string };

export default function HandoverPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const location = useLocation();
    const { businessId = "", shipmentId, challengeId } = useParams();
    const issuedPayload = (location.state as LocationState | null)?.qrPayload;
    const confirmCommand = useRef(crypto.randomUUID());
    const [screen, setScreen] = useState<Screen>(
        challengeId ? { kind: "loading" } : { kind: "form" },
    );
    const [handoverType, setHandoverType] =
        useState<IssueChallengeRequest["type"]>("COLLECTION");
    const [shipmentInput, setShipmentInput] = useState(
        shipmentId ?? mockShipmentId,
    );
    const [counterpartyUserId, setCounterpartyUserId] = useState(
        mockCounterpartyUserId,
    );
    const [deliveryOrderId, setDeliveryOrderId] = useState("");
    const [fallbackCode, setFallbackCode] = useState("");
    const [quantityOutcome, setQuantityOutcome] = useState<
        "MATCHED" | "DISPUTED"
    >("MATCHED");
    const [quantityNote, setQuantityNote] = useState("");
    const [cameraState, setCameraState] = useState<"idle" | "denied" | "ready">(
        "idle",
    );

    useEffect(() => {
        if (!businessId || !shipmentId || !challengeId) {
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "loading" });
        void loadChallenge(businessId, shipmentId, challengeId).then(
            (result) => {
                if (abort.signal.aborted) {
                    return;
                }
                if (result.error || !result.data) {
                    setScreen({
                        kind: "error",
                        retryable: isRetryableHandoverProblem(
                            result.error as ApiProblem | undefined,
                        ),
                        title: handoverFailureTitle(
                            result.error as ApiProblem | undefined,
                        ),
                    });
                    return;
                }
                if (
                    result.data.state === "COMPLETED" ||
                    result.data.state === "DISPUTED"
                ) {
                    setScreen({ kind: "receipt", challenge: result.data });
                    return;
                }
                setScreen({
                    kind: "issued",
                    challenge: result.data,
                    qrPayload: issuedPayload,
                });
            },
        );
        return () => abort.abort();
    }, [businessId, challengeId, issuedPayload, shipmentId]);

    async function onIssue(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setScreen({ kind: "loading" });
        const issued = await issueChallenge(businessId, shipmentInput, {
            type: handoverType,
            counterpartyUserId,
            deliveryOrderId:
                handoverType === "DELIVERY" ? deliveryOrderId : undefined,
        });
        if (issued.error || !issued.data?.challenge?.challengeId) {
            setScreen({
                kind: "error",
                retryable: isRetryableHandoverProblem(
                    issued.error as ApiProblem | undefined,
                ),
                title: handoverFailureTitle(
                    issued.error as ApiProblem | undefined,
                ),
            });
            return;
        }
        navigate(
            `/app/handover/${businessId}/shipments/${shipmentInput}/challenges/${issued.data.challenge.challengeId}`,
            { state: { qrPayload: issued.data.qrPayload } },
        );
    }

    async function onConfirm(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (screen.kind !== "issued") {
            return;
        }
        const qrPayload = fallbackCode.trim() || screen.qrPayload;
        if (!qrPayload) {
            setScreen({
                kind: "error",
                retryable: true,
                title: "Enter the challenge code from the sender",
            });
            return;
        }
        const expected = screen.challenge.expectedLocation;
        const confirmed = await confirmHandover({
            commandId: confirmCommand.current,
            qrPayload,
            captureMode: "ONLINE",
            observedAt: new Date().toISOString(),
            latitude: expected?.latitude,
            longitude: expected?.longitude,
            quantityOutcome,
            quantityNote: quantityNote.trim() || undefined,
        });
        if (confirmed.error || !confirmed.data) {
            setScreen({
                kind: "error",
                retryable: isRetryableHandoverProblem(
                    confirmed.error as ApiProblem | undefined,
                ),
                title: handoverFailureTitle(
                    confirmed.error as ApiProblem | undefined,
                ),
            });
            return;
        }
        const refreshed = await loadChallenge(
            businessId,
            shipmentId ?? shipmentInput,
            confirmed.data.challengeId ?? challengeId ?? "",
        );
        setScreen({
            kind: "receipt",
            challenge: refreshed.data ?? confirmed.data,
        });
    }

    async function requestCamera() {
        try {
            if (!navigator.mediaDevices?.getUserMedia) {
                setCameraState("denied");
                return;
            }
            await navigator.mediaDevices.getUserMedia({ video: true });
            setCameraState("ready");
        } catch {
            setCameraState("denied");
        }
    }

    if (screen.kind === "loading") {
        return (
            <Card>
                <Spinner label="Preparing handover..." />
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
                            navigate(`/app/handover/${businessId}`);
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "receipt") {
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Server-confirmed handover
                </Title1>
                <Body1>
                    {screen.challenge.type} is {screen.challenge.state}. Receipt
                    id {screen.challenge.challengeId}.
                </Body1>
                {(screen.challenge.confirmations ?? []).map((item) => (
                    <Body1 key={item.confirmationId}>
                        {item.quantityOutcome} {item.quantityNote}
                    </Body1>
                ))}
                <Button
                    className={styles.touchTarget}
                    onClick={() =>
                        void loadChallenge(
                            businessId,
                            shipmentId ?? "",
                            screen.challenge.challengeId ?? "",
                        ).then((result) => {
                            if (result.data) {
                                setScreen({
                                    kind: "receipt",
                                    challenge: result.data,
                                });
                            }
                        })
                    }
                >
                    Refresh receipt
                </Button>
            </Card>
        );
    }

    if (screen.kind === "issued") {
        const expired =
            Boolean(screen.challenge.expiresAt) &&
            new Date(screen.challenge.expiresAt ?? 0).getTime() <= Date.now();
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    {screen.challenge.type} challenge
                </Title1>
                <Body1>
                    Expires {screen.challenge.expiresAt}. Quantity and dispute
                    notes are sent with confirmation.
                </Body1>
                {expired ? (
                    <MessageBar intent="warning">
                        <MessageBarBody>
                            This challenge has expired. Confirmation is
                            disabled.
                        </MessageBarBody>
                    </MessageBar>
                ) : null}
                {screen.qrPayload ? (
                    <ChallengeQr payload={screen.qrPayload} />
                ) : (
                    <Body1>
                        The QR payload is not kept after reload. Use the
                        fallback code from the sender.
                    </Body1>
                )}
                <Button
                    className={styles.touchTarget}
                    onClick={() => void requestCamera()}
                >
                    Use camera to scan
                </Button>
                {cameraState === "denied" ? (
                    <MessageBar>
                        <MessageBarBody>
                            Camera access is unavailable. Enter the challenge
                            code from the sender.
                        </MessageBarBody>
                    </MessageBar>
                ) : null}
                {cameraState === "ready" ? (
                    <Body1>
                        Point the camera at the challenge QR. You can also type
                        the fallback code.
                    </Body1>
                ) : null}
                <form noValidate onSubmit={(event) => void onConfirm(event)}>
                    <Field label="Fallback challenge code">
                        <Input
                            aria-label="Fallback challenge code"
                            autoComplete="off"
                            className={styles.touchTarget}
                            onChange={(_, data) => setFallbackCode(data.value)}
                            value={fallbackCode}
                        />
                    </Field>
                    <Field label="Quantity confirmation" required>
                        <select
                            aria-label="Quantity confirmation"
                            className={styles.touchTarget}
                            onChange={(event) =>
                                setQuantityOutcome(
                                    event.target.value as
                                        "MATCHED" | "DISPUTED",
                                )
                            }
                            value={quantityOutcome}
                        >
                            <option value="MATCHED">Quantities match</option>
                            <option value="DISPUTED">Dispute quantities</option>
                        </select>
                    </Field>
                    <Field label="Dispute or quantity note">
                        <Input
                            aria-label="Dispute or quantity note"
                            className={styles.touchTarget}
                            onChange={(_, data) => setQuantityNote(data.value)}
                            value={quantityNote}
                        />
                    </Field>
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={expired}
                        type="submit"
                    >
                        Confirm handover
                    </Button>
                </form>
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Issue a handover challenge
            </Title1>
            <form noValidate onSubmit={(event) => void onIssue(event)}>
                <Field label="Shipment ID" required>
                    <Input
                        aria-label="Shipment ID"
                        className={styles.touchTarget}
                        onChange={(_, data) => setShipmentInput(data.value)}
                        value={shipmentInput}
                    />
                </Field>
                <Field label="Handover type" required>
                    <select
                        aria-label="Handover type"
                        className={styles.touchTarget}
                        onChange={(event) =>
                            setHandoverType(
                                event.target
                                    .value as IssueChallengeRequest["type"],
                            )
                        }
                        value={handoverType}
                    >
                        <option value="COLLECTION">COLLECTION</option>
                        <option value="DELIVERY">DELIVERY</option>
                    </select>
                </Field>
                {handoverType === "DELIVERY" ? (
                    <Field label="Delivery order ID" required>
                        <Input
                            aria-label="Delivery order ID"
                            className={styles.touchTarget}
                            onChange={(_, data) =>
                                setDeliveryOrderId(data.value)
                            }
                            value={deliveryOrderId}
                        />
                    </Field>
                ) : null}
                <Field label="Counterparty user ID" required>
                    <Input
                        aria-label="Counterparty user ID"
                        className={styles.touchTarget}
                        onChange={(_, data) =>
                            setCounterpartyUserId(data.value)
                        }
                        value={counterpartyUserId}
                    />
                </Field>
                <Button
                    appearance="primary"
                    className={styles.touchTarget}
                    type="submit"
                >
                    Issue challenge
                </Button>
            </form>
        </Card>
    );
}

function ChallengeQr({ payload }: { payload: string }) {
    const modules = qrModules(payload);
    const size = modules.length;
    return (
        <svg
            aria-label="Handover challenge QR code"
            role="img"
            viewBox={`0 0 ${size} ${size}`}
            width="180"
        >
            {modules.flatMap((row, y) =>
                row.map((on, x) =>
                    on ? (
                        <rect
                            fill="currentColor"
                            height="1"
                            key={`${x}-${y}`}
                            width="1"
                            x={x}
                            y={y}
                        />
                    ) : null,
                ),
            )}
        </svg>
    );
}
