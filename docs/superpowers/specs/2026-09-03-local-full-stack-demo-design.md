# Local full-stack demo frontend

## Status and constraints

This design is approved for local implementation only. The resulting files must
remain uncommitted and must not be pushed, opened as a pull request, deployed, or
used to mutate GitHub configuration. WhatsApp is outside the demo scope. SMS is
the only live mobile notification channel.

## Goal

Build a mobile-first TradeMesh demo that drives the existing backend through one
coherent business journey:

1. Sign in and complete registered-business onboarding.
2. Create a procurement request and capture a supplier quote.
3. Confirm the quote and carry the resulting order into logistics.
4. Search for capacity, reserve a match, and calculate a route.
5. Create and advance a shipment, display tracking data, and complete a
   two-account QR handover.
6. Release escrow only after both parties have signed and show the corresponding
   sandbox-wallet entries.
7. Save each user's phone contact and SMS preference so supported backend events
   can trigger the existing Infobip integration.

The demo must show state returned by the backend. It must not report success when
the backend call failed or substitute invented identifiers for missing resources.

## Approach

Keep the current React 19, React Router, Fluent UI, Vite, and generated OpenAPI
client foundation. Use the cumulative frontend work in `origin/pr-92` only as a
visual and interaction reference. Do not copy its synchronous fake API client,
stale generated types, or in-memory domain state.

The current generated client is the API boundary. Small handwritten adapters may
convert generated request/response types into view models, but they may not invent
server outcomes. Mock Service Worker remains available for automated tests and is
disabled for the live local demo.

## Information architecture

The authenticated application uses a compact mobile shell with five destinations:

- **Board** shows the active order or shipment and the next useful action.
- **Source** creates a request, records a quote, and confirms the order.
- **Route** finds transport capacity and calculates the shipment route.
- **Track** shows shipment state and telemetry, then opens handover.
- **Account** manages the notification phone number, SMS consent, and sign-out.

The shell also shows the signed-in account's sandbox balance. A wallet sheet
shows the ledger without mixing another account's state. The Track flow renders
a numbered handoff timeline: QR issued, supplier signed, SME signed, payment
released. Every step names the actor and the server-confirmed state.

Secondary steps open inside the shell with a clear back action. Navigation labels
describe the user's task rather than backend module names.

## Components and responsibilities

- `AppShell` owns navigation, responsive layout, and session-level controls.
- `DemoJourneyProvider` owns only the backend identifiers needed to continue the
  current local journey: business, request, quote, order, capacity search,
  reservation, route calculation, shipment, and handover IDs. It persists them in
  local storage so a browser refresh does not destroy the demo, and offers a
  visible reset action.
- Feature pages own form state and invoke generated API operations through focused
  service adapters.
- Shared status components render loading, inline validation, API problems, empty
  states, and confirmed outcomes consistently.
- No component writes domain data directly into the journey store until the
  backend has returned a successful response.

## Backend data flow

### Access and onboarding

The existing session provider continues to use `authLogin`, `authRefresh`, and
`authLogout`. Registered-business onboarding uses
`businessStartRegisteredOnboarding`, `businessGetRegisteredOnboarding`, and
`businessConfirmRegisteredOnboarding`. The returned business identifier seeds the
journey state.

### Procurement

`procurementCreateRequest` creates the request. `procurementCreateQuote` records a
supplier quote against that request. `procurementConfirmQuote` creates the order.
Read operations reload request, quote, and order details when their screens open.

### Logistics and routing

`capacityMatchingSearch` starts the match search and
`capacityMatchingReserve` reserves the chosen result. `routingCalculate` creates a
route calculation and `routingGet` reloads it. If the backend requires transport
profile, vehicle, driver, or offer data, the UI creates those prerequisites with
the existing transport endpoints and shows them as completed setup steps.

### Shipment, tracking, and handover

`shipmentCreate` creates the shipment from confirmed business data.
`shipmentTransition` advances valid states only. Tracking reads current position
and events through the telemetry endpoints. Handover uses `handoverIssue`,
`handoverGet`, and `handoverConfirm`; QR/challenge values come from the backend and
are never fabricated by the frontend.

The SME issues the challenge for a named counterparty and displays the signed
payload as a high-contrast QR code. A separately authenticated supplier scans it
with a camera-capable browser or pastes the payload as a fallback. Each account
submits its own confirmation. The UI must not call a handover complete until the
backend reports two accepted confirmations.

The local profile exposes one universal supplier named Lungile Mooketsi. The
seed is idempotent, uses a stable user and supplier-profile identity, and is not
created outside the local profile. Only a one-way password hash is stored in the
local seeder; the supplied password is never written to documentation, frontend
storage, rendered output, or logs. Lungile's SMS contact is preconfigured with
consent and the shipment-update preference enabled.

