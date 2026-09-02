# Live SMS and WhatsApp Notifications Design

**Date:** 2026-09-02

**Status:** Approved for implementation planning

**Provider:** Infobip Messages API

## Context

TradeMesh already has transactional email delivery, notification preferences, encrypted notification data, and a PostgreSQL-backed outbox. While this design was under review, upstream also added a minimal Twilio mobile adapter and a real escrow workflow. The mobile adapter accepts arbitrary destinations and message bodies and records no durable delivery state, consent, or callbacks, so it is migration scaffolding rather than the target architecture. Issue 76 asks for real SMS and WhatsApp notifications for transport matching, handover verification, and escrow release. Twilio is not an acceptable production notification dependency for this deployment, so the two channels will use Infobip.

This design extends the existing notification module instead of putting provider calls into transport or handover services. Domain modules identify the recipient user and the business event. The notification module owns consent, contact details, templates, persistence, submission, retries, and delivery callbacks.

The implementation is not considered live merely because an HTTP request succeeds. Completion requires an opt-in smoke test in which an environment-configured South African handset receives both an SMS and a WhatsApp message and the corresponding provider delivery reports are recorded.

## Goals

- Send real SMS and WhatsApp notifications through Infobip.
- Obtain each user's phone number and channel consent through authenticated APIs; never use a built-in recipient.
- Keep credentials, provider URLs, senders, provider template identifiers, and live-test recipients outside source code.
- Encrypt phone numbers and message variables at rest.
- Use deterministic application idempotency and the existing transactional outbox.
- Record provider identifiers, attempts, and authenticated delivery/read callbacks.
- Integrate current capacity-match and handover events without coupling those modules to Infobip.
- Consume the real escrow-release event now present in the payment module.

## Non-goals

- Replacing the existing email system.
- Building marketing campaigns, inbound chat, two-way support conversations, or a template-management UI.
- Changing escrow payment behavior as part of notification delivery.
- Automatically running paid live-message tests in CI.
- Guaranteeing provider-side exactly-once delivery. The system prevents duplicate application requests and handles ambiguous provider submissions conservatively.

## Provider Choice

Infobip Messages API provides both SMS and WhatsApp behind one provider relationship and supplies delivery and seen reports. A local capture adapter remains available for development and automated tests. The internal port is provider-neutral so another adapter can be introduced without changing feature modules.

The free trial is useful for initial verification but is constrained to verified recipients and test senders. Production WhatsApp requires an approved sender and, outside the customer-service window, approved WhatsApp templates. Those provider-account steps are deployment prerequisites rather than values embedded in the application.

## Architecture

The notification module gains a channel-neutral message path alongside the existing email path:

```text
domain event
  -> notification event listener
  -> consent/contact/template validation
  -> message_notification + transactional outbox
  -> message delivery coordinator
  -> local capture or Infobip adapter
  -> Infobip delivery/seen webhook
  -> notification status and attempt history
```

User-event integrations call an application-facing notification port with:

- recipient user ID;
- notification category;
- channel-independent event identity;
- stable template key and version; and
- a small allow-listed set of non-sensitive template variables.

They do not pass phone numbers, provider senders, API keys, URLs, or provider payloads.

The existing delivery-confirmation flow is the one narrow exception because it addresses a recipient supplied for that delivery proposal rather than an existing TradeMesh user. Its arbitrary body is replaced with the same versioned-template boundary, and its requested channel and E.164 destination remain transaction input. This compatibility route does not become a general public send-message API.

The notification module performs channel fan-out after checking the recipient's contact point, explicit consent, and per-category preferences. SMS and WhatsApp become separate persisted notifications with separate outbox entries and delivery histories.

## Application Interfaces

### Notification command

Extend `NotificationRequests` with a provider-neutral message request. The request contains recipient user ID, category, event type, event ID, template key/version, and safe template variables. It does not accept an arbitrary destination or arbitrary message body.

The application idempotency key is constructed from immutable values:

```text
event-type:event-id:recipient-user-id:channel:template-key:template-version
```

