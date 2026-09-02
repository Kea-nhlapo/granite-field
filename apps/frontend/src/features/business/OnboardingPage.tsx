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
import { validateCompanyDocument } from "./company-document";
import {
    confirmCompanyDocument,
    confirmRegisteredOnboarding,
    registerCompanyDocument,
    startRegisteredOnboarding,
    uploadCompanyDocument,
    waitForParsedDocument,
    waitForRegistryDetails,
    type BusinessProfileResponse,
    type DocumentResponse,
    type RegisteredOnboardingResponse,
} from "./onboarding-api";
import {
    isRetryableOnboardingProblem,
    problemMessage,
} from "./onboarding-errors";
import type { ApiProblem } from "../../shared/api/generated";

type Screen =
    | { kind: "lookup" }
    | { kind: "submitting" }
    | { kind: "processing"; onboardingId: string }
    | { kind: "review"; draft: RegisteredOnboardingResponse }
    | { kind: "confirming"; draft: RegisteredOnboardingResponse }
    | { kind: "upload"; profile: BusinessProfileResponse }
    | {
          kind: "document-processing";
          profile: BusinessProfileResponse;
          documentId: string;
      }
    | {
          kind: "document-review";
          profile: BusinessProfileResponse;
          document: DocumentResponse;
          fields: Record<string, string>;
      }
    | {
          kind: "document-confirming";
          profile: BusinessProfileResponse;
          document: DocumentResponse;
          fields: Record<string, string>;
      }
    | { kind: "confirmed"; profile: BusinessProfileResponse }
    | {
          kind: "error";
          title: string;
          retryable: boolean;
          retryKind: "lookup" | "review" | "upload" | "document-review";
          draft?: RegisteredOnboardingResponse;
          profile?: BusinessProfileResponse;
          document?: DocumentResponse;
          fields?: Record<string, string>;
      };

