# Infobip SMS and WhatsApp operations

TradeMesh sends event-driven SMS and WhatsApp messages through Infobip. The backend
stores one durable record per channel, submits it through the transactional outbox,
and accepts only HMAC-authenticated delivery and seen reports. Phone numbers and
template data are encrypted at rest; logs and API responses do not expose them.

## Provision Infobip

1. Create a production Infobip account and record its account-specific HTTPS API
   base URL. Do not use a generic URL copied from an example account.
2. Create a least-privilege API key that can send Messages API traffic and read
   message reports. Keep account administration and billing permissions off it.
3. Provision a sender permitted to send application-to-person SMS to South African
   numbers. Infobip must confirm whether the approved value is an alphanumeric sender
   or a provisioned number for the account and traffic class.
4. Register the WhatsApp sender and complete Meta business verification if Infobip
   requires it. Record the sender exactly as Infobip displays it.
5. Submit these six English WhatsApp templates and wait for approval before enabling
   the live provider:

   - `capacity-match-found.v1`
   - `handover-confirmation-accepted.v1`
   - `handover-finalized-clean.v1`
   - `handover-finalized-disputed.v1`
   - `escrow-released.v1`
   - `delivery-confirmation.v1` (one body parameter: the confirmation URL)

The environment values are the approved Infobip template names, not the internal
keys above. Template wording must match the corresponding files under
`apps/backend/src/main/resources/notification-templates`.

## Configure AWS

On the EC2 host, edit `/opt/trademesh/granite-field/infra/containers/.env` with
permissions `0600`. Set every `INFOBIP_*` value from `.env.aws.example`, keep
`MOBILE_NOTIFICATION_PROVIDER=infobip`, and generate the callback secret separately:

```bash
openssl rand -base64 32
```

Never put real keys, secrets, senders, template names, or test phone numbers in Git,
issue comments, command history, or application logs. After editing, validate the
Compose contract without printing resolved values:

```bash
docker compose -f docker-compose.aws.yml --env-file .env config --quiet
```

The Infobip provider fails application startup if its URL is not HTTPS or any required
credential, sender, callback secret, or template mapping is blank.
An existing AWS installation with none of these variables stays on the local capture
provider; setting `MOBILE_NOTIFICATION_PROVIDER=infobip` is the explicit cutover and
must happen only after all Infobip values have been populated.

## Configure signed callbacks

Create HMAC-SHA-256 subscription callbacks in Infobip using the same secret stored in
`INFOBIP_WEBHOOK_HMAC_SECRET`:

- delivery reports: `https://<cloudfront-host>/api/notification-provider/infobip/delivery`
- WhatsApp seen reports: `https://<cloudfront-host>/api/notification-provider/infobip/seen`
- signature header: `X-Hub-Signature`

CloudFront must forward POST bodies and `X-Hub-Signature` unchanged to the backend.
An unsigned request must return `401`; a valid signed report returns `200`. Do not add
these endpoints to a cached behavior.

## Enable a recipient

A destination is never read from an environment fallback. The signed-in user must
save an E.164 phone number through `PUT /api/notification-contacts/phone`, explicitly
consent to each desired channel, and enable that channel for the applicable category
through the notification-preferences API. Existing business events are suppressed
when any of those conditions is absent.

## Verify a release

Use a user-owned South African handset and normal application actions:

1. Trigger a capacity match, handover confirmation/finalization, escrow release, or
   delivery proposal.
2. Confirm one SMS and one WhatsApp message arrive when both channels are enabled.
3. Inspect `mobile_notification` and confirm provider identity plus progression to
   `DELIVERED`; open WhatsApp and confirm `READ` when Infobip supplies a seen report.
4. Replay the same signed callback. The observation count and current status must not
   change.
5. Check backend logs for full phone numbers, rendered message bodies, API keys, and
   callback secrets. None should be present.

## Triage and rollback

Start with notification state rather than resending manually:

- `SUPPRESSED`: contact, explicit consent, or category/channel preference is absent.
- `SUBMISSION_UNKNOWN`: the HTTP outcome was ambiguous; reconciliation is queued using
  the internal notification UUID as the Infobip custom message ID.
- `FAILED`, `REJECTED`, or `EXPIRED`: inspect the sanitized failure code and the Infobip
  portal. Correct sender, template, destination, or account policy before retrying.
- outbox dead letter: fix the cause, then use the established outbox recovery procedure;
  never create an ad-hoc provider send that bypasses idempotency.

For an emergency stop, set `MOBILE_NOTIFICATION_PROVIDER=local` and redeploy. New
messages remain inside the process-local capture adapter and do not leave the service.
This is an outage mode, not durable queuing; restore `infobip` after correcting the
provider configuration. Rotate a compromised API key or HMAC secret in Infobip and the
EC2 `.env` together, restart the backend, and verify an authenticated callback.
