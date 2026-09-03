import { createServer } from "node:http";
import { randomUUID } from "node:crypto";

const port = Number(process.env.DEMO_API_PORT || 8080);
const businessId = "00000000-0000-4000-8000-000000000101";
const shipmentId = "00000000-0000-4000-8000-000000000201";
const orderId = "00000000-0000-4000-8000-000000000301";
const supplierProfileId = "6c756e67-696c-456d-8000-000000000002";
const paymentAmount = 2250;

const accounts = new Map();
const challenges = new Map();
let escrowStatus = "LOCKED";

seedAccount({
    email: "owner@example.com",
    userId: "00000000-0000-4000-8000-000000000010",
    roles: ["BUSINESS_OWNER"],
    displayName: "Demo SME",
    balance: 4237,
});
seedAccount({
    email: "lungile.mooketsi@trademesh.local",
    userId: "6c756e67-696c-456d-8000-000000000001",
    roles: ["SUPPLIER"],
    displayName: "Lungile Mooketsi",
    balance: 628330,
});

createServer(async (request, response) => {
    cors(response);
    if (request.method === "OPTIONS") return end(response, 204);

    try {
        const url = new URL(request.url || "/", `http://${request.headers.host}`);
        const body = await readJson(request);

        if (request.method === "POST" && url.pathname === "/api/auth/login") {
            const account = accountFor(body.email);
            return json(response, 200, tokens(account));
        }
        if (request.method === "POST" && url.pathname === "/api/auth/register") {
            const account = accountFor(body.email, body.accountType);
            return json(response, 201, tokens(account));
        }
        if (request.method === "POST" && url.pathname === "/api/auth/refresh") {
            const account = accountFromToken(body.refreshToken);
            return account
                ? json(response, 200, tokens(account))
                : problem(response, 401, "Session expired");
        }
        if (request.method === "POST" && url.pathname === "/api/auth/logout") {
            return end(response, 204);
        }

        const account = accountFromToken(request.headers.authorization);
        if (!account) return problem(response, 401, "Sign in to continue");

        if (request.method === "GET" && url.pathname === "/api/sandbox/wallet") {
            return json(response, 200, wallet(account));
        }
        if (
            request.method === "GET" &&
            url.pathname === "/api/sandbox/universal-suppliers"
        ) {
            return json(response, 200, {
                suppliers: [
                    {
                        userId: "6c756e67-696c-456d-8000-000000000001",
                        supplierProfileId,
                        displayName: "Lungile Mooketsi",
                        loginEmail: "lungile.mooketsi@trademesh.local",
                    },
                ],
            });
        }
        if (
            request.method === "GET" &&
            url.pathname === `/api/businesses/${businessId}/shipments/${shipmentId}`
        ) {
            return json(response, 200, shipment());
        }
        if (
            request.method === "POST" &&
            url.pathname === `/api/businesses/${businessId}/shipments/${shipmentId}/transition`
        ) {
            return json(response, 200, shipment(body.targetStatus));
        }
        if (
            request.method === "GET" &&
            url.pathname === `/api/delivery/${shipmentId}/route`
        ) {
            return json(response, 200, {
                shipmentId,
                providerName: "TradeMesh local demo",
                geometry: [
                    { label: "Johannesburg depot", latitude: -26.2041, longitude: 28.0473 },
                    { label: "Soweto delivery", latitude: -26.2485, longitude: 27.854 },
                ],
                distanceMetres: 28400,
                durationSeconds: 2700,
                generatedAt: new Date().toISOString(),
            });
        }
        if (
            request.method === "GET" &&
            url.pathname === `/api/businesses/${businessId}/shipments/${shipmentId}/telemetry/live`
        ) {
            return json(response, 200, {
                shipmentId,
                latitude: -26.2312,
                longitude: 27.9214,
                speedKilometresPerHour: 42,
                batteryPercent: 86,
                networkStatus: "CONNECTED",
                recordedAt: new Date().toISOString(),
            });
        }
        if (
            request.method === "POST" &&
            url.pathname === `/api/businesses/${businessId}/shipments/${shipmentId}/handovers/challenges`
        ) {
            const challengeId = randomUUID();
            const qrPayload = Buffer.from(`${challengeId}.${randomUUID()}`).toString("base64url");
            const challenge = {
                challengeId,
                shipmentId,
                type: body.type || "DELIVERY",
                deliveryOrderId: body.deliveryOrderId || orderId,
                state: "PENDING",
                expectedQuantity: 20,
                unitOfMeasure: "EACH",
                initiatorUserId: account.userId,
                counterpartyUserId: body.counterpartyUserId,
                expectedLocation: {
                    label: "Soweto delivery",
                    latitude: -26.2485,
                    longitude: 27.854,
                },
                locationToleranceMetres: 250,
                expiresAt: new Date(Date.now() + 15 * 60_000).toISOString(),
                confirmations: [],
            };
            challenges.set(qrPayload, challenge);
            return json(response, 201, { challenge, qrPayload });
        }
        if (request.method === "POST" && url.pathname === "/api/handovers/confirmations") {
            const challenge = challenges.get(body.qrPayload);
            if (!challenge) return problem(response, 404, "Handoff code was not found");
            if (![challenge.initiatorUserId, challenge.counterpartyUserId].includes(account.userId)) {
                return problem(response, 403, "This handoff belongs to another account");
            }
            if (!challenge.confirmations.some((entry) => entry.actorUserId === account.userId)) {
                challenge.confirmations.push({
                    confirmationId: randomUUID(),
                    actorUserId: account.userId,
                    party: account.userId === challenge.initiatorUserId ? "INITIATOR" : "COUNTERPARTY",
                    observedAt: body.observedAt,
                    receivedAt: new Date().toISOString(),
                    latitude: body.latitude,
                    longitude: body.longitude,
                    distanceMetres: 0,
                    quantityOutcome: body.quantityOutcome,
                });
            }
            if (challenge.confirmations.length === 2) {
                challenge.state = "COMPLETED";
                challenge.completedAt = new Date().toISOString();
                console.log("[demo SMS] SME + supplier: both parties signed the QR handoff");
            }
            return json(response, 200, challenge);
        }
        const challengeMatch = url.pathname.match(
            /^\/api\/businesses\/[^/]+\/shipments\/[^/]+\/handovers\/challenges\/([^/]+)$/,
        );
        if (request.method === "GET" && challengeMatch) {
            const challenge = [...challenges.values()].find(
                (entry) => entry.challengeId === challengeMatch[1],
            );
            return challenge
                ? json(response, 200, challenge)
                : problem(response, 404, "Handoff was not found");
        }
        if (request.method === "GET" && url.pathname === `/api/delivery/${shipmentId}/escrow`) {
            return json(response, 200, escrow());
        }
        if (request.method === "POST" && url.pathname === `/api/delivery/${shipmentId}/release`) {
            const completed = [...challenges.values()].some((entry) => entry.state === "COMPLETED");
            if (!completed) return problem(response, 409, "Both parties must sign before payment");
            if (escrowStatus !== "RELEASED") {
                escrowStatus = "RELEASED";
                transferPayment();
                console.log(`[demo SMS] Lungile Mooketsi: R${paymentAmount.toFixed(2)} received`);
                console.log(`[demo SMS] Demo SME: R${paymentAmount.toFixed(2)} payment released`);
            }
            return json(response, 200, escrow());
        }

        return problem(response, 404, "Demo route is not available");
    } catch (error) {
        console.error(error);
        return problem(response, 500, "Demo API failed");
    }
}).listen(port, "0.0.0.0", () => {
    console.log(`TradeMesh local demo API ready on http://0.0.0.0:${port}`);
    console.log(`Demo business ${businessId} · shipment ${shipmentId}`);
});

