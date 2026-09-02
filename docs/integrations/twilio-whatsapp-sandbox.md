# Twilio WhatsApp Sandbox setup

TradeMesh can send SMS and WhatsApp messages through Twilio Messaging. Local development uses an in-memory capture provider, so no Twilio credentials are needed for normal coding or CI.

## Configure a shared demo environment

Set these values in the environment secret store, never in Git:

```text
MOBILE_NOTIFICATION_PROVIDER=twilio
TWILIO_ACCOUNT_SID=<Twilio account SID>
TWILIO_AUTH_TOKEN=<Twilio auth token>
TWILIO_SMS_NUMBER=<Twilio SMS sender in E.164 format>
TWILIO_WHATSAPP_NUMBER=<Twilio WhatsApp Sandbox number in E.164 format>
```

The backend adds Twilio's required `whatsapp:` prefix. Store the number itself as `+<country code><number>`.

## Join each demo phone to the sandbox

Twilio only sends sandbox messages to phones that have joined the sandbox:

1. Open **Twilio Console → Messaging → Try it out → Send a WhatsApp message**.
2. Find the current sandbox number and join code.
3. From every phone that will receive the demo messages, send `join <code>` to that number in WhatsApp.
4. Confirm Twilio shows the phone as joined.

Do this before the demo. Sandbox membership expires and the join code can change, so send one test message from every device during the final environment check.

## Events that send WhatsApp updates

- the first backhaul match found for a shipment
- a clean or disputed delivery QR scan
- a completed escrow release

The recipient must have a verified phone identity linked to the relevant business. Repeated backhaul lookups reuse one durable key, so only one delivery job is queued per shipment. Failed jobs are retried by the outbox worker.

Production WhatsApp senders require Meta business approval. Keep shared testing on the Twilio Sandbox until that approval is complete.
