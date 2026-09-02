# Mobile messaging providers

TradeMesh treats SMS and WhatsApp as delivery channels, not as Twilio features.

Business workflows submit a provider-neutral message with:

- an idempotency key
- an E.164 recipient number
- a channel: SMS or WHATSAPP
- the message body

The encrypted outbox stores that request before a provider adapter sends it. Delivery, handover, payment, and tracking code do not import a provider SDK or know which vendor is selected.

## Selecting a provider

Set these independently:

    OTP_PROVIDER=<otp adapter key>
    MOBILE_NOTIFICATION_PROVIDER=<messaging adapter key>

Local development uses the local adapters. Sandbox and production do not choose a vendor by default; they fail to start until a deployed adapter is selected and configured.

Twilio remains available as the twilio adapter. Its credentials live only under the adapter-specific configuration branches:

    trademesh.access.otp.providers.twilio
    trademesh.notifications.mobile.providers.twilio

## Adding another provider

For ordinary SMS and WhatsApp delivery:

1. Implement MobileDeliveryProvider in the notification infrastructure package.
2. Activate it with MOBILE_NOTIFICATION_PROVIDER=<new key>.
3. Put its credentials under trademesh.notifications.mobile.providers.<new key>.
4. Add request-shape tests for both supported channels.

For sign-in codes, implement OtpProvider separately and activate it with OTP_PROVIDER=<new key>. A messaging vendor and an OTP vendor do not have to be the same company.

No business workflow, API contract, event, or database migration should change when a provider adapter is swapped.
