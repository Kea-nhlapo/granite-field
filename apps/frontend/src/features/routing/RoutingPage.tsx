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
import { useNavigate, useParams, useSearchParams } from "react-router-dom";

import { useAccessStyles } from "../access/access.styles";
import { polylinePoints } from "./route-map";
import {
    calculateRoutes,
    loadAssessment,
    loadCalculation,
    scoreRoutes,
} from "./routing-api";
import {
    isForbiddenRouting,
    isRetryableRoutingProblem,
    isStaleRouting,
    optionLabel,
    problemMessage,
} from "./routing-errors";
import type {
    ApiProblem,
    CandidateResponse,
    RouteAssessmentResponse,
    RouteCalculationResponse,
} from "../../shared/api/generated";

const cargoProfiles = [
    "HIGH_VALUE_ELECTRONICS",
    "LOW_VALUE_DRY_GOODS",
    "BALANCED",
] as const;

type Screen =
    | { kind: "form" }
    | { kind: "loading" }
    | {
          kind: "ready";
          calculation: RouteCalculationResponse;
          assessment: RouteAssessmentResponse;
      }
    | { kind: "error"; title: string; retryable: boolean };

export default function RoutingPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { businessId = "", calculationId, assessmentId } = useParams();
    const [searchParams, setSearchParams] = useSearchParams();
    const [screen, setScreen] = useState<Screen>(
        calculationId ? { kind: "loading" } : { kind: "form" },
    );
    const [cargoProfile, setCargoProfile] = useState<
        (typeof cargoProfiles)[number]
    >("HIGH_VALUE_ELECTRONICS");
    const [timeWeight, setTimeWeight] = useState("0.2");
    const [safetyWeight, setSafetyWeight] = useState("0.35");

    useEffect(() => {
        if (!businessId || !calculationId || !assessmentId) {
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "loading" });
        void Promise.all([
            loadCalculation(businessId, calculationId),
            loadAssessment(businessId, assessmentId),
        ]).then(([calculation, assessment]) => {
            if (abort.signal.aborted) {
                return;
            }
            if (
                calculation.error ||
                !calculation.data ||
                assessment.error ||
                !assessment.data
            ) {
                setScreen(
                    errorScreen(
                        (calculation.error ?? assessment.error) as
                            ApiProblem | undefined,
                        "The route comparison could not be loaded",
                    ),
                );
                return;
            }
            setScreen({
                kind: "ready",
                calculation: calculation.data,
                assessment: assessment.data,
            });
        });
        return () => abort.abort();
    }, [assessmentId, businessId, calculationId]);

    async function onCompare(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setScreen({ kind: "loading" });
        const calculated = await calculateRoutes(businessId, {
            requestId: crypto.randomUUID(),
            origin: {
                label: "Tembisa pickup",
                latitude: -25.997,
                longitude: 28.226,
            },
            destination: {
                label: "City delivery",
                latitude: -26.05,
                longitude: 28.23,
            },
            waypoints: [],
            vehicleLimits: {
                maximumWeightKg: 8000,
                maximumHeightMetres: 4,
                maximumWidthMetres: 2.5,
                maximumLengthMetres: 12,
            },
            avoidances: [],
            recalculationOfId: calculationId,
        });
        if (calculated.error || !calculated.data?.calculationId) {
            setScreen(
                errorScreen(
                    calculated.error as ApiProblem | undefined,
                    "Routes could not be calculated",
                ),
            );
            return;
        }
        if ((calculated.data.candidates ?? []).length === 0) {
            setScreen({
                kind: "error",
                retryable: true,
                title: "No routes were returned",
            });
            return;
        }
        const scored = await scoreRoutes(
            businessId,
            calculated.data.calculationId,
            {
                requestId: crypto.randomUUID(),
                cargoProfile,
                weightOverrides: {
                    TIME: Number(timeWeight),
                    SAFETY_EXPOSURE: Number(safetyWeight),
                    DISTANCE: 0.15,
                    FUEL: 0.1,
                    TOLLS: 0.1,
                    ROAD_QUALITY: 0.05,
                    CONNECTIVITY: 0.05,
                },
            },
        );
        if (scored.error || !scored.data?.assessmentId) {
            setScreen(
                errorScreen(
                    scored.error as ApiProblem | undefined,
                    "Routes could not be scored",
                ),
            );
            return;
        }
        navigate(
            `/app/routing/${businessId}/calculations/${calculated.data.calculationId}/assessments/${scored.data.assessmentId}`,
        );
    }

    if (screen.kind === "loading") {
        return (
            <Card>
                <Spinner label="Comparing routes..." />
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
                            setScreen({ kind: "form" });
                            navigate(`/app/routing/${businessId}`);
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </Card>
        );
    }

    if (screen.kind === "ready") {
        const selectedId =
            searchParams.get("candidate") ??
            screen.assessment.recommendedCandidateId;
        const selected =
            screen.calculation.candidates?.find(
                (candidate) => candidate.candidateId === selectedId,
            ) ?? screen.calculation.candidates?.[0];
        return (
            <Card>
                <Title1 as="h1" className={styles.title}>
                    Route comparison
                </Title1>
                <Body1>
                    Cargo {screen.assessment.cargoProfile} · scale{" "}
                    {screen.assessment.scoreScale}
                </Body1>
                <RouteMap
                    candidates={screen.calculation.candidates ?? []}
                    selectedId={selected?.candidateId}
                />
                <div role="radiogroup" aria-label="Route options">
                    {(screen.calculation.candidates ?? []).map((candidate) => {
                        const score = screen.assessment.candidates?.find(
                            (item) =>
                                item.candidateId === candidate.candidateId,
                        );
                        const checked =
                            candidate.candidateId === selected?.candidateId;
                        return (
                            <label key={candidate.candidateId}>
                                <input
                                    aria-label={candidate.label}
                                    checked={checked}
                                    name="selected-route"
                                    onChange={() =>
                                        setSearchParams({
                                            candidate:
                                                candidate.candidateId ?? "",
                                        })
                                    }
                                    type="radio"
                                    value={candidate.candidateId}
                                />
                                {candidate.label}
                                {(score?.options ?? []).map((option) => (
                                    <span key={option}>
                                        {" "}
                                        · {optionLabel(option)}
                                    </span>
                                ))}
                            </label>
                        );
                    })}
                </div>
                {selected ? (
                    <RouteSummary
                        assessment={screen.assessment}
                        candidate={selected}
                    />
                ) : null}
                <Button
                    className={styles.touchTarget}
                    onClick={() => {
                        setScreen({ kind: "form" });
                        navigate(`/app/routing/${businessId}`);
                    }}
                >
                    Change cargo or weights
                </Button>
            </Card>
        );
    }

    return (
        <Card>
            <Title1 as="h1" className={styles.title}>
                Compare routes
            </Title1>
            <form noValidate onSubmit={(event) => void onCompare(event)}>
                <Field label="Cargo profile" required>
                    <select
                        aria-label="Cargo profile"
                        className={styles.touchTarget}
                        onChange={(event) =>
                            setCargoProfile(
                                event.target
                                    .value as (typeof cargoProfiles)[number],
                            )
                        }
                        value={cargoProfile}
                    >
                        {cargoProfiles.map((profile) => (
                            <option key={profile} value={profile}>
                                {profile}
                            </option>
                        ))}
                    </select>
                </Field>
                <Field label="Time weight">
                    <Input
                        className={styles.touchTarget}
                        name="timeWeight"
                        onChange={(_, data) => setTimeWeight(data.value)}
                        value={timeWeight}
                    />
                </Field>
                <Field label="Safety exposure weight">
                    <Input
                        className={styles.touchTarget}
                        name="safetyWeight"
                        onChange={(_, data) => setSafetyWeight(data.value)}
                        value={safetyWeight}
                    />
                </Field>
                <Button
                    appearance="primary"
                    className={styles.touchTarget}
                    type="submit"
                >
                    Calculate and score
                </Button>
            </form>
        </Card>
    );
}

