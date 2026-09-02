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
import { mockShipmentId } from "../../shared/api/mocks/tracking-handlers";
import { loadRiskIndicators } from "./risk-api";
import {
    isRetryableRestrictedProblem,
    restrictedProblemTitle,
} from "./restricted-errors";
import type {
    ApiProblem,
    IndicatorListResponse,
} from "../../shared/api/generated";

type Screen =
    | { kind: "form" }
    | { kind: "loading" }
    | { kind: "ready"; data: IndicatorListResponse }
    | { kind: "error"; title: string; retryable: boolean };

export default function InternalRiskPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { shipmentId } = useParams();
    const [screen, setScreen] = useState<Screen>(
        shipmentId ? { kind: "loading" } : { kind: "form" },
    );
    const [reloadToken, setReloadToken] = useState(0);
    const [shipmentInput, setShipmentInput] = useState(
        shipmentId ?? mockShipmentId,
    );

    useEffect(() => {
        if (!shipmentId) {
            setScreen({ kind: "form" });
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "loading" });
        void loadRiskIndicators(shipmentId).then((result) => {
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
                        "Risk indicators could not be loaded",
                    ),
                });
                return;
            }
            setScreen({ kind: "ready", data: result.data });
        });
        return () => abort.abort();
    }, [reloadToken, shipmentId]);

    if (screen.kind === "loading") {
        return (
            <Card>
                <Spinner label="Loading risk indicators..." />
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
                            if (shipmentId) {
                                setReloadToken((value) => value + 1);
                                return;
                            }
                            navigate("/app/internal-risk");
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "ready") {
        const indicators = screen.data.indicators ?? [];
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Internal risk indicators
                </Title1>
                {indicators.length === 0 ? (
                    <MessageBar>
                        <MessageBarBody>
                            No permitted indicators are available for this
                            shipment.
                        </MessageBarBody>
                    </MessageBar>
                ) : null}
                {indicators.map((indicator) => (
                    <section
                        aria-label={indicator.rule ?? "indicator"}
                        key={indicator.indicatorId}
                    >
                        <Body1>
                            {indicator.rule} · {indicator.state} ·{" "}
                            {indicator.severity}
                        </Body1>
                        <Body1>
                            First {indicator.firstObservedAt} · last{" "}
                            {indicator.lastObservedAt}
                        </Body1>
                        <Body1>{indicator.explanation}</Body1>
                        {(indicator.evidence ?? []).length === 0 ? (
                            <Body1>Missing evidence for this indicator.</Body1>
                        ) : (
                            (indicator.evidence ?? []).map((item) => (
                                <Body1 key={`${item.type}-${item.referenceId}`}>
                                    Evidence {item.type} {item.referenceId} at{" "}
                                    {item.observedAt}
                                </Body1>
                            ))
                        )}
                        {(indicator.reviewHistory ?? []).map((note) => (
                            <Body1 key={note.transitionId}>
                                Investigation {note.toState} at{" "}
                                {note.occurredAt}: {note.note}
                            </Body1>
                        ))}
                    </section>
                ))}
                <Button
                    className={styles.touchTarget}
                    onClick={() => navigate("/app/internal-risk")}
                >
                    Review another shipment
                </Button>
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Review shipment risk
            </Title1>
            <form
                noValidate
                onSubmit={(event: FormEvent<HTMLFormElement>) => {
                    event.preventDefault();
                    navigate(`/app/internal-risk/${shipmentInput}`);
                }}
            >
                <Field label="Shipment ID" required>
                    <Input
                        aria-label="Shipment ID"
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
                    Load indicators
                </Button>
            </form>
        </Card>
    );
}
