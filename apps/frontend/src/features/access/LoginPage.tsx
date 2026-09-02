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
import {
    Link as RouterLink,
    Navigate,
    useNavigate,
    useSearchParams,
} from "react-router-dom";

import { AppLoading } from "../../app/AppLoading";
import { useAccessStyles } from "./access.styles";
import { homePathForRoles } from "./home-path";
import type { AppRole } from "./roles";
import { useSession } from "./SessionProvider";

export default function LoginPage() {
    const styles = useAccessStyles();
    const { login, session, status } = useSession();
    const navigate = useNavigate();
    const [searchParams] = useSearchParams();
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
                to={safeFrom(searchParams.get("from"), session.roles)}
            />
        );
    }

    async function onSubmit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setSubmitting(true);
        setError(undefined);

        const result = await login(email, password);
        setSubmitting(false);

        if (result.error || !result.session) {
            setError(result.error?.title ?? "Authentication is required");
            return;
        }

        navigate(safeFrom(searchParams.get("from"), result.session.roles), {
            replace: true,
        });
    }

    return (
        <main className={styles.page}>
            <Card className={styles.card}>
                <form className={styles.stack} onSubmit={onSubmit}>
                    <Title1 as="h1" className={styles.title}>
                        Sign in
                    </Title1>
                    <Body1 as="p">Sign in with your email and password.</Body1>
                    {error ? (
                        <MessageBar intent="error" role="alert">
                            <MessageBarBody>{error}</MessageBarBody>
                        </MessageBar>
                    ) : null}
                    <Field label="Email" required>
                        <Input
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
                            autoComplete="current-password"
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
                        Sign in
                    </Button>
                    <Body1>
                        New here?{" "}
                        <RouterLink to="/signup">Create an account</RouterLink>
                    </Body1>
                </form>
            </Card>
        </main>
    );
}

function safeFrom(value: string | null, roles?: ReadonlySet<AppRole>) {
    if (!value || !value.startsWith("/app")) {
        return homePathForRoles(roles);
    }

    return value;
}
