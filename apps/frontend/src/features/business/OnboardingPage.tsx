import { useState } from "react";
import { useNavigate } from "react-router";

import { api } from "../../shared/api/client";
import { ApiError } from "../../shared/api/errors";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { TopBar } from "../../ui";
import { useSession } from "../access/session";

export function OnboardingPage() {
    const navigate = useNavigate();
    const { refresh } = useSession();
    const [registration, setRegistration] = useState("");
    const [legalName, setLegalName] = useState("");
    const [unconfirmed, setUnconfirmed] = useState(false);
    const [documentNote, setDocumentNote] = useState<string | null>(null);
    const [status, setStatus] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);

    async function lookup() {
        setError(null);
        setStatus("Looking up the company registry…");
        try {
            const result = api.lookupBusiness(registration);
            setLegalName(result.legalName);
            setUnconfirmed(true);
            setStatus("Unconfirmed registry values. Review before accepting.");
        } catch (caught) {
            setStatus(null);
            setError(
                caught instanceof ApiError ? caught.message : "Lookup failed.",
            );
        }
    }

    function uploadProof() {
        setError(null);
        try {
            const job = api.uploadDocument("cipc.pdf");
            const review = api.getDocument(job.id);
            setDocumentNote(
                `Document ${review.id} is ${review.confidence === "REVIEW_NEEDED" ? "ready for review" : "high confidence"} and still unconfirmed.`,
            );
        } catch (caught) {
            setError(
                caught instanceof ApiError
                    ? caught.message
                    : "Document upload failed.",
            );
        }
    }

    async function confirm() {
        setError(null);
        try {
            api.confirmBusinessProfile(registration, legalName);
            await refresh();
            navigate("/app", { replace: true });
        } catch (caught) {
            setError(
                caught instanceof ApiError
                    ? caught.message
                    : "Could not confirm.",
            );
        }
    }

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Business onboarding" />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                <p className="text-sm text-gray-600">
                    Retrieved names stay unconfirmed until you accept them. The
                    registration number is not placed on the URL.
                </p>
                <label className="block text-sm">
                    Registration number
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3 text-sm"
                        placeholder="Registration number"
                        aria-label="Registration number"
                        value={registration}
                        onChange={(event) =>
                            setRegistration(event.target.value)
                        }
                    />
                </label>
                <button
                    className="w-full h-10 rounded-lg font-semibold"
                    style={{
                        background: "var(--yellow)",
                        color: "var(--navy)",
                    }}
                    onClick={() => {
                        void lookup();
                    }}
                >
                    Look up
                </button>
                {status ? <StatusMessage>{status}</StatusMessage> : null}
                {error ? (
                    <StatusMessage tone="error">{error}</StatusMessage>
                ) : null}
                {unconfirmed ? (
                    <label className="block text-sm">
                        Legal name (unconfirmed)
                        <input
                            className="mt-1 w-full h-10 border rounded-xl px-3"
                            aria-label="Legal name"
                            value={legalName}
                            onChange={(event) =>
                                setLegalName(event.target.value)
                            }
                        />
                    </label>
                ) : null}
                {unconfirmed ? (
                    <button
                        className="w-full h-10 rounded-lg text-sm font-semibold border"
                        onClick={uploadProof}
                    >
                        Upload registration document
                    </button>
                ) : null}
                {documentNote ? (
                    <StatusMessage>{documentNote}</StatusMessage>
                ) : null}
                {unconfirmed ? (
                    <button
                        className="w-full h-10 rounded-lg text-white font-semibold"
                        style={{ background: "var(--blue)" }}
                        onClick={confirm}
                    >
                        Accept and continue
                    </button>
                ) : null}
            </div>
        </div>
    );
}
