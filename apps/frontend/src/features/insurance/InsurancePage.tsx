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
import { loadInsuranceEvidence } from "./insurance-api";
import {
    isRetryableRestrictedProblem,
    restrictedProblemTitle,
} from "../internal-risk/restricted-errors";
import type {
    ApiProblem,
    EvidencePackageResponse,
} from "../../shared/api/generated";

type Screen =
    | { kind: "form" }
    | { kind: "loading" }
    | { kind: "ready"; data: EvidencePackageResponse }
    | { kind: "error"; title: string; retryable: boolean };

export default function InsurancePage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { caseId } = useParams();
    const [screen, setScreen] = useState<Screen>(
        caseId ? { kind: "loading" } : { kind: "form" },
    );
    const [reloadToken, setReloadToken] = useState(0);
    const [caseInput, setCaseInput] = useState(
        caseId ?? "00000000-0000-4000-8000-0000000000d1",
    );

    useEffect(() => {
        if (!caseId) {
            setScreen({ kind: "form" });
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "loading" });
        void loadInsuranceEvidence(caseId).then((result) => {
            if (abort.signal.aborted) {
                return;
            }
            if (result.error || !result.data) {
                const error = result.error as ApiProblem | undefined;
                setScreen({
                    kind: "error",
                    retryable: isRetryableRestrictedProblem(error),
                    title: restrictedProblemTitle(
                        error,
                        "Insurance evidence could not be loaded",
                    ),
                });
                return;
            }
            setScreen({ kind: "ready", data: result.data });
        });
        return () => abort.abort();
    }, [caseId, reloadToken]);

    if (screen.kind === "loading") {
        return (
            <Card>
                <Spinner label="Loading insurance evidence..." />
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
                            if (caseId) {
                                setReloadToken((value) => value + 1);
                                return;
                            }
                            navigate("/app/insurance");
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "ready") {
        const pack = screen.data;
        const missing = pack.missingEvidence ?? [];
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Authorized insurance evidence
                </Title1>
                <Body1>
                    Case {pack.insuranceCase?.caseId} · shipment{" "}
                    {pack.insuranceCase?.shipmentId ??
                        pack.shipment?.shipmentId}{" "}
                    · {pack.insuranceCase?.purpose}
                </Body1>
                {(pack.sourceDocuments ?? []).map((document) => (
                    <Body1 key={document.documentId}>
                        Document {document.documentType}{" "}
                        {document.documentState} at {document.documentCreatedAt}
                    </Body1>
                ))}
                {(pack.actualRoute?.points ?? []).map((point) => (
                    <Body1 key={point.readingId}>
                        Route point at {point.recordedAt}
                    </Body1>
                ))}
                {(pack.handovers ?? []).map((handover) => (
                    <Body1 key={handover.challengeId}>
                        Handover {handover.type} {handover.state} at{" "}
                        {handover.expectedLocationLabel} ·{" "}
                        {handover.completedAt}
                    </Body1>
                ))}
                {(pack.riskIndicators ?? []).map((indicator) => (
                    <Body1 key={indicator.indicatorId}>
                        Indicator {indicator.rule} {indicator.state} first{" "}
                        {indicator.firstObservedAt}
                    </Body1>
                ))}
                {missing.length === 0 ? null : (
                    <MessageBar>
                        <MessageBarBody>
                            Missing evidence: {missing.join(", ")}
                        </MessageBarBody>
                    </MessageBar>
                )}
                <Button
                    className={styles.touchTarget}
                    onClick={() => navigate("/app/insurance")}
                >
                    Open another case
                </Button>
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Open an insurance case
            </Title1>
            <form
                noValidate
                onSubmit={(event: FormEvent<HTMLFormElement>) => {
                    event.preventDefault();
                    navigate(`/app/insurance/${caseInput}`);
                }}
            >
                <Field label="Case ID" required>
                    <Input
                        aria-label="Case ID"
                        className={styles.touchTarget}
                        onChange={(_, data) => setCaseInput(data.value)}
                        value={caseInput}
                    />
                </Field>
                <Button
                    appearance="primary"
                    className={styles.touchTarget}
                    type="submit"
                >
                    Load evidence
                </Button>
            </form>
        </Card>
    );
}