function RouteMap({
    candidates,
    selectedId,
}: {
    candidates: CandidateResponse[];
    selectedId?: string;
}) {
    return (
        <svg
            aria-label="Candidate route map"
            height="220"
            role="img"
            viewBox="0 0 400 220"
            width="100%"
        >
            {candidates.map((candidate) => (
                <polyline
                    fill="none"
                    key={candidate.candidateId}
                    points={polylinePoints(candidate.geometry)}
                    stroke={
                        candidate.candidateId === selectedId
                            ? "currentColor"
                            : "#888"
                    }
                    strokeWidth={candidate.candidateId === selectedId ? 6 : 3}
                />
            ))}
        </svg>
    );
}

function RouteSummary({
    candidate,
    assessment,
}: {
    candidate: CandidateResponse;
    assessment: RouteAssessmentResponse;
}) {
    const score = assessment.candidates?.find(
        (item) => item.candidateId === candidate.candidateId,
    );
    const safety = score?.factors?.find(
        (factor) => factor.factor === "SAFETY_EXPOSURE",
    );
    const connectivity = score?.factors?.find(
        (factor) => factor.factor === "CONNECTIVITY",
    );
    const fuel = score?.factors?.find((factor) => factor.factor === "FUEL");
    const tolls = score?.factors?.find((factor) => factor.factor === "TOLLS");
    return (
        <section aria-label="Selected route summary">
            <Title1 as="h2">Selected route summary</Title1>
            <Body1>
                Time {candidate.durationSeconds}s · distance{" "}
                {candidate.distanceMetres} m · estimated cost ZAR{" "}
                {String(candidate.tollEstimateZar)} · fuel{" "}
                {String(fuel?.rawValue)} {fuel?.rawUnit} · tolls{" "}
                {String(tolls?.rawValue)} {tolls?.rawUnit}
            </Body1>
            <Body1>
                Safety exposure {String(safety?.rawValue)} {safety?.rawUnit} ·
                connectivity {String(connectivity?.rawValue)}{" "}
                {connectivity?.rawUnit} · confidence {String(score?.confidence)}
            </Body1>
            {(score?.reasons ?? []).map((reason) => (
                <Body1 key={reason}>{reason}</Body1>
            ))}
            {(score?.factors ?? [])
                .filter((factor) => factor.dataAvailable === false)
                .map((factor) => (
                    <MessageBar intent="warning" key={factor.factor}>
                        <MessageBarBody>
                            Missing {factor.factor} data — not treated as safe.
                        </MessageBarBody>
                    </MessageBar>
                ))}
        </section>
    );
}

function errorScreen(
    error: ApiProblem | undefined,
    fallback: string,
): Extract<Screen, { kind: "error" }> {
    if (isForbiddenRouting(error)) {
        return {
            kind: "error",
            retryable: false,
            title: problemMessage(error, "Access denied"),
        };
    }
    if (isStaleRouting(error)) {
        return {
            kind: "error",
            retryable: true,
            title: problemMessage(
                error,
                "This calculation is no longer current",
            ),
        };
    }
    return {
        kind: "error",
        retryable: isRetryableRoutingProblem(error) || !error,
        title: problemMessage(error, fallback),
    };
}