function seedAccount(account) {
    accounts.set(account.email.toLowerCase(), { ...account, entries: [] });
}

function accountFor(email = "", accountType = "BUSINESS_OWNER") {
    const normalized = email.trim().toLowerCase();
    if (!accounts.has(normalized)) {
        seedAccount({
            email: normalized,
            userId: randomUUID(),
            roles: [accountType || "BUSINESS_OWNER"],
            displayName: normalized.split("@")[0] || "New account",
            balance: 50,
        });
    }
    return accounts.get(normalized);
}

function tokens(account) {
    const token = `demo.${Buffer.from(account.email).toString("base64url")}`;
    return {
        userId: account.userId,
        tokenType: "Bearer",
        accessToken: token,
        expiresInSeconds: 86400,
        refreshToken: token,
        roles: account.roles,
    };
}

function accountFromToken(value = "") {
    try {
        const token = value.replace(/^Bearer\s+/i, "");
        const email = Buffer.from(token.split(".")[1], "base64url").toString();
        return accounts.get(email);
    } catch {
        return undefined;
    }
}

function wallet(account) {
    return {
        userId: account.userId,
        displayName: account.displayName,
        currency: "ZAR",
        availableBalance: account.balance,
        heldBalance: 0,
        updatedAt: new Date().toISOString(),
        entries: account.entries,
    };
}