A unique database constraint prevents two notification records for the same key. Reprocessing an event returns the existing result rather than enqueueing another message.

### Contact API

Authenticated self-service endpoints manage the current user's phone contact:

- `GET /api/notification-contacts/phone` returns presence, a masked number, and consent timestamps.
- `PUT /api/notification-contacts/phone` accepts an E.164 phone number plus explicit `smsConsent` and `whatsappConsent` booleans.
- `DELETE /api/notification-contacts/phone` removes the encrypted contact and disables both phone-based channels.

The server canonicalizes and validates E.164 input before storage. A successful write returns only a masked representation, never the full stored number. Users may only read or change their own contact. A consent value defaults to false and cannot be inferred from simply providing a number.

### Preference API

The existing category preference response gains `smsEnabled` and `whatsappEnabled`. Existing email behavior remains unchanged.

For backward compatibility, update requests use partial-update semantics: every channel field is nullable, at least one must be present, and omitted fields keep their current values. Enabling SMS or WhatsApp without a corresponding consented contact returns a validation error. Disabling a channel is always allowed.

## Persistence

### Existing preference extension

Add the following non-null columns to `notification_preference`, both defaulting to false:

- `sms_enabled BOOLEAN NOT NULL DEFAULT FALSE`
- `whatsapp_enabled BOOLEAN NOT NULL DEFAULT FALSE`

Existing users therefore receive no new phone-based messages until they explicitly opt in.

### Contact point

Create `notification_contact_point` with:

- owning user ID as the unique key;
- encrypted canonical E.164 number;
- a keyed fingerprint used for safe equality/change detection;
- last digits for masked display;
- SMS consent timestamp;
- WhatsApp consent timestamp;
- created and updated timestamps.

The existing notification encryption facility is reused and generalized where naming currently implies email-only data. Plain phone numbers must not appear in indexes, logs, audit metadata, or exception messages.

### Message notification

Create `message_notification` with:

- notification UUID;
- unique application idempotency key;
- recipient user ID;
- channel (`SMS` or `WHATSAPP`);
- category;
- template key and version;
- encrypted recipient snapshot;
- encrypted serialized template variables;
- status;
- provider message ID when known;
- provider correlation value containing only the opaque notification UUID;
- accepted, sent, delivered, read, failed, created, and updated timestamps as applicable.

Snapshotting the encrypted recipient and template variables makes delivery deterministic if the user edits their contact after the business transaction commits. Deleting a contact prevents future notifications but does not corrupt audit history. Retention and erasure must follow the application's existing privacy policy.

### Delivery attempts

Create `message_delivery_attempt` with notification ID, outbox message ID, attempt number, provider, safe result code, status, timestamps, and an optional sanitized failure description. Store no credentials, raw provider request, full recipient, or rendered body.

## Templates and Message Safety

Templates are stable, versioned resource files rather than Java string literals or arbitrary caller-provided text. Each key has an SMS renderer and a WhatsApp mapping. Provider-approved WhatsApp template identifiers are supplied through environment configuration because they differ between Infobip accounts and environments.

Initial template keys are:

- `capacity-match-found.v1`
- `handover-confirmation-accepted.v1`
- `handover-finalized-clean.v1`
- `handover-finalized-disputed.v1`
- `escrow-released.v1`

Messages link the user back to authenticated TradeMesh views and avoid names, exact locations, QR secrets, prices, bank details, and full shipment identifiers. Variables are defined per template and unknown variables are rejected. URLs come from a validated application base URL configuration, not event payloads.

## Domain Event Integration

Listeners use the established `PublishedEvent` and after-commit listener pattern. They enqueue notifications in a new transaction so provider work never occurs in a domain transaction.

### Capacity matching

On `CAPACITY_MATCH_COMPLETED`, notify the requesting user only when `compatibleOfferCount` is greater than zero. Resolve the recipient from the authenticated actor/event ownership rather than a configured phone number. The notification says that matches are ready for review without exposing offer details.

### Handover verification

