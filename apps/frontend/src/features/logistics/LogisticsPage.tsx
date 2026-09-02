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
import {
    capacityCandidates,
    isHardFailure,
    type CapacityOfferCandidate,
} from "./capacity-candidates";
import {
    loadCapacitySearch,
    loadSuggestion,
    releaseCapacity,
    reserveCapacity,
    searchCapacity,
    suggestDemandGroup,
} from "./logistics-api";
import { exclusionReasonText } from "./logistics-copy";
import {
    isForbiddenLogistics,
    isRetryableLogisticsProblem,
    isStaleLogistics,
    problemMessage,
} from "./logistics-errors";
import type {
    ApiProblem,
    SearchResponse,
    SuggestionResponse,
} from "../../shared/api/generated";
import { mockOrderId } from "../../shared/api/mocks/procurement-handlers";

type Screen =
    | { kind: "start" }
    | { kind: "loading" }
    | { kind: "suggestion"; suggestion: SuggestionResponse }
    | { kind: "searching"; suggestion: SuggestionResponse }
    | {
          kind: "capacity";
          suggestion: SuggestionResponse;
          search: SearchResponse;
      }
    | { kind: "error"; title: string; retryable: boolean; action?: string };

export default function LogisticsPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { businessId = "", suggestionId, searchId } = useParams();
    const [screen, setScreen] = useState<Screen>(
        suggestionId || searchId ? { kind: "loading" } : { kind: "start" },
    );
    const [anchorOrderId, setAnchorOrderId] = useState(mockOrderId);
    const [weightKg, setWeightKg] = useState("80");
    const [volumeCubicMetres, setVolumeCubicMetres] = useState("6");
    const [pendingOfferId, setPendingOfferId] = useState<string | undefined>();

    useEffect(() => {
        if (!businessId) {
            return;
        }
        const abort = new AbortController();
        if (searchId) {
            setScreen({ kind: "loading" });
            void loadCapacitySearch(businessId, searchId).then(
                async (searchResult) => {
                    if (abort.signal.aborted) {
                        return;
                    }
                    if (searchResult.error || !searchResult.data) {
                        setScreen(
                            errorScreen(
                                searchResult.error as ApiProblem | undefined,
                                "The capacity search could not be loaded",
                            ),
                        );
                        return;
                    }
                    const suggestionResult = searchResult.data
                        .demandGroupSuggestionId
                        ? await loadSuggestion(
                              businessId,
                              searchResult.data.demandGroupSuggestionId,
                          )
                        : undefined;
                    if (abort.signal.aborted) {
                        return;
                    }
                    setScreen({
                        kind: "capacity",
                        search: searchResult.data,
                        suggestion:
                            suggestionResult?.data ??
                            ({
                                suggestionId:
                                    searchResult.data.demandGroupSuggestionId,
                            } satisfies SuggestionResponse),
                    });
                },
            );
            return () => abort.abort();
        }
        if (suggestionId) {
            setScreen({ kind: "loading" });
            void loadSuggestion(businessId, suggestionId).then((result) => {
                if (abort.signal.aborted) {
                    return;
                }
                if (result.error || !result.data) {
                    setScreen(
                        errorScreen(
                            result.error as ApiProblem | undefined,
                            "The consolidation suggestion could not be loaded",
                        ),
                    );
                    return;
                }
                setScreen({ kind: "suggestion", suggestion: result.data });
            });
            return () => abort.abort();
        }
        setScreen({ kind: "start" });
        return () => abort.abort();
    }, [businessId, searchId, suggestionId]);

    async function onSuggest(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setScreen({ kind: "loading" });
        const result = await suggestDemandGroup(businessId, anchorOrderId);
        if (result.error || !result.data?.suggestionId) {
            setScreen(
                errorScreen(
                    result.error as ApiProblem | undefined,
                    "The consolidation suggestion could not be created",
                ),
            );
            return;
        }
        navigate(
            `/app/logistics/${businessId}/suggestions/${result.data.suggestionId}`,
        );
    }

    async function onSearch(suggestion: SuggestionResponse) {
        setScreen({ kind: "searching", suggestion });
        const result = await searchCapacity(businessId, {
            demandGroupSuggestionId: suggestion.suggestionId ?? "",
            requiredCapacity: {
                weightKg: Number(weightKg),
                volumeCubicMetres: Number(volumeCubicMetres),
            },
            cargoTraits: ["DRY_GOODS", "FOOD_GRADE"],
        });
        if (result.error || !result.data?.searchId) {
            setScreen(
                errorScreen(
                    result.error as ApiProblem | undefined,
                    "Capacity could not be searched",
                    "change-cargo",
                ),
            );
            return;
        }
        navigate(
            `/app/logistics/${businessId}/capacity-matches/${result.data.searchId}`,
        );
    }

    async function onReserve(search: SearchResponse, offerId: string) {
        setPendingOfferId(offerId);
        const result = await reserveCapacity(
            businessId,
            search.searchId ?? "",
            offerId,
        );
        setPendingOfferId(undefined);
        if (result.error) {
            setScreen(
                errorScreen(
                    result.error as ApiProblem | undefined,
                    "The offer could not be reserved",
                ),
            );
            return;
        }
        const refreshed = await loadCapacitySearch(
            businessId,
            search.searchId ?? "",
        );
        if (refreshed.data && screen.kind === "capacity") {
            setScreen({ ...screen, search: refreshed.data });
        }
    }

    async function onRelease(search: SearchResponse) {
        const result = await releaseCapacity(businessId, search.searchId ?? "");
        if (result.error) {
            setScreen(
                errorScreen(
                    result.error as ApiProblem | undefined,
                    "The reservation could not be released",
                ),
            );
            return;
        }
        const refreshed = await loadCapacitySearch(
            businessId,
            search.searchId ?? "",
        );
        if (refreshed.data && screen.kind === "capacity") {
            setScreen({ ...screen, search: refreshed.data });
        }
    }

    if (screen.kind === "loading" || screen.kind === "searching") {
        return (
            <Card>
                <Spinner
                    label={
                        screen.kind === "searching"
                            ? "Searching capacity..."
                            : "Loading logistics..."
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
                {screen.retryable ? (
                    <Button
                        className={styles.touchTarget}
                        onClick={() => {
                            setScreen({ kind: "start" });
                            navigate(`/app/logistics/${businessId}`);
                        }}
                    >
                        {screen.action === "change-cargo"
                            ? "Change cargo or retry"
                            : "Try again"}
                    </Button>
                ) : null}
                {screen.action === "change-cargo" ? (
                    <Body1>
                        Widen the delivery window or change the cargo profile,
                        then search again.
                    </Body1>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "suggestion" || screen.kind === "capacity") {
        const suggestion = screen.suggestion;
        const included = (suggestion.orders ?? []).filter(
            (order) => order.included,
        );
        const excluded = (suggestion.orders ?? []).filter(
            (order) => !order.included,
        );
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Consolidation suggestion
                </Title1>
                <Body1>Suggestion {suggestion.suggestionId}</Body1>
                <Body1>Included orders {suggestion.includedOrderCount}</Body1>
                {included.map((order) => (
                    <Body1 key={order.orderId}>
                        Order {order.orderId} · window overlap{" "}
                        {order.windowOverlapSeconds}s · cargo overlap{" "}
                        {String(order.cargoOverlapRatio)}
                    </Body1>
                ))}
                {excluded.map((order) => (
                    <Body1 key={order.orderId}>
                        Order {order.orderId} excluded:{" "}
                        {exclusionReasonText(order.exclusionReasons?.[0])}
                    </Body1>
                ))}
                {suggestion.status === "NO_MATCH" ||
                (suggestion.includedOrderCount ?? 0) <= 1 ? (
                    <>
                        <MessageBar intent="warning">
                            <MessageBarBody>
                                Empty consolidation — no additional orders could
                                join this group.
                            </MessageBarBody>
                        </MessageBar>
                        <Button
                            className={styles.touchTarget}
                            onClick={() =>
                                navigate(`/app/logistics/${businessId}`)
                            }
                        >
                            Widen window
                        </Button>
                    </>
                ) : screen.kind === "suggestion" ? (
                    <form
                        noValidate
                        onSubmit={(event) => {
                            event.preventDefault();
                            void onSearch(suggestion);
                        }}
                    >
                        <Field label="Combined weight (kg)" required>
                            <Input
                                className={styles.touchTarget}
                                onChange={(_, data) => setWeightKg(data.value)}
                                value={weightKg}
                            />
                        </Field>
                        <Field label="Combined volume (m³)" required>
                            <Input
                                className={styles.touchTarget}
                                onChange={(_, data) =>
                                    setVolumeCubicMetres(data.value)
                                }
                                value={volumeCubicMetres}
                            />
                        </Field>
                        <Button
                            appearance="primary"
                            className={styles.touchTarget}
                            type="submit"
                        >
                            Search capacity
                        </Button>
                    </form>
                ) : (
                    <CapacityResults
                        businessId={businessId}
                        navigate={navigate}
                        onRelease={() => void onRelease(screen.search)}
                        onReserve={(offerId) =>
                            void onReserve(screen.search, offerId)
                        }
                        pendingOfferId={pendingOfferId}
                        search={screen.search}
                        styles={styles}
                    />
                )}
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Consolidate confirmed orders
            </Title1>
            <form noValidate onSubmit={(event) => void onSuggest(event)}>
                <Field label="Anchor order" required>
                    <Input
                        className={styles.touchTarget}
                        onChange={(_, data) => setAnchorOrderId(data.value)}
                        value={anchorOrderId}
                    />
                </Field>
                <Button
                    appearance="primary"
                    className={styles.touchTarget}
                    type="submit"
                >
                    Suggest consolidation
                </Button>
            </form>
        </Card>
    );
}

function CapacityResults({
    search,
    onReserve,
    onRelease,
    pendingOfferId,
    styles,
    businessId,
    navigate,
}: {
    search: SearchResponse;
    onReserve: (offerId: string) => void;
    onRelease: () => void;
    pendingOfferId?: string;
    styles: { touchTarget: string; title: string };
    businessId: string;
    navigate: (path: string) => void;
}) {
    const candidates = capacityCandidates(search);
    return (
        <>
            <Title1 as="h2" className={styles.title}>
                Capacity matches
            </Title1>
            <Body1>
                Combined weight {String(search.requiredCapacity?.weightKg)} kg ·
                volume {String(search.requiredCapacity?.volumeCubicMetres)} m³
            </Body1>
            <Body1>
                Delivery window {search.deliveryWindowStart} to{" "}
                {search.deliveryWindowEnd}
            </Body1>
            {search.status === "NO_MATCH" || candidates.length === 0 ? (
                <>
                    <MessageBar intent="warning">
                        <MessageBarBody>
                            No capacity match for this cargo and window.
                        </MessageBarBody>
                    </MessageBar>
                    <Button
                        className={styles.touchTarget}
                        onClick={() =>
                            navigate(
                                `/app/logistics/${businessId}/suggestions/${search.demandGroupSuggestionId}`,
                            )
                        }
                    >
                        Change cargo
                    </Button>
                </>
            ) : (
                candidates.map((candidate) => (
                    <CandidateCard
                        candidate={candidate}
                        disabled={Boolean(pendingOfferId)}
                        key={candidate.offerId}
                        onReserve={onReserve}
                        pending={pendingOfferId === candidate.offerId}
                        styles={styles}
                    />
                ))
            )}
            {search.status === "RESERVED" ? (
                <Button className={styles.touchTarget} onClick={onRelease}>
                    Release reservation
                </Button>
            ) : null}
        </>
    );
}

function CandidateCard({
    candidate,
    onReserve,
    pending,
    disabled,
    styles,
}: {
    candidate: CapacityOfferCandidate;
    onReserve: (offerId: string) => void;
    pending: boolean;
    disabled: boolean;
    styles: { touchTarget: string };
}) {
    const hard = isHardFailure(candidate);
    return (
        <section>
            <Body1>Offer {candidate.offerId}</Body1>
            <Body1>
                Spare capacity {String(candidate.availableCapacity?.weightKg)}{" "}
                kg / {String(candidate.availableCapacity?.volumeCubicMetres)} m³
            </Body1>
            <Body1>
                Compatible timing {candidate.timingOverlapSeconds}s · added
                distance {candidate.addedDistanceMetres} m · estimated cost ZAR{" "}
                {String(candidate.estimatedCostZar)} · score{" "}
                {String(candidate.score)}
            </Body1>
            {(candidate.checks ?? []).map((check) => (
                <Body1 key={`${candidate.offerId}-${check.constraint}`}>
                    {check.constraint}: {check.explanation}
                </Body1>
            ))}
            {hard ? (
                <MessageBar intent="error">
                    <MessageBarBody>
                        Hard failure — this offer cannot carry the group.
                    </MessageBarBody>
                </MessageBar>
            ) : (
                <MessageBar intent="warning">
                    <MessageBarBody>
                        Trade-off — extra distance or cost is acceptable but not
                        ideal.
                    </MessageBarBody>
                </MessageBar>
            )}
            {!hard ? (
                <Button
                    className={styles.touchTarget}
                    disabled={disabled}
                    onClick={() => onReserve(candidate.offerId ?? "")}
                >
                    {pending ? "Reserving..." : "Reserve this offer"}
                </Button>
            ) : null}
        </section>
    );
}

function errorScreen(
    error: ApiProblem | undefined,
    fallback: string,
    action?: string,
): Extract<Screen, { kind: "error" }> {
    if (isForbiddenLogistics(error)) {
        return {
            kind: "error",
            retryable: false,
            title: problemMessage(error, "Access denied"),
        };
    }
    if (isStaleLogistics(error)) {
        return {
            kind: "error",
            retryable: true,
            title: problemMessage(error, "This result is no longer current"),
            action: "change-cargo",
        };
    }
    return {
        kind: "error",
        retryable: isRetryableLogisticsProblem(error) || !error,
        title: problemMessage(error, fallback),
        action,
    };
}
