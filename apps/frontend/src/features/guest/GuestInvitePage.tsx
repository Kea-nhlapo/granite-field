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
import { useParams } from "react-router-dom";

import { useAccessStyles } from "../access/access.styles";
import { applyTokenResponse } from "../access/session";
import {
    convertGuestSupplier,
    registerSupplierAccount,
    submitGuestResponse,
    viewGuestInvitation,
} from "./guest-api";
import {
    isRetryableGuestProblem,
    isUnavailableInvitation,
    problemMessage,
} from "./guest-errors";
import {
    extractQuoteFields,
    validateQuoteDocument,
    type QuoteFields,
} from "./quote-document";
import type {
    ApiProblem,
    GuestInvitationResponse,
    InvitationResponse,
} from "../../shared/api/generated";

type Screen =
    | { kind: "loading" }
    | { kind: "unavailable" }
    | { kind: "upload"; invitation: GuestInvitationResponse }
    | {
          kind: "review";
          invitation: GuestInvitationResponse;
          fields: QuoteFields;
      }
    | {
          kind: "submitting";
          invitation: GuestInvitationResponse;
          fields: QuoteFields;
      }
    | {
          kind: "recorded";
          invitation: GuestInvitationResponse;
          recorded: InvitationResponse;
      }
    | {
          kind: "converting";
          invitation: GuestInvitationResponse;
          recorded: InvitationResponse;
      }
    | { kind: "converted" }
    | {
          kind: "error";
          retryable: boolean;
          title: string;
          invitation?: GuestInvitationResponse;
          fields?: QuoteFields;
          recorded?: InvitationResponse;
      };

