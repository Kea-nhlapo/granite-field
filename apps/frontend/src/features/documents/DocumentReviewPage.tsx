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

import { useAccessStyles } from "../access/access.styles";
import { validateCompanyDocument } from "../business/company-document";
import {
    compareInvoiceWithPurchaseOrder,
    confirmationOverlay,
    confirmInvoiceFields,
    loadComparison,
    loadDocument,
    needsReview,
    registerInvoiceDocument,
    uploadInvoiceDocument,
    waitForDocumentReady,
} from "./documents-api";
import type {
    ApiProblem,
    ComparisonResponse,
    DocumentResponse,
} from "../../shared/api/generated";
import { mockPurchaseOrderDocumentId } from "../../shared/api/mocks/document-handlers";

type Screen =
    | { kind: "upload" }
    | { kind: "uploading" }
    | { kind: "processing"; documentId: string }
    | {
          kind: "review";
          document: DocumentResponse;
          current: Record<string, string>;
      }
    | {
          kind: "confirming";
          document: DocumentResponse;
          current: Record<string, string>;
      }
    | {
          kind: "confirmed";
          document: DocumentResponse;
          current: Record<string, string>;
      }
    | {
          kind: "comparing";
          document: DocumentResponse;
          current: Record<string, string>;
      }
    | { kind: "compared"; comparison: ComparisonResponse }
    | { kind: "error"; title: string; retryable: boolean };