On `HANDOVER_CONFIRMATION_ACCEPTED`, resolve the handover participants through a notification query boundary and notify the participant other than the actor.

On `HANDOVER_FINALIZED`, notify both participants with the clean or disputed result. Repeated or replayed events are harmless because the event, recipient, channel, and template version form the idempotency key.

### Escrow release

On `ESCROW_RELEASED`, resolve active members of the event's business through a narrow access-module application boundary and notify them with `escrow-released.v1`. The event ID and recipient user ID provide idempotency; amount, supplier phone, and payment-provider details are not included in the notification.

## Provider Configuration

Application configuration is environment-driven:

```text
MESSAGING_PROVIDER=local|infobip
MESSAGING_MAX_DELIVERY_ATTEMPTS=
INFOBIP_BASE_URL=
INFOBIP_API_KEY=
INFOBIP_SMS_SENDER=
INFOBIP_WHATSAPP_SENDER=
INFOBIP_WEBHOOK_HMAC_SECRET=
INFOBIP_WHATSAPP_TEMPLATE_CAPACITY_MATCH_V1=
INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_ACCEPTED_V1=
INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_CLEAN_V1=
INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_DISPUTED_V1=
INFOBIP_WHATSAPP_TEMPLATE_ESCROW_RELEASED_V1=
TRADEMESH_PUBLIC_APP_URL=
```

`.env.example` documents blank placeholders only. Docker Compose passes values through without supplying real secrets or a demo recipient. AWS injects secrets from its secret store/task or instance environment. No production secret enters Git, a Docker image, a log line, or an API response.

When `MESSAGING_PROVIDER=infobip`, startup validation fails fast if the base URL, API key, required sender, HMAC secret, or required active template mapping is absent or malformed. Local and test profiles use the capture adapter and cannot accidentally call Infobip.

Delivery and seen endpoints are configured on Infobip notification subscriptions rather than as unsigned per-request callback URLs. This is required because Infobip's built-in HMAC authentication belongs to subscription notification profiles. Deployment documentation gives the two public HTTPS endpoints to enter in the Infobip portal.

The optional live smoke-test recipient is read only by a manually invoked test profile or script. It is not an application fallback and is never evaluated during ordinary startup or CI.

## Infobip Submission

The adapter maps the internal request to the Infobip Messages API and sets:

- the configured channel sender;
- the decrypted destination only in memory for the duration of the request;
- a deterministic custom message identifier/correlation value where the API permits it;
- opaque callback data containing the internal notification UUID only.

On a successful response, persist Infobip's provider message ID and accepted/queued state before acknowledging the outbox message.

Application idempotency does not imply that Infobip deduplicates a repeated HTTP request. If a timeout or connection loss happens after submission may have reached the provider, mark the notification `SUBMISSION_UNKNOWN` and reconcile it using the provider report endpoint and known correlation identifiers. Do not blindly resend an ambiguous submission. If reconciliation cannot establish an outcome within the bounded policy, dead-letter it for operator action rather than risk a duplicate customer message.

Failures known to occur before provider acceptance, HTTP 429 responses, and transient 5xx responses use the existing bounded exponential retry policy. Authentication, malformed request, invalid destination, missing/invalid WhatsApp template, and provider rejection are permanent failures unless configuration is corrected and an operator explicitly requeues them.

## Status Model

Internal states are:

- `PENDING`
- `SUBMITTING`
- `SUBMISSION_UNKNOWN`
- `ACCEPTED`
- `QUEUED`
- `SENT`
- `DELIVERED`
- `READ`
- `FAILED`
- `REJECTED`
- `EXPIRED`
- `SUPPRESSED`

`SUPPRESSED` is an auditable application decision caused by missing consent, disabled preferences, or an absent contact; it is not submitted to the outbox. Terminal provider states do not regress when late or out-of-order callbacks arrive. `READ` implies delivery, and later `SENT` or `DELIVERED` callbacks cannot move it backward.

## Webhook Security and Processing

