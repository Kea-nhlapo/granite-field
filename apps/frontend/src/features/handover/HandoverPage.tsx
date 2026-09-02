import { useState } from "react";

import { api } from "../../shared/api/client";
import { ApiError } from "../../shared/api/errors";
import type {
    HandoverChallenge,
    HandoverReceipt,
} from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { PrimaryBtn, SectionCard, TopBar } from "../../ui";

export function HandoverPage({ onBack }: { onBack: () => void }) {
    const [kind, setKind] = useState<"COLLECTION" | "DELIVERY">("DELIVERY");
    const [challenge, setChallenge] = useState<HandoverChallenge | null>(null);
    const [quantity, setQuantity] = useState("50");
    const [dispute, setDispute] = useState("");
    const [fallback, setFallback] = useState(false);
    const [receipt, setReceipt] = useState<HandoverReceipt | null>(null);
    const [error, setError] = useState<string | null>(null);

    function requestChallenge() {
        setError(null);
        setReceipt(null);
        try {
            setChallenge(api.createHandoverChallenge("SB-2026-9901", kind));
        } catch (caught) {
            setError(
                caught instanceof ApiError
                    ? caught.message
                    : "Could not start handover.",
            );
        }
    }

    function confirm() {
        if (!challenge) return;
        setError(null);
        try {
            setReceipt(
                api.confirmHandover({
                    challengeId: challenge.id,
                    quantity,
                    fallback,
                    disputeNote: dispute || undefined,
                }),
            );
        } catch (caught) {
            setError(
                caught instanceof ApiError
                    ? caught.message
                    : "Handover failed.",
            );
        }
    }

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Handover QR" onBack={onBack} />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                <fieldset className="text-sm">
                    <legend>Handover type</legend>
                    <label className="mr-4">
                        <input
                            type="radio"
                            name="kind"
                            checked={kind === "COLLECTION"}
                            onChange={() => setKind("COLLECTION")}
                        />{" "}
                        Collection
                    </label>
                    <label>
                        <input
                            type="radio"
                            name="kind"
                            checked={kind === "DELIVERY"}
                            onChange={() => setKind("DELIVERY")}
                        />{" "}
                        Delivery
                    </label>
                </fieldset>
                <PrimaryBtn
                    label="Request short-lived QR"
                    onClick={requestChallenge}
                />
                {challenge ? (
                    <SectionCard className="p-4 space-y-2">
                        <p className="text-sm font-semibold">
                            Show this code to the scanner
                        </p>
                        <p className="font-mono-data text-lg">
                            {challenge.displayCode}
                        </p>
                        <p className="text-xs text-gray-500">
                            Expires {challenge.expiresAt}. Signing secrets are
                            never shown.
                        </p>
                    </SectionCard>
                ) : null}
                <label className="flex items-center gap-2 text-sm">
                    <input
                        type="checkbox"
                        checked={fallback}
                        onChange={(event) => setFallback(event.target.checked)}
                    />
                    Camera unavailable — use accessible fallback code
                </label>
                <label className="block text-sm">
                    Quantity confirmation
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3"
                        aria-label="Quantity confirmation"
                        value={quantity}
                        onChange={(event) => setQuantity(event.target.value)}
                    />
                </label>
                <label className="block text-sm">
                    Dispute notes
                    <textarea
                        className="mt-1 w-full border rounded-xl px-3 py-2"
                        aria-label="Dispute notes"
                        value={dispute}
                        onChange={(event) => setDispute(event.target.value)}
                    />
                </label>
                <PrimaryBtn label="Confirm handover" onClick={confirm} />
                {receipt ? (
                    <StatusMessage>
                        {`Server-confirmed receipt ${receipt.id} at ${receipt.confirmedAt}.`}
                    </StatusMessage>
                ) : null}
                {error ? (
                    <StatusMessage tone="error">{error}</StatusMessage>
                ) : null}
            </div>
        </div>
    );
}