export default function DocumentReviewPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { businessId = "", documentId } = useParams();
    const [screen, setScreen] = useState<Screen>({ kind: "upload" });
    const [fileError, setFileError] = useState<string | undefined>();

    useEffect(() => {
        if (!businessId || !documentId) {
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "processing", documentId });
        void waitForDocumentReady(businessId, documentId, abort.signal).then(
            (result) => {
                if (abort.signal.aborted) {
                    return;
                }
                if (result.error || !result.document) {
                    setScreen({
                        kind: "error",
                        retryable: true,
                        title:
                            result.error?.title ??
                            "The document could not be loaded",
                    });
                    return;
                }
                setScreen(reviewScreen(result.document));
            },
        );
        return () => abort.abort();
    }, [businessId, documentId]);

    async function onUpload(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const input = event.currentTarget.elements.namedItem("invoiceDocument");
        const file =
            input instanceof HTMLInputElement ? input.files?.[0] : undefined;
        if (!file) {
            setFileError("Choose an invoice document to upload.");
            return;
        }
        const invalid = validateCompanyDocument(file);
        if (invalid) {
            setFileError(
                invalid
                    .replace("a company document", "an invoice document")
                    .replace("company document", "invoice document"),
            );
            return;
        }
        setFileError(undefined);
        setScreen({ kind: "uploading" });
        const uploaded = await uploadInvoiceDocument(businessId, file);
        if (uploaded.error || !uploaded.data?.fileId) {
            setScreen({
                kind: "error",
                retryable: true,
                title:
                    (uploaded.error as ApiProblem | undefined)?.title ??
                    "The invoice could not be uploaded",
            });
            return;
        }
        const registered = await registerInvoiceDocument(
            businessId,
            uploaded.data.fileId,
        );
        if (registered.error || !registered.data?.documentId) {
            setScreen({
                kind: "error",
                retryable: true,
                title:
                    (registered.error as ApiProblem | undefined)?.title ??
                    "The invoice could not be registered",
            });
            return;
        }
        navigate(`/app/documents/${businessId}/${registered.data.documentId}`, {
            replace: true,
        });
    }

    async function onConfirm(
        document: DocumentResponse,
        current: Record<string, string>,
    ) {
        if (!document.documentId) {
            return;
        }
        setScreen({ kind: "confirming", document, current });
        const result = await confirmInvoiceFields(
            businessId,
            document.documentId,
            Object.entries(current).map(([path, value]) => ({ path, value })),
        );
        if (result.error || !result.data) {
            setScreen({
                kind: "error",
                retryable: true,
                title:
                    (result.error as ApiProblem | undefined)?.title ??
                    "The extracted fields could not be confirmed",
            });
            return;
        }
        const reloaded = await loadDocument(businessId, document.documentId);
        setScreen(
            reviewScreen(reloaded.data ?? result.data, current, "confirmed"),
        );
    }

    async function onCompare(
        document: DocumentResponse,
        current: Record<string, string>,
    ) {
        if (!document.documentId) {
            return;
        }
        setScreen({ kind: "comparing", document, current });
        const compared = await compareInvoiceWithPurchaseOrder(
            businessId,
            document.documentId,
            mockPurchaseOrderDocumentId,
        );
        if (compared.error || !compared.data?.comparisonId) {
            setScreen({
                kind: "error",
                retryable: true,
                title:
                    (compared.error as ApiProblem | undefined)?.title ??
                    "The documents could not be compared",
            });
            return;
        }
        const loaded = await loadComparison(
            businessId,
            compared.data.comparisonId,
        );
        setScreen({
            kind: "compared",
            comparison: loaded.data ?? compared.data,
        });
    }

    return (
        <Card className={styles.card}>
            <div className={styles.stack}>{renderScreen()}</div>
        </Card>
    );

    function renderScreen() {
        if (screen.kind === "upload" || screen.kind === "uploading") {
            return (
                <form
                    className={styles.stack}
                    onSubmit={(event) => void onUpload(event)}
                >
                    <Title1 as="h1" className={styles.title}>
                        Review an invoice
                    </Title1>
                    <Body1 as="p">
                        Upload the invoice. Extracted values stay unconfirmed
                        until you save corrections.
                    </Body1>
                    {fileError ? (
                        <MessageBar intent="error" role="alert">
                            <MessageBarBody>{fileError}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <label className={styles.stack} htmlFor="invoice-document">
                        Invoice document
                    </label>
                    <input
                        accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                        className={styles.touchTarget}
                        id="invoice-document"
                        name="invoiceDocument"
                        type="file"
                    />
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={screen.kind === "uploading"}
                        type="submit"
                    >
                        Upload invoice
                    </Button>
                    {screen.kind === "uploading" ? (
                        <Spinner label="Storing the invoice..." />
                    ) : null}
                </form>
            );
        }

        if (screen.kind === "processing") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Reading the invoice
                    </Title1>
                    <Spinner label="Queued and processing extracted fields..." />
                </>
            );
        }

        if (
            screen.kind === "review" ||
            screen.kind === "confirming" ||
            screen.kind === "confirmed" ||
            screen.kind === "comparing"
        ) {
            const extracted = Object.fromEntries(
                (screen.document.extraction?.fields ?? []).map((field) => [
                    field.path ?? "",
                    field,
                ]),
            );
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        {screen.kind === "confirmed"
                            ? "Invoice confirmed"
                            : "Review extracted fields"}
                    </Title1>
                    {Object.entries(screen.current).map(([path, value]) => {
                        const field = extracted[path];
                        const flagged = needsReview(field?.confidence);
                        return (
                            <div className={styles.stack} key={path}>
                                <Field
                                    label={`${path}${flagged ? " (needs review)" : ""}`}
                                >
                                    <Input
                                        className={styles.touchTarget}
                                        disabled={screen.kind !== "review"}
                                        onChange={(_, data) => {
                                            if (screen.kind !== "review") {
                                                return;
                                            }
                                            setScreen({
                                                ...screen,
                                                current: {
                                                    ...screen.current,
                                                    [path]: data.value,
                                                },
                                            });
                                        }}
                                        value={value}
                                    />
                                </Field>
                                <Body1 as="p">
                                    Extracted value: {field?.value ?? "none"}
                                    {flagged
                                        ? `. Confidence ${field?.confidence ?? "unknown"} — not treated as certain.`
                                        : `. Confidence ${field?.confidence ?? "unknown"}.`}
                                </Body1>
                            </div>
                        );
                    })}
                    {screen.kind === "review" ? (
                        <Button
                            appearance="primary"
                            className={styles.touchTarget}
                            onClick={() =>
                                void onConfirm(screen.document, screen.current)
                            }
                        >
                            Save corrections
                        </Button>
                    ) : null}
                    {screen.kind === "confirmed" ? (
                        <Button
                            appearance="primary"
                            className={styles.touchTarget}
                            onClick={() =>
                                void onCompare(screen.document, screen.current)
                            }
                        >
                            Compare with purchase order
                        </Button>
                    ) : null}
                    {screen.kind === "confirming" ? (
                        <Spinner label="Saving corrections..." />
                    ) : null}
                    {screen.kind === "comparing" ? (
                        <Spinner label="Comparing source documents..." />
                    ) : null}
                </>
            );
        }

        if (screen.kind === "compared") {
            const comparison = screen.comparison;
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Comparison indicators
                    </Title1>
                    <Body1 as="p">
                        Reference document {comparison.reference?.documentId} (
                        {comparison.reference?.documentType})
                    </Body1>
                    <Body1 as="p">
                        Compared document {comparison.compared?.documentId} (
                        {comparison.compared?.documentType})
                    </Body1>
                    {(comparison.mismatches ?? []).length === 0 ? (
                        <Body1 as="p">
                            No value differences were recorded.
                        </Body1>
                    ) : (
                        (comparison.mismatches ?? []).map((mismatch) => (
                            <MessageBar
                                key={mismatch.indicatorId}
                                intent="warning"
                            >
                                <MessageBarBody>
                                    {mismatch.rule}: {mismatch.explanation}{" "}
                                    Reference {mismatch.reference?.documentId}{" "}
                                    value {mismatch.reference?.confirmedValue}.
                                    Compared {mismatch.compared?.documentId}{" "}
                                    value {mismatch.compared?.confirmedValue}.
                                </MessageBarBody>
                            </MessageBar>
                        ))
                    )}
                </>
            );
        }

        return (
            <>
                <Title1 as="h1" className={styles.title}>
                    {screen.title}
                </Title1>
                <MessageBar intent="error" role="alert">
                    <MessageBarBody>{screen.title}</MessageBarBody>
                </MessageBar>
                {screen.retryable ? (
                    <Button
                        className={styles.touchTarget}
                        onClick={() => {
                            setScreen({ kind: "upload" });
                            navigate(`/app/documents/${businessId}`, {
                                replace: true,
                            });
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </>
        );
    }
}

function reviewScreen(
    document: DocumentResponse,
    preferred?: Record<string, string>,
    kind: "review" | "confirmed" = document.state === "CONFIRMED"
        ? "confirmed"
        : "review",
): Screen {
    const overlay = confirmationOverlay(document);
    const current = { ...preferred };
    if (Object.keys(current).length === 0) {
        for (const field of document.extraction?.fields ?? []) {
            if (field.path) {
                current[field.path] = field.value ?? "";
            }
        }
        for (const field of overlay?.fields ?? []) {
            if (field.path) {
                current[field.path] = field.value ?? "";
            }
        }
    }
    return { kind, document, current };
}
