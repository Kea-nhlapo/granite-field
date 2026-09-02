import {
    Body1,
    Button,
    Card,
    Field,
    Input,
    MessageBar,
    MessageBarBody,
    Title1,
} from "@fluentui/react-components";
import { useState, type FormEvent } from "react";
import { Link as RouterLink, Navigate, useNavigate } from "react-router-dom";

import { AppLoading } from "../../app/AppLoading";
import type { ApiProblem } from "../../shared/api/generated";
import { startRegisteredOnboarding } from "../business/onboarding-api";
import { useAccessStyles } from "./access.styles";
import { saveAccountDetails } from "./account-profile";
import { registerWithPassword } from "./register";
import { useSession } from "./SessionProvider";

export default function SignupPage({
    kind,
}: {
    kind: "customer" | "supplier";
}) {
    const styles = useAccessStyles();
    const { session, status } = useSession();
    const navigate = useNavigate();
    const [firstName, setFirstName] = useState("");
    const [lastName, setLastName] = useState("");
    const [businessName, setBusinessName] = useState("");
    const [registrationNumber, setRegistrationNumber] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState<string | undefined>();
    const [submitting, setSubmitting] = useState(false);

    if (status === "loading") {
        return <AppLoading />;
    }

    if (status === "authenticated" && session) {
        return (
            <Navigate
                replace
                to={kind === "supplier" ? "/app/supplier" : "/app"}
            />
        );
    }

    async function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitting(true);
        setError(undefined);

        if (
            !firstName.trim() ||
            !lastName.trim() ||
            !businessName.trim() ||
            !email.trim() ||
            !password ||
            (kind === "customer" && !registrationNumber.trim())
        ) {
            setSubmitting(false);
            setError("Fill in every required field.");
            return;
        }

        const result = await registerWithPassword(
            email,
            password,
            kind === "supplier" ? "SUPPLIER" : "BUSINESS_OWNER",
        );

        if (result.error || !result.session) {
            setSubmitting(false);
            setError(
                result.error?.title ??
                    result.error?.detail ??
                    "The account could not be created",
            );
            return;
        }

        let businessId: string | undefined;
        if (kind === "customer" && registrationNumber.trim()) {
            const onboarding = await startRegisteredOnboarding(
                registrationNumber.trim(),
            );
            if (onboarding.error) {
                const problem = onboarding.error as ApiProblem;
                setSubmitting(false);
                setError(
                    problem.title ??
                        "The company registration could not be started",
                );
                return;
            }
            businessId = onboarding.data?.businessId;
        }

        saveAccountDetails({
            firstName: firstName.trim(),
            lastName: lastName.trim(),
            businessName: businessName.trim(),
            registrationNumber: registrationNumber.trim(),
            email: email.trim(),
            phoneNumber: "",
            businessId,
        });

        setSubmitting(false);
        navigate(kind === "supplier" ? "/app/supplier" : "/app", {
            replace: true,
        });
    }

    return (
        <main className={styles.page}>
            <Card className={styles.card}>
                <form className={styles.stack} noValidate onSubmit={onSubmit}>
                    <Title1 as="h1" className={styles.title}>
                        Create an account
                    </Title1>
                    <Body1 as="p">
                        {kind === "supplier"
                            ? "Sign up to reply to quote requests."
                            : "Sign up to source stock and review invoices."}
                    </Body1>
                    {error ? (
                        <MessageBar intent="error" role="alert">
                            <MessageBarBody>{error}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <Field label="First name" required>
                        <Input
                            aria-label="First name"
                            className={styles.touchTarget}
                            name="firstName"
                            onChange={(_, data) => setFirstName(data.value)}
                            value={firstName}
                        />
                    </Field>
                    <Field label="Last name" required>
                        <Input
                            aria-label="Last name"
                            className={styles.touchTarget}
                            name="lastName"
                            onChange={(_, data) => setLastName(data.value)}
                            value={lastName}
                        />
                    </Field>
                    <Field label="Business name" required>
                        <Input
                            aria-label="Business name"
                            className={styles.touchTarget}
                            name="businessName"
                            onChange={(_, data) => setBusinessName(data.value)}
                            value={businessName}
                        />
                    </Field>
                    {kind === "customer" ? (
                        <Field label="Company registration" required>
                            <Input
                                aria-label="Company registration"
                                className={styles.touchTarget}
                                name="registrationNumber"
                                onChange={(_, data) =>
                                    setRegistrationNumber(data.value)
                                }
                                value={registrationNumber}
                            />
                        </Field>
                    ) : null}
                    <Field label="Email" required>
                        <Input
                            aria-label="Email"
                            autoComplete="username"
                            className={styles.touchTarget}
                            name="email"
                            onChange={(_, data) => setEmail(data.value)}
                            type="email"
                            value={email}
                        />
                    </Field>
                    <Field label="Password" required>
                        <Input
                            aria-label="Password"
                            autoComplete="new-password"
                            className={styles.touchTarget}
                            name="password"
                            onChange={(_, data) => setPassword(data.value)}
                            type="password"
                            value={password}
                        />
                    </Field>
                    <Button
                        appearance="primary"
                        className={styles.touchTarget}
                        disabled={submitting}
                        type="submit"
                    >
                        Create account
                    </Button>
                    <Body1>
                        Already have an account?{" "}
                        <RouterLink to="/login">Sign in</RouterLink>
                    </Body1>
                    {kind === "customer" ? (
                        <Body1>
                            Are you a supplier?{" "}
                            <RouterLink to="/signup/supplier">
                                Supplier sign up
                            </RouterLink>
                        </Body1>
                    ) : null}
                </form>
            </Card>
        </main>
    );
}