export default function OnboardingPage() {
    const styles = useAccessStyles();
    const navigate = useNavigate();
    const { onboardingId } = useParams();
    const [screen, setScreen] = useState<Screen>({ kind: "lookup" });
    const [registrationNumber, setRegistrationNumber] = useState("");
    const [fileError, setFileError] = useState<string | undefined>();

    useEffect(() => {
        if (!onboardingId) {
            return;
        }
        const abort = new AbortController();
        setScreen({ kind: "processing", onboardingId });
        void waitForRegistryDetails(onboardingId, abort.signal).then(
            (result) => {
                if (abort.signal.aborted) {
                    return;
                }
                if (result.error || !result.draft) {
                    setScreen({
                        kind: "error",
                        retryable: isRetryableOnboardingProblem(result.error),
                        retryKind: "lookup",
                        title: problemMessage(
                            result.error,
                            "The onboarding record could not be loaded",
                        ),
                    });
                    return;
                }
                if (
                    result.draft.state === "CONFIRMED" &&
                    result.draft.businessId
                ) {
                    setScreen({
                        kind: "upload",
                        profile: {
                            businessId: result.draft.businessId,
                            legalName: result.draft.legalName,
                            tradingName: result.draft.tradingName,
                            registeredAddress: result.draft.registeredAddress,
                            verificationStatus: "REGISTRY_VERIFIED",
                            lifecycleStatus: "ACTIVE",
                            trusted: result.draft.trusted,
                        },
                    });
                    return;
                }
                setScreen({ kind: "review", draft: result.draft });
            },
        );
        return () => abort.abort();
    }, [onboardingId]);

    async function onLookup(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setScreen({ kind: "submitting" });
        const result = await startRegisteredOnboarding(registrationNumber);
        if (result.error || !result.data?.onboardingId) {
            setScreen({
                kind: "error",
                retryable: isRetryableOnboardingProblem(
                    result.error as ApiProblem,
                ),
                retryKind: "lookup",
                title: problemMessage(
                    result.error as ApiProblem,
                    "The company could not be looked up",
                ),
            });
            return;
        }
        navigate(`/app/onboarding/${result.data.onboardingId}`, {
            replace: true,
        });
    }

    async function onConfirmRegistry(draft: RegisteredOnboardingResponse) {
        if (!draft.onboardingId) {
            return;
        }
        setScreen({ kind: "confirming", draft });
        const result = await confirmRegisteredOnboarding(draft.onboardingId);
        if (result.error || !result.data?.businessId) {
            setScreen({
                kind: "error",
                retryable: isRetryableOnboardingProblem(
                    result.error as ApiProblem,
                ),
                retryKind: "review",
                draft,
                title: problemMessage(
                    result.error as ApiProblem,
                    "The business profile could not be confirmed",
                ),
            });
            return;
        }
        setScreen({ kind: "upload", profile: result.data });
    }

    async function onUpload(profile: BusinessProfileResponse, file: File) {
        const invalid = validateCompanyDocument(file);
        if (invalid) {
            setFileError(invalid);
            return;
        }
        if (!profile.businessId) {
            return;
        }
        setFileError(undefined);
        const uploaded = await uploadCompanyDocument(profile.businessId, file);
        if (uploaded.error || !uploaded.data?.fileId) {
            setScreen({
                kind: "error",
                retryable: true,
                retryKind: "upload",
                profile,
                title: problemMessage(
                    uploaded.error as ApiProblem,
                    "The company document could not be uploaded",
                ),
            });
            return;
        }
        const registered = await registerCompanyDocument(
            profile.businessId,
            uploaded.data.fileId,
        );
        if (registered.error || !registered.data?.documentId) {
            setScreen({
                kind: "error",
                retryable: true,
                retryKind: "upload",
                profile,
                title: problemMessage(
                    registered.error as ApiProblem,
                    "The company document could not be registered",
                ),
            });
            return;
        }
        setScreen({
            kind: "document-processing",
            documentId: registered.data.documentId,
            profile,
        });
        const parsed = await waitForParsedDocument(
            profile.businessId,
            registered.data.documentId,
        );
        if (parsed.error || !parsed.document) {
            setScreen({
                kind: "error",
                retryable: true,
                retryKind: "upload",
                profile,
                title: problemMessage(
                    parsed.error,
                    "The company document is still processing",
                ),
            });
            return;
        }
        const fields: Record<string, string> = {};
        for (const field of parsed.document.extraction?.fields ?? []) {
            if (field.path && field.value) {
                fields[field.path] = field.value;
            }
        }
        setScreen({
            kind: "document-review",
            document: parsed.document,
            fields,
            profile,
        });
    }

    async function onConfirmDocument(
        profile: BusinessProfileResponse,
        document: DocumentResponse,
        fields: Record<string, string>,
    ) {
        if (!profile.businessId || !document.documentId) {
            return;
        }
        setScreen({ kind: "document-confirming", document, fields, profile });
        const result = await confirmCompanyDocument(
            profile.businessId,
            document.documentId,
            Object.entries(fields).map(([path, value]) => ({ path, value })),
        );
        if (result.error) {
            setScreen({
                kind: "error",
                retryable: isRetryableOnboardingProblem(
                    result.error as ApiProblem,
                ),
                retryKind: "document-review",
                document,
                fields,
                profile,
                title: problemMessage(
                    result.error as ApiProblem,
                    "The extracted document fields could not be confirmed",
                ),
            });
            return;
        }
        setScreen({ kind: "confirmed", profile });
    }

    function retry() {
        if (screen.kind !== "error") {
            return;
        }
        if (screen.retryKind === "review" && screen.draft) {
            setScreen({ kind: "review", draft: screen.draft });
            return;
        }
        if (screen.retryKind === "upload" && screen.profile) {
            setScreen({ kind: "upload", profile: screen.profile });
            return;
        }
        if (
            screen.retryKind === "document-review" &&
            screen.profile &&
            screen.document &&
            screen.fields
        ) {
            setScreen({
                kind: "document-review",
                document: screen.document,
                fields: screen.fields,
                profile: screen.profile,
            });
            return;
        }
        setScreen({ kind: "lookup" });
        navigate("/app/onboarding", { replace: true });
    }

    return (
        <Card className={styles.card}>
            <div className={styles.stack}>{renderScreen()}</div>
        </Card>
    );

    function renderScreen() {
        if (screen.kind === "lookup" || screen.kind === "submitting") {
            return (
                <form className={styles.stack} onSubmit={onLookup}>
                    <Title1 as="h1" className={styles.title}>
                        Register your business
                    </Title1>
                    <Body1 as="p">
                        Look up the company in the registry, then confirm the
                        untrusted details before they are saved.
                    </Body1>
                    <Field label="Company registration number" required>
                        <Input
                            autoComplete="off"
                            className={styles.touchTarget}
                            name="registrationNumber"
                            onChange={(_, data) =>
                                setRegistrationNumber(data.value)
                            }
                            type="text"
                            value={registrationNumber}
                        />
                    </Field>
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={screen.kind === "submitting"}
                        type="submit"
                    >
                        Look up company
                    </Button>
                    {screen.kind === "submitting" ? (
                        <Spinner label="Looking up the company registry..." />
                    ) : null}
                </form>
            );
        }

        if (screen.kind === "processing") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Checking the registry
                    </Title1>
                    <Spinner label="Waiting for the company registry..." />
                </>
            );
        }

        if (screen.kind === "review" || screen.kind === "confirming") {
            const draft = screen.draft;
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Review registry details
                    </Title1>
                    <MessageBar intent="warning">
                        <MessageBarBody>
                            These values are unconfirmed until you accept them.
                        </MessageBarBody>
                    </MessageBar>
                    <UnconfirmedField
                        label="Legal name"
                        value={draft.legalName}
                    />
                    <UnconfirmedField
                        label="Trading name"
                        value={draft.tradingName}
                    />
                    <UnconfirmedField
                        label="Registered address"
                        value={draft.registeredAddress}
                    />
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={screen.kind === "confirming"}
                        onClick={() => {
                            void onConfirmRegistry(draft);
                        }}
                    >
                        Confirm business profile
                    </Button>
                    {screen.kind === "confirming" ? (
                        <Spinner label="Confirming the business profile..." />
                    ) : null}
                </>
            );
        }

        if (screen.kind === "upload") {
            return (
                <form
                    className={styles.stack}
                    onSubmit={(event) => {
                        event.preventDefault();
                        const input =
                            event.currentTarget.elements.namedItem(
                                "companyDocument",
                            );
                        const file =
                            input instanceof HTMLInputElement
                                ? input.files?.[0]
                                : undefined;
                        if (!file) {
                            setFileError(
                                "Choose a company document to upload.",
                            );
                            return;
                        }
                        void onUpload(screen.profile, file);
                    }}
                >
                    <Title1 as="h1" className={styles.title}>
                        Upload a company document
                    </Title1>
                    <Body1 as="p">
                        Add a supporting company document. The file is sent as
                        multipart upload, not as a filename.
                    </Body1>
                    {fileError ? (
                        <MessageBar intent="error" role="alert">
                            <MessageBarBody>{fileError}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <label className={styles.stack} htmlFor="company-document">
                        Company document
                    </label>
                    <input
                        accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                        className={styles.touchTarget}
                        id="company-document"
                        name="companyDocument"
                        type="file"
                    />
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        type="submit"
                    >
                        Upload document
                    </Button>
                </form>
            );
        }

        if (screen.kind === "document-processing") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Reading the document
                    </Title1>
                    <Spinner label="Extracting fields from the company document..." />
                </>
            );
        }

        if (
            screen.kind === "document-review" ||
            screen.kind === "document-confirming"
        ) {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Correct extracted fields
                    </Title1>
                    <MessageBar intent="warning">
                        <MessageBarBody>
                            Extracted values stay unconfirmed until you save
                            corrections.
                        </MessageBarBody>
                    </MessageBar>
                    {Object.entries(screen.fields).map(([path, value]) => (
                        <Field key={path} label={`${path} (unconfirmed)`}>
                            <Input
                                className={styles.touchTarget}
                                onChange={(_, data) => {
                                    if (screen.kind !== "document-review") {
                                        return;
                                    }
                                    setScreen({
                                        ...screen,
                                        fields: {
                                            ...screen.fields,
                                            [path]: data.value,
                                        },
                                    });
                                }}
                                value={value}
                            />
                        </Field>
                    ))}
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={screen.kind === "document-confirming"}
                        onClick={() => {
                            if (screen.kind === "document-review") {
                                void onConfirmDocument(
                                    screen.profile,
                                    screen.document,
                                    screen.fields,
                                );
                            }
                        }}
                    >
                        Confirm document fields
                    </Button>
                    {screen.kind === "document-confirming" ? (
                        <Spinner label="Saving your corrections..." />
                    ) : null}
                </>
            );
        }

        if (screen.kind === "confirmed") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Business confirmed
                    </Title1>
                    <Body1 as="p">
                        {screen.profile.legalName} is now a verified TradeMesh
                        business.
                    </Body1>
                    <Button
                        className={styles.touchTarget}
                        onClick={() => navigate("/app")}
                    >
                        Back to workspace
                    </Button>
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
                <Button className={styles.touchTarget} onClick={retry}>
                    Try again
                </Button>
            </>
        );
    }
}

function UnconfirmedField({ label, value }: { label: string; value?: string }) {
    return (
        <Field label={`${label} (unconfirmed)`}>
            <Input readOnly value={value ?? ""} />
        </Field>
    );
}