### Sandbox wallets and payment release

The local profile stores one wallet and an append-only, idempotent ledger per
user. Lungile's seeded wallet opens at ZAR 628,330.00, the designated existing
SME demo wallet opens at ZAR 4,237.00, and every account without an explicit
demo seed opens lazily at ZAR 50.00. Wallet responses show available balance,
escrow held, currency, and recent entries. Wallet values come from the backend;
the frontend never calculates a replacement balance.

Locking escrow records one SME debit or hold. Releasing a clean eligible escrow
records one supplier credit. Event or command retries reuse a unique reference
and cannot duplicate either entry. The SME sees an explicit **Release payment**
action only after the handover is complete; payment is not automatically
authorized by the second signature.

The payment release event carries the supplier profile identity. Notification
routing resolves its claimed supplier user instead of broadcasting the payment
message to the buyer business. The supplier SMS includes the released amount,
currency, and resulting sandbox balance. Both handover participants separately
receive the existing final-handover SMS.

### SMS notifications

The Account screen exports and uses the generated notification-contact operations
to save an E.164 phone number. It uses `notificationPreferenceGet` and
`notificationPreferenceSet` for SMS consent. WhatsApp controls and claims are
absent. Provider credentials remain outside the browser and repository.

## Visual direction

Build the interface as a freight dispatch manifest, not a SaaS dashboard. Use
the existing MTN MoMo yellow and navy as ink-and-paper colors: navy for the
control rail and load header, yellow for numbered steps and stamped status
blocks, and warm off-white for working sheets. Use square corners, visible grid
lines, condensed spacing, monospace references, oversized load headings, and
asymmetric status blocks. The work should resemble a waybill or dispatch board
that has been translated into software.

Remove rounded card grids, decorative hero graphics, soft shadows, floating
pills, and generic metric tiles. The Board presents one load manifest, a four-leg
clearance strip, a connected reference ledger, and the next dispatch action.
Workflow screens use numbered form sections with firm boundaries. Route visuals
use the same line-and-marker language as the manifest instead of a decorative
map.

Motion is limited to 150–200 ms interaction feedback. Avoid decorative
gradients, emoji icons, invented metrics, and dashboard clutter.

Body text remains at least 16px where users enter or review important data. Every
interactive target is at least 44px high. Keyboard focus is visible, color is not
the only status signal, and reduced-motion preferences are respected.

## Product copy

Copy must name the action and its consequence. Use short labels such as “Confirm
quote” and concrete status text such as “Order confirmed by the backend.” Avoid
generic claims, celebratory filler, technical endpoint names, and AI-style phrases.
Errors retain the backend request ID when available so a failed demo action can be
diagnosed quickly.

## Loading and error behavior

Actions disable only while their own request is running and show progress next to
the triggering control. Validation appears beside the relevant input. API failures
remain on the current screen with a retry action and do not clear entered values.
Unauthorized responses follow the existing refresh/logout behavior. Missing
prerequisite IDs produce a plain recovery action rather than a broken request.

## Local runtime

The backend and PostgreSQL run from the repository's local container setup. Vite
uses the local API base URL and does not start MSW. CORS and authentication use the
backend's existing local configuration. No Infobip key or other server credential
is exposed through Vite variables, browser storage, rendered HTML, or test output.

## Verification

- Unit tests cover journey-state persistence, API error rendering, route guards,
  wallet ownership, idempotent credits and debits, supplier notification
  routing, QR confirmation roles, and the absence of WhatsApp controls.
- Frontend formatting, lint, typecheck, tests, and production build pass.
- Backend tests remain green.
- A local smoke journey proves that frontend-created identifiers are accepted by
  the next backend step through procurement, routing, shipment, and handover.
- Browser checks cover a narrow mobile viewport and a desktop viewport, keyboard
  navigation, visible focus, loading feedback, and no horizontal overflow.

## Acceptance criteria

- The live local frontend makes backend requests with the generated client.
- Every success state displayed during the demo corresponds to a successful
  backend response.
- The primary journey can be completed without manually copying identifiers.
- SMS contact and preference settings persist through the backend.
- Separate SME and supplier sessions can complete the same signed QR handover.
- Both participants receive handover completion SMS requests.
- Only the claimed supplier receives the payment-release SMS request.
- Lungile's local wallet and new-account default balances are server-backed and
  payment release changes the supplier balance exactly once.
- WhatsApp is not presented as available.
- No secrets enter frontend code or browser state.
- `git status` shows only local, uncommitted implementation files; no commit or
  remote mutation is made.
