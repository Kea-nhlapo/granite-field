# Getting the Infobip variables for TradeMesh

This guide is for the person provisioning live SMS and WhatsApp notifications for
TradeMesh. It explains where every required value comes from and where to put it.
Do not send API keys or webhook secrets through email, chat, GitHub, or screenshots.
Enter them directly into the protected `.env` file on the AWS host.

## What must be ready

- An Infobip account with billing or an active trial.
- SMS enabled for South Africa.
- A registered WhatsApp Business sender.
- Access to the TradeMesh EC2 host and its active CloudFront distribution.
- A Meta Business Account and a phone number for WhatsApp registration, unless an
  Infobip-provided pre-verified number is purchased.

Infobip's current setup documentation is linked in [Official references](#official-references).

## 1. Get the account base URL

1. Sign in to the Infobip portal.
2. Open the API Resource hub or any API reference page while signed in.
3. Copy the personalized hostname displayed for the account. It normally looks like
   `xxxxx.api.infobip.com`.
4. Add `https://` and save the full value as:

```dotenv
INFOBIP_BASE_URL=https://xxxxx.api.infobip.com
```

The base URL identifies the account's Infobip API endpoint but is not itself a
password.

## 2. Create the API key

1. In Infobip, open **Developer Tools > API keys**. The API-key management page is
   also linked from Infobip's API authentication documentation.
2. Create a dedicated key for TradeMesh. Do not reuse an administrator's personal
   key.
3. Grant only the permissions needed to send Messages API traffic and read message
   reports for SMS and WhatsApp. WhatsApp sending requires the
   `whatsapp:message:send` scope when the portal exposes channel-level scopes.
4. Set an expiry and record a rotation reminder.
5. Copy the key when it is shown and save the raw key as:

```dotenv
INFOBIP_API_KEY=<secret API key only>
```

Do not add `App ` before the value. TradeMesh adds the `Authorization: App ...`
prefix itself. Treat this value as a secret.

## 3. Obtain the South African SMS sender

For a trial-only check, verify the receiving handset in Infobip and use the sender
made available by the guided SMS setup. Trial traffic may be restricted to verified
recipients.

For live traffic:

1. Go to **Channels and Numbers > Channels > SMS** and enable SMS if necessary.
2. Select **Request sender**, or go to **Channels and Numbers > My Requests > Sender
   requests > Request Sender**.
3. Select SMS, the required sender type, and **South Africa** as the destination
   country.
4. Describe TradeMesh traffic as transactional notifications and supply the requested
   company details, estimated monthly volume, and message examples.
5. Submit the request and wait until Infobip marks the sender active. Country and
   network rules determine whether Infobip supplies a number or approves an
   alphanumeric sender.
6. Copy the exact active sender value into:

```dotenv
INFOBIP_SMS_SENDER=<approved sender or number>
```

Do not invent a sender name. A value that is not approved for the account or route
can be rejected even when the API key is valid.

## 4. Register the WhatsApp sender

1. Go to **Channels and Numbers > Channels > WhatsApp**.
2. Select **Register sender**.
3. Choose either an Infobip pre-verified number or connect a number controlled by the
   business.
4. Complete the Meta embedded signup, WhatsApp Business Account selection, business
   profile, number verification, and Infobip access request.
5. Wait until the sender is registered and active.
6. Copy its full international number into:

```dotenv
INFOBIP_WHATSAPP_SENDER=<registered WhatsApp sender number>
```

An E.164 number with or without the leading `+` is acceptable to TradeMesh; the
provider adapter removes the `+` at the Infobip boundary.

## 5. Register the six WhatsApp templates

In **Channels and Numbers > Channels > WhatsApp > Senders**, select the registered
sender and choose **Register template**. Use language `en` and category **Utility**
for these transactional messages. Template names may contain lowercase letters and
underscores; the suggested names below are Infobip/Meta-compatible.

| Environment variable | Suggested approved name | Body text |
| --- | --- | --- |
| `INFOBIP_WHATSAPP_TEMPLATE_CAPACITY_MATCH_V1` | `trademesh_capacity_match_v1` | `Transport matches are ready. Sign in to TradeMesh to review them.` |
| `INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_ACCEPTED_V1` | `trademesh_handover_accepted_v1` | `The other party confirmed the handover. Sign in to TradeMesh to review its status.` |
| `INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_CLEAN_V1` | `trademesh_handover_clean_v1` | `The handover was completed successfully. Sign in to TradeMesh to review it.` |
| `INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_DISPUTED_V1` | `trademesh_handover_disputed_v1` | `The handover needs attention. Sign in to TradeMesh to review the recorded outcome.` |
| `INFOBIP_WHATSAPP_TEMPLATE_ESCROW_RELEASED_V1` | `trademesh_escrow_released_v1` | `An escrow payment was released. Sign in to TradeMesh to review the transaction.` |
| `INFOBIP_WHATSAPP_TEMPLATE_DELIVERY_CONFIRMATION_V1` | `trademesh_delivery_confirmation_v1` | `A delivery is waiting for your confirmation: {{1}}` |

Only the delivery-confirmation template has a body parameter. Add one sample URL for
`{{1}}` during template registration. Do not add parameters to the other five.

Submit every template to Meta and wait for status **Approved**. Then set each variable
to the exact approved template name—not an internal filename and not the display
text. If Meta changes a name during approval, use the final name shown in Infobip.

