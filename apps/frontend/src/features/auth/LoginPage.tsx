import { useState, type FormEvent } from "react";
import { useLocation, useNavigate } from "react-router";

import { ApiError } from "../../shared/api/errors";
import { useSession } from "../access/session";

const emailPattern = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function LoginPage() {
    const { login } = useSession();
    const navigate = useNavigate();
    const location = useLocation();
    const from =
        (location.state as { from?: string } | null)?.from &&
        !(location.state as { from?: string }).from?.startsWith("/invite")
            ? (location.state as { from: string }).from
            : "/app";
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [errors, setErrors] = useState<{ email?: string; password?: string }>(
        {},
    );
    const [formError, setFormError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);

    async function onSubmit(event: FormEvent) {
        event.preventDefault();
        const next: { email?: string; password?: string } = {};
        if (!email.trim()) next.email = "Enter your work email.";
        else if (!emailPattern.test(email.trim()))
            next.email = "Enter a valid email address.";
        if (!password) next.password = "Enter your password.";
        else if (password.length < 8)
            next.password = "Use at least 8 characters.";
        setErrors(next);
        setFormError(null);
        if (next.email || next.password) return;
        setBusy(true);
        try {
            await login(email.trim(), password);
            navigate(from, { replace: true });
        } catch (error) {
            setFormError(
                error instanceof ApiError
                    ? error.message
                    : "Sign-in is unavailable right now.",
            );
        } finally {
            setBusy(false);
        }
    }

    return (
        <main className="min-h-dvh flex items-center justify-center p-6 bg-[var(--surface)]">
            <form
                className="w-full max-w-sm bg-white rounded-2xl p-6 space-y-4 border border-gray-100"
                onSubmit={(event) => {
                    void onSubmit(event);
                }}
                noValidate
            >
                <img
                    src="/trademesh-logo.png"
                    alt="TradeMesh"
                    className="h-16 w-auto"
                />
                <h1
                    className="text-xl font-semibold"
                    style={{ color: "var(--navy)" }}
                >
                    Log in
                </h1>
                {formError ? (
                    <p
                        role="alert"
                        className="text-sm"
                        style={{ color: "var(--error)" }}
                    >
                        {formError}
                    </p>
                ) : null}
                <label className="block text-sm">
                    Email
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3"
                        name="email"
                        type="email"
                        autoComplete="username"
                        aria-label="Email"
                        value={email}
                        onChange={(event) => setEmail(event.target.value)}
                    />
                    {errors.email ? (
                        <span className="text-xs text-red-600">
                            {errors.email}
                        </span>
                    ) : null}
                </label>
                <label className="block text-sm">
                    Password
                    <input
                        className="mt-1 w-full h-10 border rounded-xl px-3"
                        name="password"
                        type="password"
                        autoComplete="current-password"
                        aria-label="Password"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                    />
                    {errors.password ? (
                        <span className="text-xs text-red-600">
                            {errors.password}
                        </span>
                    ) : null}
                </label>
                <button
                    type="submit"
                    disabled={busy}
                    className="w-full h-10 rounded-lg font-semibold"
                    style={{
                        background: "var(--yellow)",
                        color: "var(--navy)",
                    }}
                >
                    {busy ? "Signing in" : "Log in"}
                </button>
            </form>
        </main>
    );
}
