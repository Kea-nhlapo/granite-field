/**
 * Real validate-account-holder check against POST /api/auth/momo/validate
 * (za.co.trademesh.modules.access.api.AuthController). Only attempted when
 * VITE_API_MODE=live; PhoneVerifyField falls back to its local simulation on
 * any failure (network down, backend not running, request rejected) so the
 * UI never breaks in a demo.
 */

const SA_MSISDN_LOCAL = /^0[6-8][0-9]{8}$/;
const SA_MSISDN_INTL = /^\+27[6-8][0-9]{8}$/;

function toE164(rawDigits: string): string | null {
    if (SA_MSISDN_INTL.test(rawDigits)) return rawDigits;
    if (SA_MSISDN_LOCAL.test(rawDigits)) return "+27" + rawDigits.slice(1);
    return null;
}

/**
 * Cloudflare Turnstile isn't provisioned (no VITE_TURNSTILE_SITE_KEY yet), so
 * this returns a "local-pass:" token the backend's LocalTurnstileVerifier
 * accepts when trademesh.access.turnstile.provider=local. Swap for a real
 * Turnstile widget render once a site key exists.
 */
async function getTurnstileToken(action: string): Promise<string> {
    const siteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY;
    if (!siteKey) {
        return `local-pass:${action}:${crypto.randomUUID()}`;
    }

    const w = window as unknown as {
        turnstile?: {
            render: (
                container: HTMLElement,
                opts: {
                    sitekey: string;
                    action: string;
                    callback: (token: string) => void;
                    size: "invisible";
                },
            ) => void;
        };
    };

    if (!w.turnstile) {
        await new Promise<void>((resolve, reject) => {
            const script = document.createElement("script");
            script.src =
                "https://challenges.cloudflare.com/turnstile/v0/api.js";
            script.async = true;
            script.onload = () => resolve();
            script.onerror = () =>
                reject(new Error("Failed to load Turnstile"));
            document.head.appendChild(script);
        });
    }

    return new Promise((resolve, reject) => {
        const container = document.createElement("div");
        document.body.appendChild(container);
        const timeout = setTimeout(() => {
            container.remove();
            reject(new Error("Turnstile timed out"));
        }, 8000);

        (
            window as unknown as { turnstile: NonNullable<typeof w.turnstile> }
        ).turnstile.render(container, {
            sitekey: siteKey,
            action,
            size: "invisible",
            callback: (token) => {
                clearTimeout(timeout);
                container.remove();
                resolve(token);
            },
        });
    });
}

export async function validateMomoAccount(rawDigits: string): Promise<boolean> {
    const phoneNumber = toE164(rawDigits);
    if (!phoneNumber) {
        throw new Error("Phone number is not a valid South African MSISDN");
    }

    const turnstileToken = await getTurnstileToken("momo-sign-in");

    const response = await fetch(
        `${import.meta.env.VITE_API_BASE_URL}/api/auth/momo/validate`,
        {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ phoneNumber, turnstileToken }),
        },
    );

    if (!response.ok) {
        throw new Error(
            `MoMo validate request failed with HTTP ${response.status}`,
        );
    }

    const body = (await response.json()) as { active: boolean };
    return body.active;
}