## 6. Create the webhook HMAC secret and subscriptions

Generate a new secret directly on the EC2 host:

```bash
openssl rand -hex 32
```

Store the output as `INFOBIP_WEBHOOK_HMAC_SECRET`. In Infobip:

1. Go to **Developer Tools > Subscriptions Management > Authentication settings**.
2. Create an HMAC authentication setting using that same secret.
3. Select `HMAC_SHA_256` and leave the signature header as
   `X-Hub-Signature`.
4. Create a notification profile that uses the authentication setting.
5. Create delivery-report subscriptions for SMS and WhatsApp pointing to:
   `https://<active-cloudfront-domain>/api/notification-provider/infobip/delivery`.
6. Create the WhatsApp seen-report subscription pointing to:
   `https://<active-cloudfront-domain>/api/notification-provider/infobip/seen`.

Use the active distribution domain from **AWS Console > CloudFront > Distributions >
Distribution domain name**. Confirm it resolves before registering it. CloudFront must
forward POST bodies and `X-Hub-Signature` unchanged and must not cache these paths.

## 7. Complete the AWS `.env`

On the EC2 host, edit:

```text
/opt/trademesh/granite-field/infra/containers/.env
```

Set file permissions to `0600` and fill this block:

```dotenv
MOBILE_NOTIFICATION_PROVIDER=infobip
MOBILE_NOTIFICATION_MAX_DELIVERY_ATTEMPTS=3
INFOBIP_BASE_URL=https://xxxxx.api.infobip.com
INFOBIP_API_KEY=<secret>
INFOBIP_SMS_SENDER=<approved SMS sender>
INFOBIP_WHATSAPP_SENDER=<registered WhatsApp sender>
INFOBIP_WEBHOOK_HMAC_SECRET=<generated secret>
INFOBIP_CONNECT_TIMEOUT=PT5S
INFOBIP_READ_TIMEOUT=PT15S
INFOBIP_WHATSAPP_TEMPLATE_CAPACITY_MATCH_V1=trademesh_capacity_match_v1
INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_ACCEPTED_V1=trademesh_handover_accepted_v1
INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_CLEAN_V1=trademesh_handover_clean_v1
INFOBIP_WHATSAPP_TEMPLATE_HANDOVER_DISPUTED_V1=trademesh_handover_disputed_v1
INFOBIP_WHATSAPP_TEMPLATE_ESCROW_RELEASED_V1=trademesh_escrow_released_v1
INFOBIP_WHATSAPP_TEMPLATE_DELIVERY_CONFIRMATION_V1=trademesh_delivery_confirmation_v1
DELIVERY_CONFIRMATION_BASE_URL=https://<active-cloudfront-domain>/delivery/confirm
```

Replace every placeholder. Never commit this populated file. Validate without
printing resolved secrets:

```bash
cd /opt/trademesh/granite-field/infra/containers
chmod 600 .env
docker compose -f docker-compose.aws.yml --env-file .env config --quiet
```

TradeMesh deliberately refuses to start with `MOBILE_NOTIFICATION_PROVIDER=infobip`
when any required Infobip value or template mapping is blank.

## 8. Test in the right order

1. In the Infobip portal, send one SMS to a verified, opted-in test handset. Confirm
   the portal records it as delivered.
2. Send one approved WhatsApp template to the same opted-in handset. Confirm delivery.
3. Deploy the TradeMesh notification commits.
4. In TradeMesh, save the user's phone number, capture explicit SMS and WhatsApp
   consent, and enable both notification preferences.
5. Trigger a supported business event and confirm the database state progresses to
   `DELIVERED`; opening WhatsApp should allow it to progress to `READ` when Infobip
   sends a seen report.
6. Check that neither logs nor Git contain the phone number, rendered message, API
   key, or HMAC secret.

If either portal-level test fails, fix sender/template/account provisioning before
debugging TradeMesh. A successful API response means the provider accepted or queued
the message; use its delivery report to confirm handset delivery.

## Values that are secrets

| Value | Secret? | Handling |
| --- | --- | --- |
| `INFOBIP_API_KEY` | Yes | EC2 `.env` only; rotate if exposed. |
| `INFOBIP_WEBHOOK_HMAC_SECRET` | Yes | EC2 `.env` and Infobip authentication setting only. |
| `INFOBIP_BASE_URL` | No | Still keep environment-specific configuration out of source. |
| Sender values | Not credentials | Avoid publishing them unnecessarily. |
| Template names | No | They must exactly match approved Infobip names. |

## Official references

- [Infobip personalized base URL](https://www.infobip.com/docs/essentials/api-essentials/base-url)
- [Infobip API authentication and API keys](https://www.infobip.com/docs/essentials/api-essentials/api-authentication)
- [Request SMS senders](https://www.infobip.com/docs/myrequests/submit-myrequests)
- [Register a WhatsApp sender](https://www.infobip.com/docs/whatsapp/get-started/embedded-signup)
- [Create and send WhatsApp templates](https://www.infobip.com/docs/tutorials/send-whatsapp-template-messages)
- [Webhook subscription HMAC settings](https://www.infobip.com/docs/subscriptions/subscription-components)
- [Messages API send operation](https://www.infobip.com/docs/messages-api/send-a-message)
