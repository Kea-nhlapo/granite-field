import { useState, type FormEvent } from "react";
import { AnimatePresence } from "motion/react";
import { ChevronDown } from "lucide-react";
import { QRCodeSVG } from "qrcode.react";
import type { Navigate } from "./types";
import {
    Badge,
    BottomDock,
    MessageBar,
    PrimaryBtn,
    SecondaryBtn,
    SectionCard,
    TopBar,
} from "./ui";
import {
    CheckmarkIcon,
    ChevronRightIcon,
    QrCodeIcon,
    ShieldCheckmarkIcon,
} from "./icons";
import { m, springs } from "./motion";
import {
    escrowGet,
    escrowRelease,
    handoverConfirm,
    handoverGet,
    handoverIssue,
} from "./shared/api/app-api";
import type {
    ApiProblem,
    ChallengeResponse,
    IssuedChallengeResponse,
} from "./shared/api/generated";
import { SandboxWalletCard } from "./SandboxWalletCard";

const DEMO_BUSINESS_ID = "00000000-0000-4000-8000-000000000101";
const DEMO_SHIPMENT_ID = "00000000-0000-4000-8000-000000000201";
const DEMO_ORDER_ID = "00000000-0000-4000-8000-000000000301";
const LUNGILE_USER_ID = "6c756e67-696c-456d-8000-000000000001";

