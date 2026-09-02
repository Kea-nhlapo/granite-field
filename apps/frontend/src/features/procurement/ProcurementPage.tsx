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
import { useNavigate, useParams } from "react-router-dom";

import { useAccessStyles } from "../access/access.styles";
import {
    confirmQuote,
    createProductRequest,
    loadOrder,
    loadProductRequest,
    loadQuote,
} from "./procurement-api";
import {
    isForbiddenProcurement,
    isProcurementConflict,
    isRetryableProcurementProblem,
    problemMessage,
} from "./procurement-errors";
import { moneySummaryLines, moneyText } from "./procurement-money";
import {
    emptyLine,
    toCreateBody,
    unitOptions,
    validateRequestDraft,
    type DraftLine,
} from "./request-draft";
import type {
    ApiProblem,
    OrderResponse,
    ProductRequestResponse,
    QuoteResponse,
} from "../../shared/api/generated";
import { mockQuoteId } from "../../shared/api/mocks/procurement-handlers";

type Screen =
    | { kind: "create" }
    | { kind: "creating" }
    | { kind: "loading" }
    | {
          kind: "quote";
          request: ProductRequestResponse;
          quote: QuoteResponse;
          snapshotOpen: boolean;
      }
    | {
          kind: "confirming";
          quote: QuoteResponse;
          request: ProductRequestResponse;
      }
    | { kind: "order"; order: OrderResponse }
    | {
          kind: "error";
          title: string;
          retryable: boolean;
          detail?: string;
          quote?: QuoteResponse;
          request?: ProductRequestResponse;
      };

