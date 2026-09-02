const apiBase = (
    process.env.VITE_API_BASE_URL ?? "http://127.0.0.1:8080"
).replace(/\/$/, "");
const email = `live-owner-${Date.now()}@example.com`;
const password = "correct-horse-battery";

function problemText(body) {
    if (body && typeof body === "object" && "title" in body) {
        return String(body.title);
    }
    return "request failed";
}

async function readJson(response) {
    const text = await response.text();
    if (!text) {
        return undefined;
    }
    try {
        return JSON.parse(text);
    } catch {
        return undefined;
    }
}

async function call(path, init = {}) {
    const response = await fetch(`${apiBase}${path}`, {
        ...init,
        headers: {
            Accept: "application/json",
            "Content-Type": "application/json",
            ...init.headers,
        },
    });
    const body = await readJson(response);
    return { response, body };
}

function requireToken(body, field) {
    const value = body?.[field];
    if (typeof value !== "string" || value.length < 8) {
        throw new Error(`${field} was missing from the token response`);
    }
    return value;
}

const registered = await call("/api/auth/register", {
    method: "POST",
    body: JSON.stringify({
        email,
        password,
        accountType: "BUSINESS_OWNER",
    }),
});
if (registered.response.status !== 201) {
    throw new Error(
        `register failed: ${registered.response.status} ${problemText(registered.body)}`,
    );
}

const login = await call("/api/auth/login", {
    method: "POST",
    body: JSON.stringify({ email, password }),
});
if (!login.response.ok) {
    throw new Error(
        `login failed: ${login.response.status} ${problemText(login.body)}`,
    );
}
const accessToken = requireToken(login.body, "accessToken");
const refreshToken = requireToken(login.body, "refreshToken");

const refresh = await call("/api/auth/refresh", {
    method: "POST",
    body: JSON.stringify({ refreshToken }),
});
if (!refresh.response.ok) {
    throw new Error(
        `refresh failed: ${refresh.response.status} ${problemText(refresh.body)}`,
    );
}
requireToken(refresh.body, "accessToken");

const trust = await call(
    `/api/public/businesses/00000000-0000-4000-8000-000000000001/trust`,
    {
        method: "GET",
        headers: {
            Authorization: `Bearer ${accessToken}`,
        },
    },
);
if (trust.response.status !== 200 && trust.response.status !== 404) {
    throw new Error(
        `trust lookup failed: ${trust.response.status} ${problemText(trust.body)}`,
    );
}

const onboarding = await call("/api/businesses/onboarding/registered", {
    method: "POST",
    headers: {
        Authorization: `Bearer ${accessToken}`,
    },
    body: JSON.stringify({
        registrationNumber: "2024/123456/07",
    }),
});
if (![201, 400, 409, 422].includes(onboarding.response.status)) {
    throw new Error(
        `onboarding start failed: ${onboarding.response.status} ${problemText(onboarding.body)}`,
    );
}

console.log(
    "Live backend auth succeeded: register, login, refresh, public trust, and onboarding POST.",
);