export default function GuestInvitePage() {
    const styles = useAccessStyles();
    const { token = "" } = useParams();
    const inviteToken = token;
    const responseReference = useRef<string>("");
    const [screen, setScreen] = useState<Screen>({ kind: "loading" });
    const [fileError, setFileError] = useState<string | undefined>();
    const [convertError, setConvertError] = useState<string | undefined>();

    useEffect(() => {
        let cancelled = false;
        setScreen({ kind: "loading" });
        void viewGuestInvitation(inviteToken).then((result) => {
            if (cancelled) {
                return;
            }
            if (result.error || !result.data?.requestId) {
                const error = result.error as ApiProblem | undefined;
                if (
                    isUnavailableInvitation(error) ||
                    !isRetryableGuestProblem(error)
                ) {
                    setScreen({ kind: "unavailable" });
                    return;
                }
                setScreen({
                    kind: "error",
                    retryable: true,
                    title: problemMessage(
                        error,
                        "The invitation could not be loaded",
                    ),
                });
                return;
            }
            setScreen({ kind: "upload", invitation: result.data });
        });
        return () => {
            cancelled = true;
        };
    }, [inviteToken]);

    async function onUpload(invitation: GuestInvitationResponse, file: File) {
        const invalid = validateQuoteDocument(file);
        if (invalid) {
            setFileError(invalid);
            return;
        }
        setFileError(undefined);
        setScreen({
            kind: "review",
            invitation,
            fields: extractQuoteFields(),
        });
    }

    async function onSubmitQuote(
        invitation: GuestInvitationResponse,
        fields: QuoteFields,
    ) {
        if (!invitation.requestId) {
            return;
        }
        if (!responseReference.current) {
            responseReference.current = crypto.randomUUID();
        }
        setScreen({ kind: "submitting", invitation, fields });
        const result = await submitGuestResponse(inviteToken, {
            requestId: invitation.requestId,
            responseReference: responseReference.current,
        });
        if (result.error) {
            const error = result.error as ApiProblem;
            if (isUnavailableInvitation(error)) {
                setScreen({ kind: "unavailable" });
                return;
            }
            setScreen({
                kind: "error",
                retryable: isRetryableGuestProblem(error),
                invitation,
                fields,
                title: problemMessage(error, "The quote could not be recorded"),
            });
            return;
        }
        if (
            result.data?.status === "RESPONDED" ||
            result.data?.responseReference
        ) {
            setScreen({
                kind: "recorded",
                invitation,
                recorded: result.data,
            });
            return;
        }
        setScreen({
            kind: "error",
            retryable: true,
            invitation,
            fields,
            title: "The quote could not be recorded",
        });
    }

    async function onConvert(
        event: FormEvent<HTMLFormElement>,
        invitation: GuestInvitationResponse,
        recorded: InvitationResponse,
    ) {
        event.preventDefault();
        if (!invitation.supplierProfileId) {
            return;
        }
        const form = event.currentTarget;
        const emailInput = form.elements.namedItem("email");
        const passwordInput = form.elements.namedItem("password");
        const email =
            emailInput instanceof HTMLInputElement ? emailInput.value : "";
        const password =
            passwordInput instanceof HTMLInputElement
                ? passwordInput.value
                : "";
        setConvertError(undefined);
        setScreen({ kind: "converting", invitation, recorded });
        const registered = await registerSupplierAccount(email, password);
        if (registered.error || !registered.data) {
            setScreen({ kind: "recorded", invitation, recorded });
            setConvertError(
                problemMessage(
                    registered.error as ApiProblem,
                    "The supplier account could not be created",
                ),
            );
            return;
        }
        applyTokenResponse(registered.data);
        const converted = await convertGuestSupplier(
            invitation.supplierProfileId,
            inviteToken,
        );
        if (converted.error) {
            setScreen({ kind: "recorded", invitation, recorded });
            setConvertError(
                problemMessage(
                    converted.error as ApiProblem,
                    "The supplier profile could not be converted",
                ),
            );
            return;
        }
        setScreen({ kind: "converted" });
    }

    return (
        <main className={styles.page}>
            <Card className={styles.card}>
                <div className={styles.stack}>{renderScreen()}</div>
            </Card>
        </main>
    );

    function renderScreen() {
        if (screen.kind === "loading") {
            return <Spinner label="Opening the invitation..." />;
        }

        if (screen.kind === "unavailable") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        This supplier invitation is unavailable
                    </Title1>
                    <Body1 as="p">
                        The link is expired, revoked, already used, or was not
                        recognised.
                    </Body1>
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
                                "quoteDocument",
                            );
                        const file =
                            input instanceof HTMLInputElement
                                ? input.files?.[0]
                                : undefined;
                        if (!file) {
                            setFileError("Choose a quote document to upload.");
                            return;
                        }
                        void onUpload(screen.invitation, file);
                    }}
                >
                    <Title1 as="h1" className={styles.title}>
                        Reply to this quote request
                    </Title1>
                    <Body1 as="p">
                        Upload a quote document, then confirm the extracted
                        values before they are recorded.
                    </Body1>
                    {fileError ? (
                        <MessageBar intent="error" role="alert">
                            <MessageBarBody>{fileError}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <label className={styles.stack} htmlFor="quote-document">
                        Quote document
                    </label>
                    <input
                        accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png"
                        className={styles.touchTarget}
                        id="quote-document"
                        name="quoteDocument"
                        type="file"
                    />
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        type="submit"
                    >
                        Extract quote fields
                    </Button>
                </form>
            );
        }

        if (screen.kind === "review" || screen.kind === "submitting") {
            const fields = screen.fields;
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Confirm extracted quote fields
                    </Title1>
                    <MessageBar intent="warning">
                        <MessageBarBody>
                            Extracted values stay unconfirmed until you send
                            this response.
                        </MessageBarBody>
                    </MessageBar>
                    {(
                        [
                            ["lineTotal", "Line total"],
                            ["currency", "Currency"],
                            ["validDays", "Valid days"],
                        ] as const
                    ).map(([path, label]) => (
                        <Field key={path} label={`${label} (unconfirmed)`}>
                            <Input
                                className={styles.touchTarget}
                                disabled={screen.kind === "submitting"}
                                onChange={(_, data) => {
                                    if (screen.kind !== "review") {
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
                                value={fields[path]}
                            />
                        </Field>
                    ))}
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={screen.kind === "submitting"}
                        onClick={() => {
                            if (screen.kind === "review") {
                                void onSubmitQuote(screen.invitation, fields);
                            }
                        }}
                    >
                        Send quote response
                    </Button>
                    {screen.kind === "submitting" ? (
                        <Spinner label="Recording the quote response..." />
                    ) : null}
                </>
            );
        }

        if (screen.kind === "recorded" || screen.kind === "converting") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Quote response recorded
                    </Title1>
                    <Body1 as="p">
                        You can convert this temporary supplier profile after
                        the quote is saved. This page does not open the
                        signed-in workspace.
                    </Body1>
                    {convertError ? (
                        <MessageBar intent="error" role="alert">
                            <MessageBarBody>{convertError}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <form
                        className={styles.stack}
                        onSubmit={(event) =>
                            void onConvert(
                                event,
                                screen.invitation,
                                screen.recorded,
                            )
                        }
                    >
                        <label className={styles.stack} htmlFor="guest-email">
                            Supplier email
                        </label>
                        <input
                            autoComplete="username"
                            className={styles.touchTarget}
                            disabled={screen.kind === "converting"}
                            id="guest-email"
                            name="email"
                            required
                            type="email"
                        />
                        <label
                            className={styles.stack}
                            htmlFor="guest-password"
                        >
                            Password
                        </label>
                        <input
                            autoComplete="new-password"
                            className={styles.touchTarget}
                            disabled={screen.kind === "converting"}
                            id="guest-password"
                            name="password"
                            required
                            type="password"
                        />
                        <Button
                            appearance="primary"
                            className={styles.touchTarget}
                            disabled={screen.kind === "converting"}
                            type="submit"
                        >
                            Create supplier account
                        </Button>
                    </form>
                    {screen.kind === "converting" ? (
                        <Spinner label="Converting the supplier profile..." />
                    ) : null}
                </>
            );
        }

        if (screen.kind === "converted") {
            return (
                <>
                    <Title1 as="h1" className={styles.title}>
                        Supplier profile converted
                    </Title1>
                    <Body1 as="p">
                        The quote stays on this invitation. Sign in later from
                        the public login page if you need the workspace.
                    </Body1>
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
                            if (screen.recorded && screen.invitation) {
                                setScreen({
                                    kind: "recorded",
                                    invitation: screen.invitation,
                                    recorded: screen.recorded,
                                });
                                return;
                            }
                            if (screen.invitation && screen.fields) {
                                setScreen({
                                    kind: "review",
                                    invitation: screen.invitation,
                                    fields: screen.fields,
                                });
                                return;
                            }
                            setScreen({ kind: "loading" });
                            void viewGuestInvitation(inviteToken).then(
                                (result) => {
                                    if (
                                        result.error ||
                                        !result.data?.requestId
                                    ) {
                                        const error = result.error as
                                            ApiProblem | undefined;
                                        if (isUnavailableInvitation(error)) {
                                            setScreen({ kind: "unavailable" });
                                            return;
                                        }
                                        setScreen({
                                            kind: "error",
                                            retryable:
                                                isRetryableGuestProblem(error),
                                            title: problemMessage(
                                                error,
                                                "The invitation could not be loaded",
                                            ),
                                        });
                                        return;
                                    }
                                    setScreen({
                                        kind: "upload",
                                        invitation: result.data,
                                    });
                                },
                            );
                        }}
                    >
                        Try again
                    </Button>
                ) : null}
            </>
        );
    }
}