function shipment(status = "IN_TRANSIT") {
    return {
        shipmentId,
        requestedByBusinessId: businessId,
        status,
        reservedCapacity: { weightKg: 1200, volumeCubicMetres: 6 },
        loadOrders: [
            {
                orderId,
                buyerBusinessId: businessId,
                destinationLabel: "Soweto delivery",
                latitude: -26.2485,
                longitude: 27.854,
                cargoItems: [{ productCode: "MAIZE-10KG", unitOfMeasure: "EACH" }],
            },
        ],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
    };
}

function escrow() {
    return {
        escrowId: "00000000-0000-4000-8000-000000000401",
        shipmentId,
        businessId,
        currency: "ZAR",
        agreedAmount: paymentAmount,
        status: escrowStatus,
        updatedAt: new Date().toISOString(),
        transactions: [],
    };
}

function transferPayment() {
    const owner = accounts.get("owner@example.com");
    const supplier = accounts.get("lungile.mooketsi@trademesh.local");
    owner.balance -= paymentAmount;
    supplier.balance += paymentAmount;
    const now = new Date().toISOString();
    owner.entries.unshift(entry("ESCROW_SETTLED", -paymentAmount, owner.balance, "Payment released to Lungile", now));
    supplier.entries.unshift(entry("PAYMENT_RECEIVED", paymentAmount, supplier.balance, "Payment received from Demo SME", now));
}

function entry(type, delta, balance, description, createdAt) {
    return {
        entryId: randomUUID(),
        type,
        availableDelta: delta,
        heldDelta: 0,
        availableBalanceAfter: balance,
        heldBalanceAfter: 0,
        description,
        createdAt,
    };
}

function cors(response) {
    response.setHeader("Access-Control-Allow-Origin", "*");
    response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type");
    response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
}

async function readJson(request) {
    if (!["POST", "PUT", "PATCH"].includes(request.method)) return {};
    let text = "";
    for await (const chunk of request) text += chunk;
    return text ? JSON.parse(text) : {};
}

function json(response, status, value) {
    response.writeHead(status, { "Content-Type": "application/json" });
    response.end(JSON.stringify(value));
}

function problem(response, status, detail) {
    return json(response, status, {
        type: "about:blank",
        title: detail,
        status,
        detail,
        instance: "/api",
        code: "LOCAL_DEMO_ERROR",
        requestId: randomUUID(),
    });
}

function end(response, status) {
    response.writeHead(status);
    response.end();
}