Delivery and seen endpoints are unauthenticated only at the normal user-session layer. They require a valid Infobip HMAC signature over the exact raw request body using `INFOBIP_WEBHOOK_HMAC_SECRET`. Verification uses constant-time comparison and occurs before JSON deserialization or persistence.

The handler:

1. rejects missing or invalid signatures;
2. validates body size and schema;
3. resolves the message using provider ID or opaque internal correlation value;
4. records an idempotent callback observation;
5. applies only a valid monotonic status transition; and
6. returns success for an already processed callback.

Unknown message IDs are recorded through safe metrics without logging the supplied recipient or body. The callback endpoint is rate-limited at the edge where available.

## Reliability and Privacy Rules

- Validate and canonicalize destinations before an outbox entry is created.
- Default SMS and WhatsApp consent and preferences to off.
- Never log API keys, full phone numbers, rendered bodies, encrypted ciphertext, template variables, QR data, or private shipment data.
- Log only notification UUID, channel, template key/version, safe result code, and masked provider identifier.
- Keep provider calls outside business transactions.
- Use bounded retries and retain failed/dead-letter records for diagnosis.
- Ensure event replay, worker restart, callback replay, and callback reordering are idempotent.
- Decrypt sensitive fields as late as possible and do not retain plaintext in long-lived objects.

## Testing

### Unit tests

- E.164 validation, canonicalization, masking, and encrypted round trips.
- Consent and preference rules, including defaults and partial updates.
- Template variable allow lists and resource loading.
- Deterministic idempotency keys.
- Infobip response/error classification and status transition monotonicity.
- HMAC verification using exact raw bytes and constant-time comparison.

### Integration tests

- PostgreSQL migrations, constraints, encryption, preferences, and contact ownership.
- Current capacity and handover events create the expected per-recipient/per-channel records.
- Missing consent/contact/preferences suppress delivery.
- Outbox retries are bounded and duplicate events do not duplicate notifications.
- Callback replay and out-of-order callback sequences converge on the correct state.

### Provider contract tests

A local fake HTTP server verifies Infobip request paths, headers, channel payloads, provider IDs, delivery reports, error mappings, timeouts, and reconciliation behavior. It receives synthetic numbers only.

### Live smoke test

An explicitly invoked, excluded-by-default test sends one SMS and one WhatsApp message to an environment-provided verified handset. It verifies receipt and stored callback status. CI never runs it automatically. The operator supplies all account credentials, senders, template mappings, public callback URLs, and recipient values at execution time.

## Deployment and Operations

Before enabling the live adapter, an operator must:

1. create or select the Infobip account;
2. provision an SMS sender suitable for South Africa;
3. register the production WhatsApp sender;
4. approve and map the required WhatsApp templates;
5. create a least-privilege API key;
6. configure publicly reachable HTTPS delivery/seen subscription callbacks and their HMAC secret;
7. inject configuration and secrets into AWS;
8. apply database migrations;
9. run the manual two-channel smoke test; and
10. inspect notification attempts and callback state before enabling event listeners for users.

Operational metrics should cover pending age, submission latency, accepted/delivered/failed counts by channel and safe result code, retry/dead-letter count, invalid callback signatures, and reconciliation backlog. Alerts must not contain PII.

## Acceptance Criteria

- No credentials, sender identities, recipients, provider template identifiers, provider URLs, or message bodies are embedded in Java code or committed environment files.
- A signed-in user can save a validated phone contact, explicitly consent to each channel, and manage per-category SMS/WhatsApp preferences.
- Phone and template data are encrypted at rest and masked in all responses and logs.
- Capacity-match and handover events create deterministic, outbox-backed notifications for the correct users.
- An Infobip adapter submits both channels, stores provider message IDs, classifies failures, and handles ambiguous submissions without blind retries.
- Authenticated delivery and seen callbacks update status idempotently and monotonically.
- Automated unit, database integration, provider contract, and webhook tests pass.
- A manual smoke test proves that both channels reach an environment-configured South African handset and their delivery reports are stored.
- The real escrow-release event creates deterministic notifications for the correct business users without exposing payment details.
