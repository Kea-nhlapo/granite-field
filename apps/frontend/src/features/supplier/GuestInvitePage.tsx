import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router";

import { api } from "../../shared/api/client";
import { ApiError } from "../../shared/api/errors";
import type { InvitationStatus } from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { TopBar } from "../../ui";

export function GuestInvitePage() {
    const { token } = useParams();
    const tokenRef = useRef(token ?? "");
    const [status, setStatus] = useState<InvitationStatus | null>(null);
    const [summary, setSummary] = useState("");
    const [error, setError] = useState<string | null>(null);
    const [receipt, setReceipt] = useState<string | null>(null);
    const [extracted, setExtracted] = useState<string | null>(null);

    useEffect(() => {
        tokenRef.current = token ?? "";
        try {
            const invitation = api.getInvitation(tokenRef.current);
            setStatus(invitation.status);
            setSummary(invitation.requestSummary);
        } catch (caught) {
            setError(
                caught instanceof ApiError ? caught.message : "Invite failed.",
            );
        }
    }, [token]);

    function reviewDocument() {
        setError(null);
        try {
            const job = api.uploadDocument("quote.pdf");
            const document = api.getDocument(job.id);
            const first = document.lines[0];
            setExtracted(
                first
                    ? `${first.label}: extracted ${first.extracted}`
                    : "No lines extracted",
            );
        } catch (caught) {
            setError(
                caught instanceof ApiError
                    ? caught.message
                    : "Document review failed.",
            );
        }
    }

    function submit() {
        setError(null);
        try {
            const result = api.submitGuestQuote(tokenRef.current);
            setReceipt(result.quoteId);
            setStatus("USED");
        } catch (caught) {
            setError(
                caught instanceof ApiError ? caught.message : "Submit failed.",
            );
        }
    }

    return (
        <main className="min-h-dvh bg-[var(--surface)]">
            <TopBar title="Supplier invitation" />
            <div className="p-4 space-y-4 max-w-md mx-auto">
                {status && status !== "VALID" ? (
                    <StatusMessage>
                        {`This invitation is ${status.toLowerCase()}. It cannot be used to open the signed-in workspace.`}
                    </StatusMessage>
                ) : null}
                {status === "VALID" && !receipt ? (
                    <>
                        <p className="text-sm">{summary}</p>
                        <button
                            className="w-full h-10 rounded-lg text-sm font-semibold border"
                            onClick={reviewDocument}
                        >
                            Review extracted quote
                        </button>
                        {extracted ? (
                            <StatusMessage>{extracted}</StatusMessage>
                        ) : null}
                        <button
                            className="w-full h-10 rounded-lg font-semibold"
                            style={{
                                background: "var(--yellow)",
                                color: "var(--navy)",
                            }}
                            onClick={submit}
                        >
                            Submit quote
                        </button>
                    </>
                ) : null}
                {receipt ? (
                    <StatusMessage>
                        {`Quote ${receipt} received. You can create an account now if you want — it is optional.`}
                    </StatusMessage>
                ) : null}
                {error ? (
                    <StatusMessage tone="error">{error}</StatusMessage>
                ) : null}
                <Link className="text-sm font-semibold" to="/login">
                    Shop sign-in
                </Link>
            </div>
        </main>
    );
}