export function TrackScreen({ navigate }: { navigate: Navigate }) {
    const [showAudit, setShowAudit] = useState(false);

    return (
        <>
            <TopBar title="Consignment Radar" />
            <div
                className="flex-1 fluent-scroll overflow-y-auto p-4 space-y-4"
                style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
            >
                <SandboxWalletCard />
                {/* Active Shipment Status */}
                <SectionCard
                    className="p-4"
                    style={{
                        borderLeftWidth: "4px",
                        borderLeftColor: "#003E85",
                    }}
                >
                    <div className="flex items-center justify-between">
                        <div>
                            <p className="app-caption text-[#595959]">
                                SB-2026-9901
                            </p>
                            <p className="app-heading mt-0.5">
                                Germiston Hub → Soweto CBD
                            </p>
                        </div>
                        <Badge label="In Transit" color="brand" />
                    </div>

                    <div className="mt-3">
                        <div className="flex justify-between app-caption mb-1">
                            <span className="text-[#595959]">
                                Corridor Completion
                            </span>
                            <span className="app-caption-strong text-[#003E85]">
                                68% (67 km left)
                            </span>
                        </div>
                        <div className="w-full bg-[#E5E7EB] rounded-full h-1.5 overflow-hidden">
                            <div
                                className="h-1.5 rounded-full"
                                style={{
                                    width: "68%",
                                    backgroundColor:
                                        "var(--momo-blue, #003E85)",
                                }}
                            />
                        </div>
                    </div>

                    <p className="app-caption text-[#595959] mt-2">
                        Target ETA:{" "}
                        <strong className="app-caption-strong text-[#002B49]">
                            14:30 SAST
                        </strong>{" "}
                        • Driver: Sipho Mthembu (T-JHB-0047)
                    </p>
                </SectionCard>

                {/* Timeline */}
                <SectionCard className="p-4">
                    <p className="app-heading mb-3">
                        Live Telematics Milestones
                    </p>
                    <div className="relative pl-6 space-y-4">
                        {/* Timeline vertical bar */}
                        <div
                            className="absolute left-2.5 top-2 bottom-2 w-0.5"
                            style={{
                                backgroundColor:
                                    "var(--fluent-stroke-divider, #E5E7EB)",
                            }}
                        />

                        {[
                            {
                                time: "07:30",
                                label: "Consignment sealed at Germiston Hub",
                                state: "done",
                            },
                            {
                                time: "09:14",
                                label: "En route via N1 + R21 Bypass",
                                state: "done",
                            },
                            {
                                time: "10:00",
                                label: "Checkpoint: N12/R21 Telematics verified",
                                state: "done",
                            },
                            {
                                time: "14:30",
                                label: "Scheduled arrival at Soweto Distribution Node",
                                state: "active",
                            },
                            {
                                time: "~15:00",
                                label: "Handover proof-of-delivery scan",
                                state: "pending",
                            },
                        ].map((e, i) => (
                            <div
                                key={i}
                                className="relative flex items-start gap-3"
                            >
                                <div
                                    className={`absolute -left-6 w-5 h-5 rounded-full flex items-center justify-center shrink-0 z-10 ${
                                        e.state === "done"
                                            ? "bg-[#00875A] text-white"
                                            : e.state === "active"
                                              ? "bg-[#003E85] text-white ring-2 ring-[#C7E0F4]"
                                              : "bg-white border border-[#D1D5DB] text-[#8E8E93]"
                                    }`}
                                >
                                    {e.state === "done" ? (
                                        <CheckmarkIcon size={12} />
                                    ) : (
                                        <span className="w-1.5 h-1.5 rounded-full bg-current" />
                                    )}
                                </div>
                                <div className="flex-1 min-w-0">
                                    <p
                                        className={`app-body leading-snug ${
                                            e.state === "done"
                                                ? "text-[#1A1A1A] font-medium"
                                                : e.state === "active"
                                                  ? "text-[#003E85] font-semibold"
                                                  : "text-[#8E8E93]"
                                        }`}
                                    >
                                        {e.label}
                                    </p>
                                    <p className="app-micro mt-0.5">
                                        {e.time} SAST
                                    </p>
                                </div>
                            </div>
                        ))}
                    </div>
                </SectionCard>

                {/* QR Verification Card */}
                <button
                    onClick={() => navigate({ id: "track_qr" })}
                    className="w-full bg-white rounded-xl p-3.5 border border-[#E5E7EB] hover:border-[#003E85] hover:shadow-xs active:bg-[#F8F9FA] transition-all flex items-center gap-3.5 text-left"
                >
                    <div className="w-10 h-10 rounded-lg bg-[#EBF3FC] text-[#003E85] flex items-center justify-center shrink-0 border border-[#C7E0F4]">
                        <QrCodeIcon size={22} />
                    </div>
                    <div className="flex-1 min-w-0">
                        <p className="app-heading">
                            Delivery Verification QR Token
                        </p>
                        <p className="app-caption text-[#595959] mt-0.5">
                            Issue an expiring code for the receiving party to
                            confirm the handover
                        </p>
                    </div>
                    <ChevronRightIcon size={18} className="text-[#8E8E93]" />
                </button>

                {/* Telematics Audit — collapsed by default, only needed if something looks off */}
                <SectionCard className="overflow-hidden">
                    <button
                        onClick={() => setShowAudit((v) => !v)}
                        className="w-full flex items-center justify-between p-4 text-left"
                    >
                        <div className="flex items-center gap-1.5">
                            <ShieldCheckmarkIcon
                                size={16}
                                className="text-[#003E85]"
                            />
                            <span className="app-heading">
                                Cryptographic Trip Telematics Log
                            </span>
                        </div>
                        <m.span
                            animate={{ rotate: showAudit ? 180 : 0 }}
                            transition={springs.quick}
                        >
                            <ChevronDown size={18} className="text-[#8E8E93]" />
                        </m.span>
                    </button>

                    <AnimatePresence initial={false}>
                        {showAudit && (
                            <m.div
                                initial={{ height: 0, opacity: 0 }}
                                animate={{ height: "auto", opacity: 1 }}
                                exit={{ height: 0, opacity: 0 }}
                                transition={springs.snappy}
                                className="overflow-hidden"
                            >
                                <div className="px-4 pb-4 divide-y divide-[#E5E7EB]">
                                    {[
                                        {
                                            event: "Departure QR cryptographic seal signed",
                                            time: "07:31",
                                            ok: true,
                                        },
                                        {
                                            event: "Minor corridor speed variance logged (N12)",
                                            time: "08:02",
                                            ok: false,
                                        },
                                        {
                                            event: "Telemetry re-aligned: authorized refuel stop",
                                            time: "08:08",
                                            ok: true,
                                        },
                                    ].map((r, i) => (
                                        <div
                                            key={i}
                                            className="py-2 flex items-center justify-between gap-2"
                                        >
                                            <div className="flex items-center gap-2 min-w-0">
                                                {r.ok ? (
                                                    <span className="w-1.5 h-1.5 rounded-full bg-[#00875A] shrink-0" />
                                                ) : (
                                                    <span className="w-1.5 h-1.5 rounded-full bg-[#F57C00] shrink-0" />
                                                )}
                                                <span className="app-caption text-[#595959] truncate">
                                                    {r.event}
                                                </span>
                                            </div>
                                            <span className="app-micro shrink-0">
                                                {r.time}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            </m.div>
                        )}
                    </AnimatePresence>
                </SectionCard>
            </div>
        </>
    );
}

type DeliveryContext = {
    businessId: string;
    shipmentId: string;
    deliveryOrderId: string;
    counterpartyUserId: string;
};

type DeliveryHandoverLink = {
    shipmentId: string;
    token: string;
    expectedQuantity?: number;
};

const UUID_PATTERN =
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function readDeliveryHandoverLink(
    fragment = window.location.hash,
): DeliveryHandoverLink | null {
    const params = new URLSearchParams(fragment.replace(/^#/, ""));
    const shipmentId = params.get("handoverShipmentId")?.trim();
    const token = params.get("handoverToken")?.trim();
    const expectedQuantityValue = params.get("expectedQuantity");
    const expectedQuantity = expectedQuantityValue
        ? Number(expectedQuantityValue)
        : undefined;

    if (!shipmentId || !token || !UUID_PATTERN.test(shipmentId)) {
        return null;
    }

    return {
        shipmentId,
        token,
        expectedQuantity:
            expectedQuantity !== undefined &&
            Number.isFinite(expectedQuantity) &&
            expectedQuantity >= 0
                ? expectedQuantity
                : undefined,
    };
}

export function buildDeliveryHandoverLink(
    shipmentId: string,
    token: string,
    expectedQuantity?: number,
) {
    const link = new URL(
        window.location.pathname || "/",
        window.location.origin,
    );
    const fragment = new URLSearchParams();
    fragment.set("handoverShipmentId", shipmentId);
    fragment.set("handoverToken", token);
    if (expectedQuantity !== undefined) {
        fragment.set("expectedQuantity", String(expectedQuantity));
    }
    link.hash = fragment.toString();
    return link.toString();
}

function problemDetail(error: unknown, fallback: string) {
    const problem = error as Partial<ApiProblem> | undefined;
    return problem?.detail?.trim() || fallback;
}

function browserLocation(): Promise<{ latitude: number; longitude: number }> {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) {
            reject(new Error("Location is not available on this device."));
            return;
        }
        navigator.geolocation.getCurrentPosition(
            ({ coords }) =>
                resolve({
                    latitude: coords.latitude,
                    longitude: coords.longitude,
                }),
            () =>
                reject(
                    new Error(
                        "Location permission is required to verify this handover.",
                    ),
                ),
            { enableHighAccuracy: true, maximumAge: 15_000, timeout: 10_000 },
        );
    });
}

const emptyContext: DeliveryContext = {
    businessId: DEMO_BUSINESS_ID,
    shipmentId: DEMO_SHIPMENT_ID,
    deliveryOrderId: DEMO_ORDER_ID,
    counterpartyUserId: LUNGILE_USER_ID,
};

export function QRScreen({ onBack }: { onBack: () => void }) {
    const [scanLink] = useState(() => readDeliveryHandoverLink());
    const [context, setContext] = useState<DeliveryContext>(emptyContext);
    const [issued, setIssued] = useState<IssuedChallengeResponse | null>(null);
    const [verification, setVerification] = useState<ChallengeResponse | null>(
        null,
    );
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [paymentStatus, setPaymentStatus] = useState<string>();

    const qrPayload = issued?.qrPayload;
    const challenge = issued?.challenge;
    const qrLink =
        qrPayload && challenge?.shipmentId
            ? buildDeliveryHandoverLink(
                  challenge.shipmentId,
                  qrPayload,
                  challenge.expectedQuantity,
              )
            : null;

    function updateContext(field: keyof DeliveryContext, value: string) {
        setContext((current) => ({ ...current, [field]: value.trim() }));
    }

    async function issueCode(event: FormEvent) {
        event.preventDefault();
        setError(null);
        setIssued(null);

        if (Object.values(context).some((value) => !UUID_PATTERN.test(value))) {
            setError("Enter a valid UUID in every field.");
            return;
        }

        setBusy(true);
        try {
            const result = await handoverIssue({
                path: {
                    businessId: context.businessId,
                    shipmentId: context.shipmentId,
                },
                body: {
                    deliveryOrderId: context.deliveryOrderId,
                    counterpartyUserId: context.counterpartyUserId,
                    type: "DELIVERY",
                },
            });

            if (result.error) {
                setError(
                    problemDetail(
                        result.error,
                        "The delivery QR code could not be issued.",
                    ),
                );
                return;
            }
            if (!result.data?.qrPayload || !result.data.challenge?.shipmentId) {
                setError("The server returned an incomplete QR challenge.");
                return;
            }
            setIssued(result.data);
        } catch {
            setError("The backend could not be reached. Please try again.");
        } finally {
            setBusy(false);
        }
    }

    async function confirmDelivery(event: FormEvent) {
        event.preventDefault();
        if (!scanLink) return;

        setBusy(true);
        setError(null);
        setVerification(null);
        try {
            const location = await browserLocation();
            const result = await handoverConfirm({
                body: {
                    captureMode: "ONLINE",
                    commandId: crypto.randomUUID(),
                    latitude: location.latitude,
                    longitude: location.longitude,
                    observedAt: new Date().toISOString(),
                    qrPayload: scanLink.token,
                    quantityOutcome: "MATCHED",
                },
            });

            if (result.error) {
                setError(
                    problemDetail(
                        result.error,
                        "The backend rejected this handover confirmation.",
                    ),
                );
                return;
            }
            if (
                !result.data ||
                !["PENDING", "COMPLETED", "DISPUTED"].includes(
                    result.data.state ?? "",
                )
            ) {
                setError("The handover has not been verified by the backend.");
                return;
            }

            setVerification(result.data);
            window.history.replaceState(null, "", window.location.pathname);
        } catch (caught) {
            setError(
                caught instanceof Error
                    ? caught.message
                    : "The backend could not be reached. Please try again.",
            );
        } finally {
            setBusy(false);
        }
    }

    async function confirmSmeSide() {
        if (!issued?.qrPayload) return;
        setBusy(true);
        setError(null);
        try {
            const location = await browserLocation();
            const result = await handoverConfirm({
                body: {
                    captureMode: "ONLINE",
                    commandId: crypto.randomUUID(),
                    latitude: location.latitude,
                    longitude: location.longitude,
                    observedAt: new Date().toISOString(),
                    qrPayload: issued.qrPayload,
                    quantityOutcome: "MATCHED",
                },
            });
            if (result.error || !result.data) {
                setError(
                    problemDetail(
                        result.error,
                        "The SME signature was rejected.",
                    ),
                );
                return;
            }
            setIssued((current) =>
                current ? { ...current, challenge: result.data } : current,
            );
        } catch {
            setError("The backend could not be reached. Please try again.");
        } finally {
            setBusy(false);
        }
    }

    async function refreshSignatures() {
        const challengeId = issued?.challenge?.challengeId;
        if (!challengeId) return;
        setBusy(true);
        setError(null);
        try {
            const result = await handoverGet({
                path: {
                    businessId: context.businessId,
                    shipmentId: context.shipmentId,
                    challengeId,
                },
            });
            if (result.error || !result.data) {
                setError(
                    problemDetail(
                        result.error,
                        "The signatures could not be refreshed.",
                    ),
                );
                return;
            }
            setIssued((current) =>
                current ? { ...current, challenge: result.data } : current,
            );
        } finally {
            setBusy(false);
        }
    }

    async function releasePayment() {
        setBusy(true);
        setError(null);
        try {
            const current = await escrowGet({
                path: { shipmentId: context.shipmentId },
                query: { businessId: context.businessId },
            });
            if (!current.data?.agreedAmount) {
                setError("This shipment has no releasable escrow amount.");
                return;
            }
            const result = await escrowRelease({
                path: { shipmentId: context.shipmentId },
                body: {
                    businessId: context.businessId,
                    requestId: crypto.randomUUID(),
                    resolvedAmount: current.data.agreedAmount,
                },
            });
            if (result.error || !result.data) {
                setError(
                    problemDetail(
                        result.error,
                        "Payment could not be released.",
                    ),
                );
                return;
            }
            setPaymentStatus(result.data.status);
            window.dispatchEvent(new Event("trademesh:wallet-updated"));
        } finally {
            setBusy(false);
        }
    }

    return (
        <>
            <TopBar
                title={scanLink ? "Confirm Delivery" : "Delivery QR"}
                onBack={onBack}
            />
            <div
                className="flex-1 fluent-scroll overflow-y-auto p-5 space-y-4"
                style={{ background: "var(--fluent-bg-canvas, #F8F9FA)" }}
            >
                <SandboxWalletCard />
                {scanLink ? (
                    <form onSubmit={confirmDelivery} className="space-y-4">
                        <MessageBar intent="info">
                            Confirm the physical delivery only after checking
                            the goods. Your account and current location are
                            sent to the server as evidence.
                        </MessageBar>
                        <SectionCard className="p-4 space-y-3">
                            <div>
                                <p className="app-caption text-[#595959]">
                                    Shipment
                                </p>
                                <p className="app-caption-strong break-all">
                                    {scanLink.shipmentId}
                                </p>
                            </div>
                            <p className="app-caption text-[#595959]">
                                Sign with your supplier account. The SME must
                                sign separately before payment can move.
                            </p>
                        </SectionCard>
                        {error && (
                            <MessageBar intent="error">{error}</MessageBar>
                        )}
                        {verification && (
                            <MessageBar
                                intent={
                                    verification.state === "COMPLETED"
                                        ? "success"
                                        : "warning"
                                }
                            >
                                <strong>
                                    {verification.state === "COMPLETED"
                                        ? "Both parties signed the handoff."
                                        : "Your signature was saved. Waiting for the SME."}
                                </strong>{" "}
                                Evidence reference: {verification.challengeId}.
                                Payment release remains a separate decision.
                            </MessageBar>
                        )}
                        {!verification && (
                            <PrimaryBtn
                                type="submit"
                                disabled={busy}
                                label={
                                    busy
                                        ? "Checking location and confirming…"
                                        : "Confirm delivery"
                                }
                            />
                        )}
                    </form>
                ) : (
                    <form onSubmit={issueCode} className="space-y-4">
                        <MessageBar intent="info">
                            Connect an active delivery. TradeMesh will issue a
                            signed, expiring code tied to this shipment and the
                            intended receiving user.
                        </MessageBar>
                        {!qrLink ? (
                            <SectionCard className="p-4 space-y-3">
                                {(
                                    [
                                        ["businessId", "Business ID"],
                                        ["shipmentId", "Shipment ID"],
                                        [
                                            "deliveryOrderId",
                                            "Delivery order ID",
                                        ],
                                        [
                                            "counterpartyUserId",
                                            "Receiving user ID",
                                        ],
                                    ] as const
                                ).map(([field, label]) => (
                                    <label key={field} className="block">
                                        <span className="app-caption-strong">
                                            {label}
                                        </span>
                                        <input
                                            aria-label={label}
                                            required
                                            value={context[field]}
                                            onChange={(event) =>
                                                updateContext(
                                                    field,
                                                    event.target.value,
                                                )
                                            }
                                            placeholder="00000000-0000-0000-0000-000000000000"
                                            className="mt-1.5 w-full h-10 rounded-lg border border-[#D1D5DB] px-3 text-sm font-mono"
                                        />
                                    </label>
                                ))}
                            </SectionCard>
                        ) : (
                            <SectionCard className="p-5 flex flex-col items-center text-center gap-3">
                                <div className="bg-white p-3 border border-[#E5E7EB] rounded-xl">
                                    <QRCodeSVG
                                        value={qrLink}
                                        size={224}
                                        level="M"
                                        marginSize={2}
                                        bgColor="#FFFFFF"
                                        fgColor="#002B49"
                                        title="Secure delivery QR code"
                                    />
                                </div>
                                <Badge label="Server issued" color="success" />
                                <p className="app-heading">
                                    Ask the receiving user to scan this code
                                </p>
                                <p className="app-caption text-[#595959]">
                                    Expires {challenge?.expiresAt ?? "soon"}.
                                    The code contains no business or recipient
                                    details.
                                </p>
                                <p className="app-caption-strong text-[#002B49]">
                                    {challenge?.confirmations?.length ?? 0}/2
                                    signatures · {challenge?.state ?? "PENDING"}
                                </p>
                            </SectionCard>
                        )}
                        {error && (
                            <MessageBar intent="error">{error}</MessageBar>
                        )}
                        {!qrLink && (
                            <PrimaryBtn
                                type="submit"
                                disabled={busy}
                                label={
                                    busy
                                        ? "Requesting secure code…"
                                        : "Issue secure QR code"
                                }
                            />
                        )}
                        {qrLink && (
                            <div className="grid grid-cols-2 gap-2">
                                <SecondaryBtn
                                    label="Sign SME side"
                                    onClick={() => void confirmSmeSide()}
                                    disabled={busy}
                                />
                                <SecondaryBtn
                                    label="Refresh signatures"
                                    onClick={() => void refreshSignatures()}
                                    disabled={busy}
                                />
                            </div>
                        )}
                        {challenge?.state === "COMPLETED" && (
                            <PrimaryBtn
                                label={
                                    paymentStatus === "RELEASED"
                                        ? "Payment released"
                                        : "Release supplier payment"
                                }
                                onClick={() => void releasePayment()}
                                disabled={busy || paymentStatus === "RELEASED"}
                            />
                        )}
                    </form>
                )}
            </div>

            <BottomDock>
                <SecondaryBtn
                    label="Return to Live Tracking"
                    onClick={onBack}
                />
            </BottomDock>
        </>
    );
}
