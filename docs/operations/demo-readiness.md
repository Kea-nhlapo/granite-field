# Demo readiness

## Repeatable backend run

From the repository root, run:

```powershell
.\scripts\run-demo-journey.ps1
```

The test performs the complete local journey twice. Each run starts with clean data and covers:

1. OTP login and Mobile Money consent using local providers.
2. Product requests, supplier invitation, document extraction, and a deliberate document mismatch.
3. Demand grouping, spare-capacity matching, cargo-aware route selection, and route retrieval.
4. Delivery proposal and confirmation, followed by a successful escrow lock.
5. Live position updates, a route deviation, and a stationary fuel drop.
6. A signed delivery QR scan with the wrong quantity.
7. Blocked payment release, recorded resolution, and successful release.
8. WhatsApp notification requests, a changed trust score, a new premium estimate, and insurer evidence.

This run uses deterministic local adapters. It proves our code and fallback path without spending provider credits.

## Live provider preflight

Once credentials are available, run:

```powershell
.\scripts\check-demo-environment.ps1 -Mode Live
```

The script checks that live providers were selected and that the common secrets are present. It never prints secret values.

Then check these items manually because configuration alone cannot prove them:

- Complete one OTP login on the same phone and network that will be used in the demo.
- Complete one Mobile Money consent, collection, and disbursement using the demo accounts.
- Send one SMS and one WhatsApp message through the selected provider. Confirm every demo phone has completed any required WhatsApp opt-in or sandbox join.
- Confirm the server Google key can use Directions and Distance Matrix from the demo server IP.
- Confirm the browser Google key accepts only the deployed frontend domains and can load the Maps JavaScript API.
- Complete one real Turnstile challenge from the deployed frontend hostname.
- Sign in, wait longer than the access-token lifetime, and confirm the frontend refreshes the session without interrupting the journey.
- Run the full deployed journey twice without editing database rows, replaying queue messages by hand, or restarting the backend.

Do not mark the readiness ticket complete until both deployed runs pass.

## If a live provider fails

Keep escrow and QR proof of handover. They are the core of the demo.

Cut features in this order:

1. Backhaul-match ranking.
2. Dynamic premium comparison.
3. Non-essential notification triggers.

Switch only the failing adapter to its local or mock implementation. Do not change the business workflow during the demo.

Keep a prerecorded route response and telemetry sequence ready. Never paste keys into source files, screenshots, or terminal commands that will be shown to judges.