export default function ProcurementPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { businessId = "", quoteId, orderId } = useParams();
    const [screen, setScreen] = useState<Screen>(
        quoteId || orderId ? { kind: "loading" } : { kind: "create" },
    );
    const [destinationLabel, setDestinationLabel] = useState("");
    const [deliveryWindowStart, setDeliveryWindowStart] = useState("");
    const [deliveryWindowEnd, setDeliveryWindowEnd] = useState("");
    const [items, setItems] = useState<DraftLine[]>([emptyLine()]);
    const [formError, setFormError] = useState<string | undefined>();
    const confirmationRequestId = useRef<string | undefined>(undefined);
    const confirmationInFlight = useRef(false);

    useEffect(() => {
        if (!businessId) {
            return;
        }
        const abort = new AbortController();
        if (orderId) {
            setScreen({ kind: "loading" });
            void loadOrder(businessId, orderId).then((result) => {
                if (abort.signal.aborted) {
                    return;
                }
                if (result.error || !result.data) {
                    setScreen(
                        errorScreen(
                            result.error as ApiProblem | undefined,
                            "The order could not be loaded",
                        ),
                    );
                    return;
                }
                setScreen({ kind: "order", order: result.data });
            });
            return () => abort.abort();
        }
        if (quoteId) {
            setScreen({ kind: "loading" });
            void loadQuote(businessId, quoteId).then(async (quoteResult) => {
                if (abort.signal.aborted) {
                    return;
                }
                if (quoteResult.error || !quoteResult.data?.requestId) {
                    setScreen(
                        errorScreen(
                            quoteResult.error as ApiProblem | undefined,
                            "The quote could not be loaded",
                        ),
                    );
                    return;
                }
                const requestResult = await loadProductRequest(
                    businessId,
                    quoteResult.data.requestId,
                );
                if (abort.signal.aborted) {
                    return;
                }
                if (requestResult.error || !requestResult.data) {
                    setScreen(
                        errorScreen(
                            requestResult.error as ApiProblem | undefined,
                            "The product request could not be loaded",
                        ),
                    );
                    return;
                }
                if (!confirmationRequestId.current) {
                    confirmationRequestId.current = crypto.randomUUID();
                }
                setScreen({
                    kind: "quote",
                    quote: quoteResult.data,
                    request: requestResult.data,
                    snapshotOpen: false,
                });
            });
            return () => abort.abort();
        }
        setScreen({ kind: "create" });
        return () => abort.abort();
    }, [businessId, orderId, quoteId]);

    async function onCreate(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const draft = {
            destinationLabel,
            deliveryWindowStart,
            deliveryWindowEnd,
            items,
        };
        const invalid = validateRequestDraft(draft);
        if (invalid) {
            setFormError(invalid);
            return;
        }
        setFormError(undefined);
        setScreen({ kind: "creating" });
        const created = await createProductRequest(
            businessId,
            toCreateBody(draft),
        );
        if (created.error || !created.data?.id) {
            setScreen(
                errorScreen(
                    created.error as ApiProblem | undefined,
                    "The product request could not be created",
                ),
            );
            return;
        }
        navigate(`/app/procurement/${businessId}/quotes/${mockQuoteId}`);
    }

    async function onConfirm() {
        if (screen.kind !== "quote" && screen.kind !== "confirming") {
            return;
        }
        if (confirmationInFlight.current) {
            return;
        }
        const quote = screen.quote;
        const request = screen.request;
        if (!confirmationRequestId.current) {
            confirmationRequestId.current = crypto.randomUUID();
        }
        confirmationInFlight.current = true;
        setScreen({ kind: "confirming", quote, request });
        const confirmed = await confirmQuote(
            businessId,
            quote.id ?? "",
            confirmationRequestId.current,
        );
        if (confirmed.error || !confirmed.data?.id) {
            confirmationInFlight.current = false;
            const next = errorScreen(
                confirmed.error as ApiProblem | undefined,
                "The quote could not be confirmed",
            );
            setScreen({
                ...next,
                quote,
                request,
            });
            return;
        }
        navigate(`/app/procurement/${businessId}/orders/${confirmed.data.id}`);
    }

    function updateItem(index: number, patch: Partial<DraftLine>) {
        setItems(
            items.map((item, itemIndex) =>
                itemIndex === index ? { ...item, ...patch } : item,
            ),
        );
    }

    if (screen.kind === "loading" || screen.kind === "creating") {
        return (
            <Card>
                <Spinner
                    label={
                        screen.kind === "creating"
                            ? "Creating product request..."
                            : "Loading procurement..."
                    }
                />
            </Card>
        );
    }

    if (screen.kind === "error") {
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    {screen.title}
                </Title1>
                {screen.detail ? <Body1>{screen.detail}</Body1> : null}
                {screen.retryable ? (
                    <Button
                        className={styles.touchTarget}
                        onClick={() => {
                            if (screen.quote && screen.request) {
                                setScreen({
                                    kind: "quote",
                                    quote: screen.quote,
                                    request: screen.request,
                                    snapshotOpen: true,
                                });
                                return;
                            }
                            if (orderId) {
                                setScreen({ kind: "loading" });
                                navigate(
                                    `/app/procurement/${businessId}/orders/${orderId}`,
                                );
                                return;
                            }
                            if (quoteId) {
                                setScreen({ kind: "loading" });
                                navigate(
                                    `/app/procurement/${businessId}/quotes/${quoteId}`,
                                );
                                return;
                            }
                            setScreen({ kind: "create" });
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "order") {
        const money = moneySummaryLines(screen.order.money);
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Confirmed order
                </Title1>
                <Body1>Order {screen.order.id}</Body1>
                <Body1>Source quote {screen.order.sourceQuoteId}</Body1>
                <Body1>Status {screen.order.status}</Body1>
                <Body1>Destination {screen.order.destination?.label}</Body1>
                <Body1>
                    Delivery window {screen.order.deliveryWindow?.start} to{" "}
                    {screen.order.deliveryWindow?.end}
                </Body1>
                {(screen.order.items ?? []).map((item) => (
                    <Body1 key={item.id}>
                        {item.description} · requested {item.quantity}{" "}
                        {item.unitOfMeasure} · unit{" "}
                        {moneyText(money.currency, item.unitPrice)} · line{" "}
                        {moneyText(money.currency, item.lineTotal)}
                    </Body1>
                ))}
                <Body1>Subtotal {money.subtotal}</Body1>
                <Body1>Tax {money.tax}</Body1>
                <Body1>Total {money.total}</Body1>
            </Card>
        );
    }

    if (screen.kind === "quote" || screen.kind === "confirming") {
        const pending = screen.kind === "confirming";
        const money = moneySummaryLines(screen.quote.money);
        const requested = new Map(
            (screen.request.items ?? []).map((item) => [item.id, item]),
        );
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Supplier quote
                </Title1>
                <Body1>Quote {screen.quote.id}</Body1>
                <Body1>Supplier {screen.quote.supplierProfileId}</Body1>
                <Body1>Valid until {screen.quote.validUntil}</Body1>
                <Body1>Status {screen.quote.status}</Body1>
                {(screen.quote.items ?? []).map((item) => {
                    const original = requested.get(item.requestItemId);
                    return (
                        <Body1 key={item.id}>
                            {item.description} · requested{" "}
                            {original?.quantity ?? item.quantity}{" "}
                            {original?.unitOfMeasure ?? item.unitOfMeasure} ·
                            quoted {item.quantity} {item.unitOfMeasure} · unit{" "}
                            {moneyText(money.currency, item.unitPrice)} · line{" "}
                            {moneyText(money.currency, item.lineTotal)}
                        </Body1>
                    );
                })}
                <Body1>Subtotal {money.subtotal}</Body1>
                <Body1>Tax {money.tax}</Body1>
                <Body1>Total {money.total}</Body1>
                {screen.kind === "quote" && screen.snapshotOpen ? (
                    <>
                        <Title1 as="h2" className={styles.title}>
                            Confirm this quote
                        </Title1>
                        <Body1>
                            Accepting this snapshot will create an immutable
                            order for quote {screen.quote.id} totalling{" "}
                            {money.total}.
                        </Body1>
                    </>
                ) : null}
                {screen.quote.status === "EXPIRED" ? (
                    <MessageBar intent="error">
                        <MessageBarBody>
                            This quote has expired and cannot be confirmed.
                        </MessageBarBody>
                    </MessageBar>
                ) : (
                    <Button
                        className={styles.touchTarget}
                        disabled={pending}
                        onClick={() => {
                            if (
                                screen.kind === "quote" &&
                                !screen.snapshotOpen
                            ) {
                                setScreen({ ...screen, snapshotOpen: true });
                                return;
                            }
                            void onConfirm();
                        }}
                    >
                        {pending
                            ? "Confirming quote..."
                            : screen.kind === "quote" && screen.snapshotOpen
                              ? "Confirm quote"
                              : "Review confirmation"}
                    </Button>
                )}
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Create a product request
            </Title1>
            {formError ? (
                <MessageBar intent="error">
                    <MessageBarBody>{formError}</MessageBarBody>
                </MessageBar>
            ) : null}
            <form noValidate onSubmit={(event) => void onCreate(event)}>
                <Field
                    label="Destination"
                    required
                    validationMessage={formError}
                    validationState={formError ? "error" : "none"}
                >
                    <Input
                        className={styles.touchTarget}
                        name="destination"
                        onChange={(_, data) => setDestinationLabel(data.value)}
                        value={destinationLabel}
                    />
                </Field>
                <Field label="Delivery window start" required>
                    <Input
                        className={styles.touchTarget}
                        name="deliveryWindowStart"
                        onChange={(_, data) =>
                            setDeliveryWindowStart(data.value)
                        }
                        type="datetime-local"
                        value={deliveryWindowStart}
                    />
                </Field>
                <Field label="Delivery window end" required>
                    <Input
                        className={styles.touchTarget}
                        name="deliveryWindowEnd"
                        onChange={(_, data) => setDeliveryWindowEnd(data.value)}
                        type="datetime-local"
                        value={deliveryWindowEnd}
                    />
                </Field>
                {items.map((item, index) => (
                    <fieldset key={item.key}>
                        <legend>Line {index + 1}</legend>
                        <Field label="Description" required>
                            <Input
                                className={styles.touchTarget}
                                name={`description-${index}`}
                                onChange={(_, data) =>
                                    updateItem(index, {
                                        description: data.value,
                                    })
                                }
                                value={item.description}
                            />
                        </Field>
                        <Field label="Product code">
                            <Input
                                className={styles.touchTarget}
                                name={`productCode-${index}`}
                                onChange={(_, data) =>
                                    updateItem(index, {
                                        productCode: data.value,
                                    })
                                }
                                value={item.productCode}
                            />
                        </Field>
                        <Field label="Quantity" required>
                            <Input
                                className={styles.touchTarget}
                                name={`quantity-${index}`}
                                onChange={(_, data) =>
                                    updateItem(index, {
                                        quantity: data.value,
                                    })
                                }
                                type="number"
                                value={item.quantity}
                            />
                        </Field>
                        <Field label="Unit" required>
                            <select
                                aria-label="Unit"
                                className={styles.touchTarget}
                                name={`unit-${index}`}
                                onChange={(event) =>
                                    updateItem(index, {
                                        unitOfMeasure: event.target
                                            .value as DraftLine["unitOfMeasure"],
                                    })
                                }
                                value={item.unitOfMeasure}
                            >
                                {unitOptions.map((unit) => (
                                    <option key={unit} value={unit}>
                                        {unit}
                                    </option>
                                ))}
                            </select>
                        </Field>
                        <Button
                            className={styles.touchTarget}
                            disabled={items.length === 1}
                            onClick={() =>
                                setItems(items.filter((_, i) => i !== index))
                            }
                            type="button"
                        >
                            Remove line
                        </Button>
                    </fieldset>
                ))}
                <Button
                    className={styles.touchTarget}
                    onClick={() => setItems([...items, emptyLine()])}
                    type="button"
                >
                    Add line
                </Button>
                <Button
                    appearance="primary"
                    className={styles.touchTarget}
                    type="submit"
                >
                    Submit request
                </Button>
            </form>
        </Card>
    );
}

function errorScreen(
    error: ApiProblem | undefined,
    fallback: string,
): Extract<Screen, { kind: "error" }> {
    if (isForbiddenProcurement(error)) {
        return {
            kind: "error",
            retryable: false,
            title: problemMessage(error, "Access denied"),
        };
    }
    if (isProcurementConflict(error)) {
        return {
            kind: "error",
            retryable: false,
            title: problemMessage(
                error,
                "This quote cannot be confirmed in its current state",
            ),
        };
    }
    return {
        kind: "error",
        retryable: isRetryableProcurementProblem(error) || !error,
        title: problemMessage(error, fallback),
        detail: error?.detail,
    };
}
