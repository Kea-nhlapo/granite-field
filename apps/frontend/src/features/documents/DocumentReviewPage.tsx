import { useState } from "react";

import { api } from "../../shared/api/client";
import { ApiError } from "../../shared/api/errors";
import type {
    DocumentReview,
    MismatchEvidence,
} from "../../shared/api/generated";
import { StatusMessage } from "../../shared/components/StatusMessage";
import { PrimaryBtn, SectionCard, TopBar } from "../../ui";

export function DocumentReviewPage({ onBack }: { onBack: () => void }) {
    const [phase, setPhase] = useState<"upload" | "processing" | "review">(
        "upload",
    );
    const [review, setReview] = useState<DocumentReview | null>(null);
    const [mismatch, setMismatch] = useState<MismatchEvidence | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [fileLabel, setFileLabel] = useState("invoice.pdf");

    function upload() {
        setError(null);
        setPhase("processing");
        try {
            const job = api.uploadDocument(fileLabel);
            if (job.state === "FAILED") {
                throw new ApiError(
                    "SERVER_ERROR",
                    "The document could not be parsed. Try another file.",
                    500,
                );
            }
            const document = api.getDocument(job.id);
            setReview(document);
            setMismatch(api.getMismatch("mm-1"));
            setPhase("review");
        } catch (caught) {
            setPhase("upload");
            setError(
                caught instanceof ApiError ? caught.message : "Upload failed.",
            );
        }
    }

    function correct(lineId: string) {
        if (!review) return;
        const next = api.correctExtraction(lineId, "30");
        setReview(next);
    }

    return (
        <div className="flex-1 flex flex-col overflow-hidden">
            <TopBar title="Document review" onBack={onBack} />
            <div className="flex-1 phone-scroll overflow-y-auto p-4 space-y-4">
                <p className="text-xs text-gray-500">
                    Upload limit 10 MB. PDF, JPG, or PNG only.
                </p>
                {phase === "upload" ? (
                    <>
                        <label className="block text-sm">
                            File name
                            <input
                                className="mt-1 w-full h-10 border rounded-xl px-3"
                                aria-label="File name"
                                value={fileLabel}
                                onChange={(event) =>
                                    setFileLabel(event.target.value)
                                }
                            />
                        </label>
                        <PrimaryBtn label="Upload invoice" onClick={upload} />
                    </>
                ) : null}
                {phase === "processing" ? (
                    <StatusMessage>Reading invoice…</StatusMessage>
                ) : null}
                {error ? (
                    <StatusMessage tone="error">{error}</StatusMessage>
                ) : null}
                {review ? (
                    <SectionCard className="p-4 space-y-3">
                        <p className="text-sm font-semibold">
                            Extraction{" "}
                            {review.confidence === "REVIEW_NEEDED"
                                ? "needs review"
                                : "is high confidence"}
                        </p>
                        {review.lines.map((line) => (
                            <div key={line.id} className="text-xs space-y-1">
                                <p className="font-semibold">{line.label}</p>
                                <p>
                                    Original extracted value: {line.extracted}
                                </p>
                                <p>Current value: {line.current}</p>
                                <button
                                    className="text-xs font-semibold"
                                    style={{ color: "var(--blue)" }}
                                    onClick={() => correct(line.id)}
                                >
                                    Correct line
                                </button>
                            </div>
                        ))}
                    </SectionCard>
                ) : null}
                {mismatch ? (
                    <SectionCard className="p-4 space-y-2">
                        <p className="text-sm font-semibold">
                            Mismatch evidence
                        </p>
                        <p className="text-xs">
                            {mismatch.left.source}: {mismatch.left.value}
                        </p>
                        <p className="text-xs">
                            {mismatch.right.source}: {mismatch.right.value}
                        </p>
                        <p className="text-xs text-gray-500">
                            This is a mismatch indicator, not an accusation.
                        </p>
                    </SectionCard>
                ) : null}
            </div>
        </div>
    );
}
